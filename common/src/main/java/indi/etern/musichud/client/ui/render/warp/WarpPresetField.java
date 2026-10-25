package indi.etern.musichud.client.ui.render.warp;

/** Continuous displacement field sampled at normalized local coordinates. */
@FunctionalInterface
public interface WarpPresetField {
    void sample(float x, float y, WarpPreset preset, float[] out);
}
