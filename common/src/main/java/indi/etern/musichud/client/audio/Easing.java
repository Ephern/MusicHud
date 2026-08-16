package indi.etern.musichud.client.audio;

import java.util.function.DoubleUnaryOperator;

/**
 * Easing curves for fade in/out transitions.
 */
public enum Easing {
    LINEAR(t -> t),
    EASE_IN_OUT_SINE(t -> (1 - Math.cos(t * Math.PI)) / 2);

    private final DoubleUnaryOperator function;

    Easing(DoubleUnaryOperator function) {
        this.function = function;
    }

    /**
     * Apply the easing curve, clamping the input progress into [0, 1].
     */
    public double apply(double progress) {
        return function.applyAsDouble(Math.clamp(progress, 0, 1));
    }
}
