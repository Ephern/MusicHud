package indi.etern.musichud.client.utils.ui;

import icyllis.modernui.animation.TimeInterpolator;
import lombok.Getter;

/**
 * Normalized damped-spring easing over {@code t in [0, 1]}, covering all three regimes:
 * <ul>
 *     <li>{@code dampingFraction < 1}: under-damped &mdash; overshoots the target and
 *     settles back (this is the <b>rebound / bounce</b> behaviour);</li>
 *     <li>{@code dampingFraction == 1}: critically damped &mdash; fastest approach with
 *     no overshoot;</li>
 *     <li>{@code dampingFraction > 1}: over-damped &mdash; slower, creep-toward-target
 *     approach with no overshoot.</li>
 * </ul>
 * The response is normalized by its value at {@code duration} so the output is exactly
 * {@code 0} at {@code t = 0} and {@code 1} at {@code t = 1}.
 */
@Getter
public class SpringInterpolator implements TimeInterpolator {
    private final float responseTime; // unit: second
    private final float dampingFraction; // (0, +inf): <1 under-damped (rebound), =1 critical, >1 over-damped
    private final float initialVelocity;
    private final float duration; // calculated duration, unit: second

    private final float omegaN;
    private final float zeta;
    private final float omegaD; // under-damped frequency, zeta < 1
    private final float omegaOver; // over-damped decay rate, zeta > 1
    private final float v0;
    private final float fullValue; // x(duration), used to normalize output to exactly 1.0 at t=1

    /**
     * @param responseTime     unit: second
     * @param dampingFraction  {@code <1} rebound, {@code =1} critical, {@code >1} over-damped
     * @param initialVelocity  initial velocity, in target-units per second
     * @param duration         animation duration, ≤0: auto
     */
    public SpringInterpolator(float responseTime, float dampingFraction,
                              float initialVelocity, float duration) {
        float duration1;
        this.responseTime = responseTime;
        this.dampingFraction = Math.max(dampingFraction, 0.005f);
        this.initialVelocity = initialVelocity;
        this.omegaN = (float) (2 * Math.PI / responseTime);
        this.zeta = this.dampingFraction;
        this.omegaD = (float) (omegaN * Math.sqrt(Math.max(0f, 1 - zeta * zeta)));
        this.omegaOver = (float) (omegaN * Math.sqrt(Math.max(0f, zeta * zeta - 1)));
        this.v0 = initialVelocity;

        if (duration <= 0) {
            float epsilon = 0.001f;
            duration1 = (float) (-Math.log(epsilon) / (zeta * omegaN));
            if (Float.isInfinite(duration1) || duration1 > 60f) {
                duration1 = 60f;
            }
        } else {
            duration1 = duration;
        }
        this.duration = duration1;
        this.fullValue = raw(duration1);
    }

    // auto calculate duration
    public SpringInterpolator(float responseTime, float dampingFraction) {
        this(responseTime, dampingFraction, 0f, -1f);
    }

    @Override
    public float getInterpolation(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return raw(t * duration) / fullValue;
    }

    private float raw(float time) {
        if (zeta < 1f) {
            return underDamping(time);
        }
        if (zeta > 1f) {
            return overDamping(time);
        }
        return criticalDamping(time);
    }

    private float underDamping(float time) {
        float expTerm = (float) Math.exp(-zeta * omegaN * time);
        float cosTerm = (float) Math.cos(omegaD * time);
        float sinTerm = (float) Math.sin(omegaD * time);
        float coeff = (zeta * omegaN - v0) / omegaD;
        return 1 - expTerm * (cosTerm + coeff * sinTerm);
    }

    private float criticalDamping(float time) {
        float expTerm = (float) Math.exp(-omegaN * time);
        float linearTerm = 1 + (omegaN - v0) * time;
        return 1 - expTerm * linearTerm;
    }

    private float overDamping(float time) {
        float expTerm = (float) Math.exp(-zeta * omegaN * time);
        float coshTerm = (float) Math.cosh(omegaOver * time);
        float sinhTerm = (float) Math.sinh(omegaOver * time);
        float coeff = (zeta * omegaN - v0) / omegaOver;
        return 1 - expTerm * (coshTerm + coeff * sinhTerm);
    }
}
