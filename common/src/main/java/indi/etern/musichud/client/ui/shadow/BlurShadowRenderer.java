package indi.etern.musichud.client.ui.shadow;

import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.RRect;
import icyllis.modernui.graphics.pipeline.ArcCanvas;

/** Shared rounded-rect shadow drawing so the wrapper and the drawable stay identical. */
final class BlurShadowRenderer {
    private BlurShadowRenderer() {
    }

    static void drawRoundedRect(ArcCanvas canvas,
                                RRect rrect, Paint paint,
                                float left, float top, float right, float bottom,
                                float cornerRadius, float blurRadius,
                                BlurShadowSpec spec) {
        if (right <= left || bottom <= top || blurRadius <= 0f || spec.alpha() <= 0f) {
            return;
        }
        rrect.setRectXY(left, top, right, bottom, cornerRadius, cornerRadius);
        int color = spec.color();
        float alpha = ((color >>> 24) & 0xFF) / 255f * spec.alpha();
        paint.setColor4f(
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
                alpha);
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStyle(Paint.FILL);
        canvas.getCanvas().drawBlurredRRect(rrect, paint, blurRadius, 0.1f);
    }
}
