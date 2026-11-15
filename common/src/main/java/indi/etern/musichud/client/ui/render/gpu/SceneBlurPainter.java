package indi.etern.musichud.client.ui.render.gpu;

import icyllis.arc3d.core.Matrix4;
import icyllis.arc3d.core.RefCnt;
import icyllis.arc3d.core.SamplingOptions;
import icyllis.arc3d.sketch.Image;
import icyllis.arc3d.sketch.Matrix;
import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.RRect;
import icyllis.arc3d.sketch.shaders.ImageShader;
import icyllis.arc3d.sketch.shaders.Shader;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import org.jetbrains.annotations.NotNull;

/**
 * Draws an already blurred texture back, clipped to a rounded rect. The image origin is mapped to
 * {@code (imageLeft, imageTop)} in device space, so the caller can crop with padding.
 */
@SuppressWarnings("UnstableApiUsage")
public final class SceneBlurPainter {
    private static final Matrix4 IDENTITY = new Matrix4();

    private final Paint paint = new Paint();
    private final RRect rect = new RRect();

    public void draw(@NotNull ArcCanvas canvas, @NotNull Image image,
                     int imageLeft, int imageTop,
                     float left, float top, float right, float bottom,
                     float radius, float alpha) {
        if (alpha <= 0.001f || right <= left || bottom <= top) {
            return;
        }
        Matrix shaderMatrix = new Matrix();
        shaderMatrix.setScaleTranslate(1f, 1f, imageLeft, imageTop);
        Shader shader = ImageShader.make(RefCnt.create(image),
                Shader.TILE_MODE_CLAMP, Shader.TILE_MODE_CLAMP,
                SamplingOptions.LINEAR, shaderMatrix);
        if (shader == null) {
            return;
        }
        try {
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha(alpha);
            paint.setShader(shader);
            paint.setBlendMode(null);
            var nativeCanvas = canvas.getCanvas();
            nativeCanvas.save();
            nativeCanvas.setMatrix(IDENTITY);
            rect.setRectXY(left, top, right, bottom, radius, radius);
            nativeCanvas.drawRRect(rect, paint);
            nativeCanvas.restore();
            paint.setShader(null);
        } finally {
            shader.unref();
        }
    }

    public void close() {
        paint.close();
    }
}
