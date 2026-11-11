package indi.etern.musichud.client.ui.shadow;

import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.RRect;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import icyllis.modernui.view.View;
import org.jetbrains.annotations.NotNull;

/**
 * Draws a rounded-rect Gaussian shadow for another view. Set it as the background of a
 * large ancestor (e.g. the page) so the blur can extend beyond the target's own bounds
 * without changing layout.
 */
@SuppressWarnings("UnstableApiUsage")
public final class BlurShadowDrawable extends Drawable {
    private final BlurShadowSpec spec;
    private final View target;
    private final RRect rrect = new RRect();
    private Paint shadowPaint;

    public BlurShadowDrawable(@NotNull BlurShadowSpec spec, @NotNull View target) {
        this.spec = spec;
        this.target = target;
    }

    @Override
    public void draw(@NotNull Canvas canvas) {
        if (!(canvas instanceof ArcCanvas arc)
                || !(getCallback() instanceof View host)
                || !(spec.shape() instanceof BlurShadowSpec.Shape.RoundedRect rounded)) {
            return;
        }
        int width = target.getWidth();
        int height = target.getHeight();
        if (width <= 0 || height <= 0 || target.getScaleX() <= 0f || target.getScaleY() <= 0f) {
            return;
        }
        float x = relativeOffset(target, host, true);
        float y = relativeOffset(target, host, false);
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            return;
        }

        float blurRadius = target.dp(spec.radiusDp());
        float offsetX = target.dp(spec.offsetXDp());
        float offsetY = target.dp(spec.offsetYDp());
        float cornerRadius = Math.min(target.dp(rounded.cornerRadiusDp()),
                Math.min(width, height) * 0.5f);

        canvas.save();
        canvas.translate(x, y);
        canvas.scale(target.getScaleX(), target.getScaleY(),
                target.getPivotX(), target.getPivotY());
        BlurShadowRenderer.drawRoundedRect(arc, rrect, shadowPaint(),
                offsetX, offsetY, width + offsetX, height + offsetY,
                cornerRadius, blurRadius, spec);
        canvas.restore();
    }

    private static float relativeOffset(View view, View ancestor, boolean horizontal) {
        float offset = 0f;
        View current = view;
        while (current != null && current != ancestor) {
            offset += horizontal
                    ? current.getLeft() + current.getTranslationX()
                    : current.getTop() + current.getTranslationY();
            var parent = current.getParent();
            current = parent instanceof View parentView ? parentView : null;
        }
        return current == ancestor ? offset : Float.NaN;
    }

    private Paint shadowPaint() {
        if (shadowPaint == null) {
            shadowPaint = new Paint();
        }
        return shadowPaint;
    }

    public void close() {
        if (shadowPaint != null) {
            shadowPaint.close();
            shadowPaint = null;
        }
    }
}
