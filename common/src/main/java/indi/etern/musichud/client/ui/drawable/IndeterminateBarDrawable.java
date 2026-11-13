package indi.etern.musichud.client.ui.drawable;

import icyllis.modernui.animation.Animator;
import icyllis.modernui.animation.BezierInterpolator;
import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.graphics.drawable.Animatable;
import icyllis.modernui.graphics.drawable.Drawable;

/**
 * Indeterminate companion to {@link LinearProgressDrawable}, ported from ModernUI's
 * {@code LinearIndeterminateDrawable}: two segments chase each other on an 1800 ms loop with
 * four independent delay/duration/easing tracks. Segments are drawn as pills instead of round
 * capped strokes so the thickness can follow the drawable height.
 */
public final class IndeterminateBarDrawable extends Drawable implements Animatable {

    private static final int TOTAL_DURATION_MS = 1800;
    private static final int[] SEGMENT_DURATION_MS = {533, 567, 850, 750};
    private static final int[] SEGMENT_DELAY_MS = {1267, 1000, 333, 0};
    private static final TimeInterpolator[] SEGMENT_INTERPOLATORS = {
            new BezierInterpolator(0.2f, 0f, 0.8f, 1f),
            new BezierInterpolator(0.4f, 0f, 1f, 1f),
            new BezierInterpolator(0f, 0f, 0.65f, 1f),
            new BezierInterpolator(0.1f, 0f, 0.45f, 1f),
    };

    private final int trackColor;
    private final int bandColor;
    private final int heightPx;
    private final float trackThicknessPx;
    private final float cornerRadiusPx;

    private final Paint trackPaint = new Paint();
    private final Paint bandPaint = new Paint();

    private Animator animator;
    private float alpha = 1f;

    private float startFraction1;
    private float endFraction1;
    private float startFraction2;
    private float endFraction2;

    /**
     * @param trackThicknessPx fixed track thickness, or {@code <= 0} to fill the drawable height
     * @param heightPx         intrinsic height reported to the owning widget
     */
    public IndeterminateBarDrawable(int trackColor, int bandColor, int heightPx,
                                    float trackThicknessPx, float cornerRadiusPx) {
        this.trackColor = trackColor;
        this.bandColor = bandColor;
        this.heightPx = heightPx;
        this.trackThicknessPx = trackThicknessPx;
        this.cornerRadiusPx = cornerRadiusPx;
        trackPaint.setAntiAlias(true);
        bandPaint.setAntiAlias(true);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();
        final int width = bounds.width();
        final int height = bounds.height();
        if (width <= 0 || height <= 0 || alpha == 0) {
            return;
        }

        final float thickness = trackThicknessPx > 0 ? Math.min(trackThicknessPx, height) : height;
        final float top = bounds.top + (height - thickness) * 0.5f;
        final float bottom = top + thickness;
        final float radius = cornerRadiusPx < 0
                ? thickness * 0.5f
                : Math.min(cornerRadiusPx, thickness * 0.5f);

        trackPaint.setColor(trackColor);
        trackPaint.setAlphaF(alphaOf(trackColor) * alpha);
        canvas.drawRoundRect(bounds.left, top, bounds.right, bottom, radius, trackPaint);

        final float inner = width - thickness;
        if (inner <= 0) {
            return;
        }

        bandPaint.setColor(bandColor);
        bandPaint.setAlphaF(alphaOf(bandColor) * alpha);
        drawSegment(canvas, bounds.left, thickness, inner, top, bottom, radius,
                startFraction1, endFraction1);
        drawSegment(canvas, bounds.left, thickness, inner, top, bottom, radius,
                startFraction2, endFraction2);
    }

    private void drawSegment(Canvas canvas, float left, float thickness, float inner,
                             float top, float bottom, float radius, float start, float end) {
        if (start == end) {
            return;
        }
        final float segmentLeft = left + start * inner;
        final float segmentRight = left + thickness + end * inner;
        if (segmentRight > segmentLeft) {
            canvas.drawRoundRect(segmentLeft, top, segmentRight, bottom, radius, bandPaint);
        }
    }

    @Override
    public void start() {
        if (animator == null) {
            animator = createAnimator();
        }
        animator.start();
    }

    @Override
    public void stop() {
        if (animator != null) {
            animator.end();
        }
    }

    @Override
    public boolean isRunning() {
        return animator != null && animator.isRunning();
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        if (animator != null && animator.isStarted()) {
            if (visible) {
                animator.resume();
            } else {
                animator.pause();
            }
        }
        return super.setVisible(visible, restart);
    }

    @Override
    public void setAlpha(float alpha) {
        if (this.alpha != alpha) {
            this.alpha = alpha;
            invalidateSelf();
        }
    }

    @Override
    public float getAlpha() {
        return alpha;
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

    @NonNull
    private Animator createAnimator() {
        ValueAnimator animator = new ValueAnimator();
        animator.setDuration(TOTAL_DURATION_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(TimeInterpolator.LINEAR);
        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            updateSegmentPositions((int) (fraction * TOTAL_DURATION_MS + 0.5f));
            invalidateSelf();
        });
        return animator;
    }

    private void updateSegmentPositions(int playtime) {
        startFraction1 = interpolateSegment(0, playtime);
        endFraction1 = interpolateSegment(1, playtime);
        startFraction2 = interpolateSegment(2, playtime);
        endFraction2 = interpolateSegment(3, playtime);
    }

    private static float interpolateSegment(int index, int playtime) {
        float fraction = (playtime - SEGMENT_DELAY_MS[index]) / (float) SEGMENT_DURATION_MS[index];
        return Math.clamp(SEGMENT_INTERPOLATORS[index].getInterpolation(fraction), 0f, 1f);
    }

    private static float alphaOf(int color) {
        return (color >>> 24) / 255f;
    }
}
