package indi.etern.musichud.client.ui.shadow;

import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.RRect;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import icyllis.modernui.widget.FrameLayout;
import org.jetbrains.annotations.NotNull;

/**
 * A transparent wrapper that draws a Gaussian shadow behind its content. The current
 * fast path handles rounded-rect silhouettes directly with Canvas.drawBlurredRRect;
 * content-silhouette shadows can share the same layout API when their offscreen path
 * is added.
 */
@SuppressWarnings("UnstableApiUsage")
public final class BlurShadowLayout extends FrameLayout {
    private final BlurShadowSpec spec;
    private final RRect rrect = new RRect();
    private Paint shadowPaint;

    public BlurShadowLayout(@NotNull Context context, @NotNull BlurShadowSpec spec) {
        super(context);
        this.spec = spec;
        setClipChildren(false);
    }

    @Override
    protected void dispatchDraw(@NotNull Canvas canvas) {
        if (canvas instanceof ArcCanvas arc
                && spec.shape() instanceof BlurShadowSpec.Shape.RoundedRect rounded) {
            drawRoundedRectShadow(arc, rounded);
        }
        super.dispatchDraw(canvas);
    }

    private void drawRoundedRectShadow(@NotNull ArcCanvas canvas,
                                       @NotNull BlurShadowSpec.Shape.RoundedRect shape) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || spec.radiusDp() <= 0f || spec.alpha() <= 0f) {
            return;
        }
        float blurRadius = dp(spec.radiusDp());
        float offsetX = dp(spec.offsetXDp());
        float offsetY = dp(spec.offsetYDp());
        float cornerRadius = Math.min(dp(shape.cornerRadiusDp()),
                Math.min(width, height) * 0.5f);
        Paint paint = shadowPaint();
        BlurShadowRenderer.drawRoundedRect(canvas, rrect, paint,
                offsetX, offsetY, width + offsetX, height + offsetY,
                cornerRadius, blurRadius, spec);
    }

    private Paint shadowPaint() {
        if (shadowPaint == null) {
            shadowPaint = new Paint();
        }
        return shadowPaint;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (shadowPaint != null) {
            shadowPaint.close();
            shadowPaint = null;
        }
        super.onDetachedFromWindow();
    }
}
