package indi.etern.musichud.client.ui.drawable;

import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.core.ColorSpaces;
import icyllis.arc3d.core.ImageInfo;
import icyllis.arc3d.core.Matrix4;
import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.core.Rect2i;
import icyllis.arc3d.core.RefCnt;
import icyllis.arc3d.core.SamplingOptions;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.granite.GraniteSurface;
import icyllis.arc3d.sketch.BlendMode;
import icyllis.arc3d.sketch.Image;
import icyllis.arc3d.sketch.Matrix;
import icyllis.arc3d.sketch.NWayCanvas;
import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.RRect;
import icyllis.arc3d.sketch.Surface;
import icyllis.arc3d.sketch.shaders.ImageShader;
import icyllis.arc3d.sketch.shaders.Shader;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import indi.etern.musichud.client.ui.lyric.LineBlurRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * Draws a blurred copy of whatever is already in the ModernUI layer behind this drawable's bounds.
 * It is meant to sit under a translucent panel; the panel itself supplies the tint.
 * <p>
 * The down/up sampling mirrors {@code SceneBlur}: a multi-level 3x3 tent chain with the separable
 * Gaussian at the level where sigma is small enough, using RGBA16F targets when available.
 */
public final class BackdropBlurDrawable extends Drawable {
    private static final int MAX_LEVELS = 4;
    private static final Matrix4 IDENTITY = new Matrix4();
    private static final Field NWAY_LIST = nwayList();

    private final int cornerRadius;
    private final int blurRadius;
    private final Paint tapPaint = new Paint();
    private final Paint maskPaint = new Paint();
    private final RRect maskRect = new RRect();

    private Surface sourceSurface;
    private Surface[] downsamples = new Surface[1];
    private Surface scratchSurface;
    private Surface resultSurface;
    private int contentWidth;
    private int contentHeight;
    private int level;
    private boolean closed;

    public BackdropBlurDrawable(int cornerRadius, int blurRadius) {
        this.cornerRadius = Math.max(0, cornerRadius);
        this.blurRadius = Math.max(0, blurRadius);
    }

