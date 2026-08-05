package indi.etern.musichud.client.utils.ui;

import icyllis.modernui.animation.TimeInterpolator;
import lombok.Getter;

@Getter
public class SpringInterpolator implements TimeInterpolator {
    private final float responseTime; // unit: second
    private final float dampingFraction; // range: 0~1
    private final float initialVelocity;
    private final float duration; // calculated duration, unit: second

    private final float omegaN;
    private final float zeta;
    private final float omegaD; // under damped
    private final float v0;
    private final float fullValue; // x(duration), used to normalize output to exactly 1.0 at t=1

    /**
     * @param responseTime     unit: second
     * @param dampingFraction  range: 0~1
     * @param initialVelocity  range: 0~1
     * @param duration         animation duration, ≤0: auto
     */
    public SpringInterpolator(float responseTime, float dampingFraction,
                              float initialVelocity, float duration) {
        float duration1;
        this.responseTime = responseTime;
        this.dampingFraction = Math.clamp(dampingFraction, 0.005f, 1);
        this.initialVelocity = initialVelocity;
        this.omegaN = (float) (2 * Math.PI / responseTime);
        this.zeta = this.dampingFraction;
        this.omegaD = (float) (omegaN * Math.sqrt(1 - zeta * zeta));
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

        this.fullValue = dampingFraction >= 1f ? criticalDamping(duration1) : underDamping(duration1);
    }

    // auto calculate duration
    public SpringInterpolator(float responseTime, float dampingFraction) {
        this(responseTime, dampingFraction, 0f, -1f);
    }

    @Override
    public float getInterpolation(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;

        float time = t * duration;

        float raw;
        if (dampingFraction >= 1f) {
            raw = criticalDamping(time);
        } else {
            raw = underDamping(time);
        }

        return raw / fullValue;
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
}
