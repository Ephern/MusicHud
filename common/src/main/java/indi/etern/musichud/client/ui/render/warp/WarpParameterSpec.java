package indi.etern.musichud.client.ui.render.warp;

import java.util.Objects;

/** UI and generator metadata for one macro parameter. */
public record WarpParameterSpec(WarpParameter parameter, float min, float max,
                                float step, float defaultValue) {
    public WarpParameterSpec {
        Objects.requireNonNull(parameter, "parameter");
        if (!Float.isFinite(min) || !Float.isFinite(max) || !Float.isFinite(step)
                || !Float.isFinite(defaultValue) || min >= max || step <= 0) {
            throw new IllegalArgumentException("Invalid warp parameter range");
        }
    }

    /** FloatOption expects a denominator: 100 means a 0.01 step. */
    public int denominator() {
        return Math.max(1, Math.round(1f / step));
    }
}