    @Override
    public void draw(@NotNull Canvas canvas) {
        if (closed || blurRadius <= 0 || !(canvas instanceof ArcCanvas arc)) {
            return;
        }
        var nativeCanvas = arc.getCanvas();
        Surface layer = surfaceOf(nativeCanvas);
        if (layer == null) {
            return;
        }
        var bounds = getBounds();
        int localWidth = bounds.width();
        int localHeight = bounds.height();
        if (localWidth <= 0 || localHeight <= 0) {
            return;
        }

        Rect2i device = new Rect2i();
        arc.getMatrix().mapRect(bounds.left, bounds.top, bounds.right, bounds.bottom, device);
        if (device.width() <= 0 || device.height() <= 0) {
            return;
        }

        float scale = device.width() / (float) localWidth;
        float blurDevice = blurRadius * scale;
        float cornerDevice = cornerRadius * scale;
        float sigmaFull = blurDevice / 3f;
        int padding = (int) Math.ceil(blurDevice) + 2;

        int snapLeft = Math.max(0, device.left() - padding);
        int snapTop = Math.max(0, device.top() - padding);
        int snapRight = Math.min(layer.getWidth(), device.right() + padding);
        int snapBottom = Math.min(layer.getHeight(), device.bottom() + padding);
        int snapshotWidth = snapRight - snapLeft;
        int snapshotHeight = snapBottom - snapTop;
        if (snapshotWidth < 1 || snapshotHeight < 1) {
            return;
        }

        int contentWidth = alignUp(snapshotWidth);
        int contentHeight = alignUp(snapshotHeight);

        int targetLevel = 0;
        int levelWidth = contentWidth;
        int levelHeight = contentHeight;
        float sigmaLow = sigmaFull;
        while (targetLevel < MAX_LEVELS && sigmaLow > 4f
                && levelWidth / 2 >= 16 && levelHeight / 2 >= 16) {
            targetLevel++;
            levelWidth /= 2;
            levelHeight /= 2;
            sigmaLow *= 0.5f;
        }

        if (!ensureSurfaces(contentWidth, contentHeight, targetLevel)) {
            return;
        }

        Image snapshot = layer.makeImageSnapshot(
                new Rect2i(snapLeft, snapTop, snapRight, snapBottom));
        if (snapshot == null) {
            return;
        }
        Image result = null;
        try {
            sourceSurface.notifyWillChange();
            var sourceCanvas = sourceSurface.getCanvas();
            sourceCanvas.clear(0);
            sourceCanvas.drawImageRect(snapshot,
                    new Rect2f(0, 0, snapshotWidth, snapshotHeight),
                    new Rect2f(0, 0, snapshotWidth, snapshotHeight),
                    SamplingOptions.LINEAR, null,
                    icyllis.arc3d.sketch.Canvas.SRC_RECT_CONSTRAINT_FAST);

            Image source = sourceSurface.makeImageSnapshot();
            if (source == null) {
                return;
            }
            Image current = null;
            try {
                if (targetLevel == 0) {
                    if (!LineBlurRenderer.convolveInto(source, contentWidth, contentHeight,
                            sigmaLow, scratchSurface, resultSurface, tapPaint)) {
                        return;
                    }
                } else {
                    current = source;
                    source = null;
                    for (int i = 1; i <= targetLevel; i++) {
                        int dstWidth = Math.max(1, contentWidth >> i);
                        int dstHeight = Math.max(1, contentHeight >> i);
                        drawTent(current, downsamples[i],
                                current.getWidth(), current.getHeight(), dstWidth, dstHeight);
                        current.unref();
                        current = downsamples[i].makeImageSnapshot();
                        if (current == null) {
                            return;
                        }
                    }

                    int lastWidth = Math.max(1, contentWidth >> targetLevel);
                    int lastHeight = Math.max(1, contentHeight >> targetLevel);
                    if (!LineBlurRenderer.convolveInto(current, lastWidth, lastHeight,
                            sigmaLow, scratchSurface, downsamples[targetLevel], tapPaint)) {
                        return;
                    }
                    current.unref();
                    current = downsamples[targetLevel].makeImageSnapshot();
                    if (current == null) {
                        return;
                    }

                    for (int i = targetLevel - 1; i >= 1; i--) {
                        int dstWidth = Math.max(1, contentWidth >> i);
                        int dstHeight = Math.max(1, contentHeight >> i);
                        drawTent(current, downsamples[i],
                                current.getWidth(), current.getHeight(), dstWidth, dstHeight);
                        current.unref();
                        current = downsamples[i].makeImageSnapshot();
                        if (current == null) {
                            return;
                        }
                    }
                    drawTent(current, resultSurface,
                            current.getWidth(), current.getHeight(), contentWidth, contentHeight);
                    current.unref();
                    current = null;
                }

                result = resultSurface.makeImageSnapshot();
            } finally {
                if (source != null) source.unref();
                if (current != null) current.unref();
            }
            if (result == null) {
                return;
            }
            drawResult(nativeCanvas, result, device, snapLeft, snapTop, cornerDevice);
        } finally {
            snapshot.unref();
            if (result != null) {
                result.unref();
            }
        }
    }

