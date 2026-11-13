package indi.etern.musichud.client.utils.ui;

import icyllis.modernui.animation.TimeInterpolator;

/**
 * Asymmetric "bump" over {@code t in [0, 1]}: 0 at both ends, 1 at the peak {@code alpha/(alpha+beta)}.
 * The normalized monomial {@code t^alpha * (1-t)^beta} has zero value and slope at both ends for
 * {@code alpha, beta > 1}, so the bump leaves and returns to rest without a velocity step.
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
