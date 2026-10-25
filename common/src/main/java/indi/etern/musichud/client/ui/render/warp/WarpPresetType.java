package indi.etern.musichud.client.ui.render.warp;

/** Original macro preset families; none are derived from the native capture presets. */
public enum WarpPresetType {
    TILT,
    BULGE,
    SWIRL,
    RIPPLE,
    PINCH,
    NOISE,
    NOISE_FLOW;

    public String labelKey() {
        return "music_hud.warpPreset.type." + name();
    }
}
