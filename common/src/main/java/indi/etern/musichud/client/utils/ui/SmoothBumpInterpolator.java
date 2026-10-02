package indi.etern.musichud.client.utils.ui;

import icyllis.modernui.animation.TimeInterpolator;

/**
 * Smooth, asymmetric "bump" over {@code t in [0, 1]}: value 0 at both ends, peaks at 1.
 * <p>
 * The shape is the normalized monomial {@code t^alpha * (1 - t)^beta}. For
 * {@code alpha > 1} and {@code beta > 1} the value <b>and its derivative</b> are 0 at
 * both ends, so the bump can leave from and return to rest ({@code v = 0}) without a
 * velocity discontinuity. The peak sits at {@code alpha / (alpha + beta)}, so
 * {@code alpha < beta} gives a fast rise and a slower fall, {@code alpha > beta} the
 * opposite, and {@code alpha == beta == 1} degenerates to the symmetric parabola
 * {@code t(1 - t)} (which has non-zero end velocities).
 */
public class SmoothBumpInterpolator implements TimeInterpolator {
    private final float alpha;
    private final float beta;
    private final float peakAt;
    private final float peakValue;

    public SmoothBumpInterpolator(float alpha, float beta) {
        this.alpha = alpha;
        this.beta = beta;
        this.peakAt = alpha / (alpha + beta);
        this.peakValue = (float) (Math.pow(peakAt, alpha) * Math.pow(1 - peakAt, beta));
    }

    @Override
    public float getInterpolation(float t) {
        if (t <= 0f || t >= 1f) {
            return 0f;
        }
        float value = (float) (Math.pow(t, alpha) * Math.pow(1 - t, beta));
        return value / peakValue;
    }
}
