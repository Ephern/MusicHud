package indi.etern.musichud.client.ui.drawable;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.LinearGradient;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.graphics.Shader;
import icyllis.modernui.graphics.drawable.Drawable;

/**
 * Level-driven zero-to-full bar: a pill track plus a pill fill anchored at the left edge.
 * The fill gradient is pinned to the head (not to the track), so the colour band keeps its
 * length as progress grows; the level is the only per-frame state.
 */
public final class LinearProgressDrawable extends Drawable {

    private final int heightPx;
    private final float trackThicknessPx;
    private final float cornerRadiusPx;
    private final float alphaFadeIn;

    private final Paint trackPaint = new Paint();
    private final Paint fillPaint = new Paint();

    /**
     * @param trackThicknessPx fixed track thickness, or {@code <= 0} to fill the drawable height
     * @param heightPx         intrinsic height reported to the owning widget
     */
    public LinearProgressDrawable(int trackColor, @NonNull int[] fillColors, int heightPx,
                                  float trackThicknessPx, float cornerRadiusPx,
                                  float gradientLengthPx, float alphaFadeIn) {
        int fillColor = fillColors[fillColors.length - 1];
        this.heightPx = heightPx;
        this.trackThicknessPx = trackThicknessPx;
        this.cornerRadiusPx = cornerRadiusPx;
        this.alphaFadeIn = alphaFadeIn;

        trackPaint.setAntiAlias(true);
        trackPaint.setColor(trackColor);

        fillPaint.setAntiAlias(true);
        if (fillColors.length > 1 && gradientLengthPx > 0) {
            LinearGradient fillShader = new LinearGradient(-gradientLengthPx, 0, 0, 0,
                    fillColors, null, Shader.TileMode.CLAMP, null);
            fillPaint.setShader(fillShader);
        } else {
            fillPaint.setColor(fillColor);
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();
        final int width = bounds.width();
        final int height = bounds.height();
        if (width <= 0 || height <= 0) {
            return;
        }

        final float thickness = trackThicknessPx > 0 ? Math.min(trackThicknessPx, height) : height;
        final float top = bounds.top + (height - thickness) * 0.5f;
        final float bottom = top + thickness;
        final float radius = cornerRadiusPx < 0
                ? thickness * 0.5f
                : Math.min(cornerRadiusPx, thickness * 0.5f);
        final float left = bounds.left;

        canvas.drawRoundRect(left, top, bounds.right, bottom, radius, trackPaint);

        final float fraction = getLevel() / (float) MAX_LEVEL;
        if (fraction <= 0) {
            return;
        }
        final float fillWidth = width * fraction;
        final float fillRadius = Math.min(radius, fillWidth * 0.5f);
        if (alphaFadeIn > 0) {
            fillPaint.setAlphaF(Math.min(1f, fraction / alphaFadeIn));
        }

        final int saveCount = canvas.save();
        canvas.translate(left + fillWidth, 0);
        canvas.drawRoundRect(-fillWidth, top, 0, bottom, fillRadius, fillPaint);
        canvas.restoreToCount(saveCount);
    }

    @Override
    protected boolean onLevelChange(int level) {
        invalidateSelf();
        return true;
    }

    @Override
    public int getIntrinsicWidth() {
        return -1;
    }

    @Override
    public int getIntrinsicHeight() {
        return heightPx;
    }

    @Override
    public int getMinimumHeight() {
        return heightPx;
    }
}
