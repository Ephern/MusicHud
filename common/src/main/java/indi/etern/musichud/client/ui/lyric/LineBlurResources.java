package indi.etern.musichud.client.ui.lyric;

import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.core.ColorSpace;
import icyllis.arc3d.core.ImageInfo;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.granite.GraniteSurface;
import icyllis.arc3d.granite.RecordingContext;
import icyllis.arc3d.sketch.Image;
import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.Surface;
import icyllis.modernui.core.Core;
import org.jetbrains.annotations.Nullable;

/**
 * Per-lyric-line offscreen render targets and cached convolution results used by
 * {@link LineBlurRenderer}. All fields are mutated on the UI/render thread only.
 * <p>
 * A {@link GraniteSurface} image snapshot is a real copy (never a shared handle) and is
 * cached by the surface, so {@link Surface#notifyWillChange()} must be called before the
 * same target is drawn into again.
 */
public final class LineBlurResources {
    Surface sourceSurface;
    Surface scratchSurface;
    Surface finalSurface;

    /** Cached snapshot of the rendered line content. */
    Image sourceImage;
    /** Cached snapshot of the fully blurred content. */
    Image finalImage;

    final Paint tapPaint = new Paint();

    private boolean closed;

    int surfaceWidth;
    int surfaceHeight;
    float downscale = -1f;

    /** Signature of the rendered content; {@link Long#MIN_VALUE} forces a re-render. */
    long cachedContentStamp = Long.MIN_VALUE;
    /** Radius the cached {@link #finalImage} was blurred with; NaN forces a re-blur. */
    float cachedRadius = Float.NaN;

    /**
     * Ensures the three render targets match the requested size/downscale.
     *
     * @return true if the targets were (re)created, i.e. any cached content is stale
     */
    boolean ensureSurfaces(int width, int height, float downscale) {
        if (sourceSurface != null
                && this.surfaceWidth == width
                && this.surfaceHeight == height
                && this.downscale == downscale) {
            return false;
        }
        releaseSurfaces();
        RecordingContext rc = Core.requireUiRecordingContext();
        ImageInfo info = ImageInfo.make(width, height,
                ColorInfo.CT_RGBA_8888, ColorInfo.AT_PREMUL, ColorSpace.get(ColorSpace.Named.SRGB));
        sourceSurface = GraniteSurface.makeRenderTarget(rc, info, false,
                Engine.SurfaceOrigin.kUpperLeft, "musichud-line-blur-src");
        scratchSurface = GraniteSurface.makeRenderTarget(rc, info, false,
                Engine.SurfaceOrigin.kUpperLeft, "musichud-line-blur-scratch");
        finalSurface = GraniteSurface.makeRenderTarget(rc, info, false,
                Engine.SurfaceOrigin.kUpperLeft, "musichud-line-blur-final");
        this.surfaceWidth = width;
        this.surfaceHeight = height;
        this.downscale = downscale;
        cachedContentStamp = Long.MIN_VALUE;
        cachedRadius = Float.NaN;
        return isReady();
    }

    boolean isReady() {
        return sourceSurface != null && scratchSurface != null && finalSurface != null;
    }

    void replaceSourceImage(@Nullable Image image) {
        if (sourceImage == image) {
            return;
        }
        if (sourceImage != null) {
            sourceImage.unref();
        }
        sourceImage = image;
    }

    void replaceFinalImage(@Nullable Image image) {
        if (finalImage == image) {
            return;
        }
        if (finalImage != null) {
            finalImage.unref();
        }
        finalImage = image;
    }

    /** Releases GPU render targets but keeps the reusable native paint. */
    public void release() {
        releaseSurfaces();
    }

    /** Releases everything including the native paint. */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        release();
        tapPaint.close();
    }

    private void releaseSurfaces() {
        if (sourceSurface != null) {
            sourceSurface.unref();
            sourceSurface = null;
        }
        if (scratchSurface != null) {
            scratchSurface.unref();
            scratchSurface = null;
        }
        if (finalSurface != null) {
            finalSurface.unref();
            finalSurface = null;
        }
        replaceSourceImage(null);
        replaceFinalImage(null);
        surfaceWidth = 0;
        surfaceHeight = 0;
        downscale = -1f;
        cachedContentStamp = Long.MIN_VALUE;
        cachedRadius = Float.NaN;
    }
}