    private void drawTent(Image source, Surface target, int srcWidth, int srcHeight,
                          int dstWidth, int dstHeight) {
        target.notifyWillChange();
        var canvas = target.getCanvas();
        canvas.clear(0);
        float stepX = dstWidth / (float) Math.max(1, srcWidth);
        float stepY = dstHeight / (float) Math.max(1, srcHeight);
        Rect2f src = new Rect2f(0, 0, srcWidth, srcHeight);
        tapPaint.setBlendMode(BlendMode.PLUS);
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                float weight = (i == 0 ? 2f : 1f) * (j == 0 ? 2f : 1f) / 16f;
                tapPaint.setAlpha(weight);
                Rect2f dst = new Rect2f(i * stepX, j * stepY,
                        dstWidth + i * stepX, dstHeight + j * stepY);
                canvas.drawImageRect(source, src, dst, SamplingOptions.LINEAR, tapPaint,
                        icyllis.arc3d.sketch.Canvas.SRC_RECT_CONSTRAINT_FAST);
            }
        }
        tapPaint.setBlendMode(null);
    }

    private void drawResult(icyllis.arc3d.sketch.Canvas nativeCanvas, Image result,
                            Rect2i device, int snapLeft, int snapTop, float radius) {
        Matrix shaderMatrix = new Matrix();
        shaderMatrix.setScaleTranslate(1f, 1f, snapLeft, snapTop);
        Shader shader = ImageShader.make(RefCnt.create(result),
                Shader.TILE_MODE_CLAMP, Shader.TILE_MODE_CLAMP,
                SamplingOptions.LINEAR, shaderMatrix);
        if (shader == null) {
            return;
        }
        try {
            maskPaint.setColor(0xFFFFFFFF);
            maskPaint.setShader(shader);
            maskPaint.setBlendMode(null);
            nativeCanvas.save();
            nativeCanvas.setMatrix(IDENTITY);
            maskRect.setRectXY(device.left(), device.top(), device.right(), device.bottom(),
                    radius, radius);
            nativeCanvas.drawRRect(maskRect, maskPaint);
            nativeCanvas.restore();
            maskPaint.setShader(null);
        } finally {
            shader.unref();
        }
    }

    private boolean ensureSurfaces(int width, int height, int level) {
        if (sourceSurface != null && contentWidth == width && contentHeight == height
                && this.level == level) {
            return true;
        }
        releaseSurfaces();
        contentWidth = width;
        contentHeight = height;
        this.level = level;
        try {
            sourceSurface = makeTarget("src", width, height);
            resultSurface = makeTarget("result", width, height);
            downsamples = new Surface[level + 1];
            for (int i = 1; i <= level; i++) {
                downsamples[i] = makeTarget("down" + i,
                        Math.max(1, width >> i), Math.max(1, height >> i));
            }
            scratchSurface = makeTarget("scratch",
                    Math.max(1, width >> level), Math.max(1, height >> level));
            return sourceSurface != null && resultSurface != null && scratchSurface != null;
        } catch (RuntimeException failure) {
            releaseSurfaces();
            return false;
        }
    }

    private static Surface makeTarget(String label, int width, int height) {
        var context = Core.requireUiRecordingContext();
        try {
            return GraniteSurface.makeRenderTarget(context,
                    ImageInfo.make(width, height,
                            ColorInfo.CT_RGBA_F16, ColorInfo.AT_PREMUL, ColorSpaces.SRGB),
                    false, Engine.SurfaceOrigin.kUpperLeft,
                    "musichud-backdrop-" + label);
        } catch (RuntimeException unsupportedHalfFloat) {
            return GraniteSurface.makeRenderTarget(context,
                    ImageInfo.make(width, height,
                            ColorInfo.CT_RGBA_8888, ColorInfo.AT_PREMUL, ColorSpaces.SRGB),
                    false, Engine.SurfaceOrigin.kUpperLeft,
                    "musichud-backdrop-" + label);
        }
    }

    private void releaseSurfaces() {
        if (sourceSurface != null) {
            sourceSurface.unref();
            sourceSurface = null;
        }
        for (int i = 1; i < downsamples.length; i++) {
            if (downsamples[i] != null) {
                downsamples[i].unref();
            }
        }
        downsamples = new Surface[1];
        if (scratchSurface != null) {
            scratchSurface.unref();
            scratchSurface = null;
        }
        if (resultSurface != null) {
            resultSurface.unref();
            resultSurface = null;
        }
        contentWidth = 0;
        contentHeight = 0;
        level = 0;
    }

    private static @Nullable Field nwayList() {
        try {
            Field field = NWayCanvas.class.getDeclaredField("mList");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    private static @Nullable Surface surfaceOf(icyllis.arc3d.sketch.Canvas canvas) {
        icyllis.arc3d.sketch.Canvas current = canvas;
        for (int i = 0; i < 4 && current != null; i++) {
            Surface surface = current.getSurface();
            if (surface != null) {
                return surface;
            }
            if (!(current instanceof NWayCanvas) || NWAY_LIST == null) {
                return null;
            }
            try {
                Object list = NWAY_LIST.get(current);
                if (!(list instanceof icyllis.arc3d.sketch.Canvas[] canvases) || canvases.length == 0) {
                    return null;
                }
                current = canvases[0];
            } catch (IllegalAccessException failure) {
                return null;
            }
        }
        return null;
    }

    private static int alignUp(int value) {
        return (value + 31) & ~31;
    }

    public void release() {
        if (closed) {
            return;
        }
        closed = true;
        releaseSurfaces();
        tapPaint.close();
        maskPaint.close();
    }
}
