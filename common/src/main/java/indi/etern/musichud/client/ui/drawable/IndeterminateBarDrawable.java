package indi.etern.musichud.client.ui.drawable;

import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.graphics.drawable.Animatable;
import icyllis.modernui.graphics.drawable.Drawable;

/**
 * Indeterminate companion to {@link LinearProgressDrawable}: a pill band sweeping inside a pill
 * track. The band never enters the track caps, so no path clipping is needed.
 */
public final class IndeterminateBarDrawable extends Drawable
        implements Animatable, ValueAnimator.AnimatorUpdateListener {

    private static final long SWEEP_DURATION_MS = 1600;
    private static final float BAND_RATIO = 0.35f;

    private final int heightPx;
    private final float trackThicknessPx;
    private final float cornerRadiusPx;

    private final Paint trackPaint = new Paint();
    private final Paint bandPaint = new Paint();
    private final ValueAnimator animator;

    private float phase;

    /**
     * @param trackThicknessPx fixed track thickness, or {@code <= 0} to fill the drawable height
     * @param heightPx         intrinsic height reported to the owning widget
     */
    public IndeterminateBarDrawable(int trackColor, int bandColor, int heightPx,
                                    float trackThicknessPx, float cornerRadiusPx) {
        this.heightPx = heightPx;
        this.trackThicknessPx = trackThicknessPx;
        this.cornerRadiusPx = cornerRadiusPx;

        trackPaint.setAntiAlias(true);
        trackPaint.setColor(trackColor);
        bandPaint.setAntiAlias(true);
        bandPaint.setColor(bandColor);

        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(SWEEP_DURATION_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(TimeInterpolator.LINEAR);
        animator.addUpdateListener(this);
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

        canvas.drawRoundRect(bounds.left, top, bounds.right, bottom, radius, trackPaint);

        final float span = width - radius * 2;
        if (span <= 0) {
            return;
        }
        //noinspection MathClampMigration
        final float band = Math.min(span, Math.max(span * BAND_RATIO, radius * 2));
        final float bandLeft = bounds.left + radius + (span - band) * phase;
        canvas.drawRoundRect(bandLeft, top, bandLeft + band, bottom, radius, bandPaint);
    }

    @Override
    public void onAnimationUpdate(@NonNull ValueAnimator animation) {
        Object value = animation.getAnimatedValue();
        if (value instanceof Float f) {
            phase = f;
        }
        invalidateSelf();
    }

    @Override
    public void start() {
        if (!animator.isRunning()) {
            animator.start();
        }
    }

    @Override
    public void stop() {
        animator.cancel();
    }

    @Override
    public boolean isRunning() {
        return animator.isRunning();
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (visible) {
            if (restart) {
                start();
            }
        } else {
            stop();
        }
        return changed;
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
