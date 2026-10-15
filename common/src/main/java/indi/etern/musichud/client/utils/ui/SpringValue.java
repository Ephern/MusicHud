package indi.etern.musichud.client.utils.ui;

import lombok.Getter;

/**
 * Stateful one-dimensional damped-spring value with closed-form evaluation.
 * <p>
 * Value and velocity are first-class state, so a running animation can be retargeted
 * without losing its velocity (interruptible animation): {@link #setTarget} captures the
 * current {@link #getValue() value}/{@link #getVelocity() velocity} as the new initial
 * conditions and only changes the target. Evaluation is closed-form in the elapsed time
 * (under-damped, critical and over-damped regimes), so it is exact regardless of frame
 * pacing - no per-frame integration drift, no dt noise, and it settles correctly even
 * after a long pause (e.g. the window losing focus).
 * <p>
 * Units are arbitrary (normalized progress, pixels, ...); {@code responseTime} is in
 * seconds and velocity is in value-units per second.
 */
public class SpringValue {
    private static final float SETTLE_EPSILON = 0.01f;

    private float omegaN;
    private float zeta;
    private float omegaD; // under-damped frequency, zeta < 1
    private float omegaOver; // over-damped decay rate, zeta > 1

    @Getter
    private float value;
    @Getter
    private float velocity;
    @Getter
    private float target;

    // initial conditions of the current segment (captured on retarget / set)
    private float startValue;
    private float startVelocity;
    private long startTimeNanos;

    public SpringValue(float responseTime, float dampingFraction) {
        setResponse(responseTime);
        setDamping(dampingFraction);
    }

    public void setResponse(float responseTime) {
        omegaN = (float) (2 * Math.PI / Math.max(responseTime, 1e-4f));
    }

    public void setDamping(float dampingFraction) {
        zeta = Math.max(dampingFraction, 0.005f);
        omegaD = (float) (omegaN * Math.sqrt(Math.max(0f, 1 - zeta * zeta)));
        omegaOver = (float) (omegaN * Math.sqrt(Math.max(0f, zeta * zeta - 1)));
    }

    /**
     * Retarget while preserving the current value and velocity.
     */
    public void setTarget(float target, long nowNanos) {
        this.startValue = value;
        this.startVelocity = velocity;
        this.target = target;
        this.startTimeNanos = nowNanos;
    }

    /**
     * Restart from explicit initial conditions toward {@code target}.
     */
    public void set(float value, float velocity, float target, long nowNanos) {
        this.value = value;
        this.velocity = velocity;
        setTarget(target, nowNanos);
    }

    /**
     * Snap to a value at rest (velocity 0).
     */
    public void jumpTo(float value) {
        this.value = value;
        this.velocity = 0f;
        this.target = value;
        this.startValue = value;
        this.startVelocity = 0f;
        this.startTimeNanos = 0L;
    }

    /**
     * Shift the whole spring - current value, rest target and the running segment - by {@code delta},
     * preserving the velocity. Used to keep a moving frame of reference in sync without interrupting
     * the interpolation.
     */
    public void translate(float delta) {
        value += delta;
        target += delta;
        startValue += delta;
    }

    /**
     * Advance the spring to {@code nowNanos} and return the new value.
     */
    public float update(long nowNanos) {
        float dt = (nowNanos - startTimeNanos) / 1_000_000_000f;
        if (dt < 0f) {
            dt = 0f;
        }

        float d0 = startValue - target;
        float v0 = startVelocity;
        float displacement;
        float displacementVelocity;

        if (zeta < 1f) {
            float a = zeta * omegaN;
            float b = omegaD;
            float exp = (float) Math.exp(-a * dt);
            float cos = (float) Math.cos(b * dt);
            float sin = (float) Math.sin(b * dt);
            float c = (v0 + a * d0) / b;
            displacement = exp * (d0 * cos + c * sin);
            displacementVelocity = exp * ((-a * d0 + c * b) * cos + (-a * c - d0 * b) * sin);
        } else if (zeta > 1f) {
            float a = zeta * omegaN;
            float c = omegaOver;
            float exp = (float) Math.exp(-a * dt);
            float cosh = (float) Math.cosh(c * dt);
            float sinh = (float) Math.sinh(c * dt);
            float k = (v0 + a * d0) / c;
            displacement = exp * (d0 * cosh + k * sinh);
            displacementVelocity = exp * ((-a * d0 + k * c) * cosh + (-a * k + d0 * c) * sinh);
        } else {
            float a = omegaN;
            float exp = (float) Math.exp(-a * dt);
            float k = v0 + a * d0;
            displacement = exp * (d0 + k * dt);
            displacementVelocity = exp * (k - a * d0 - a * k * dt);
        }

        value = target + displacement;
        velocity = displacementVelocity;
        return value;
    }

    public boolean isSettled() {
        return Math.abs(value - target) < SETTLE_EPSILON && Math.abs(velocity) < SETTLE_EPSILON;
    }

}
