package indi.etern.musichud.client.ui.render.warp;

/** Macro parameters exposed by the original warp preset generator. */
public enum WarpParameter {
    CENTER_X,
    CENTER_Y,
    RADIUS,
    AMPLITUDE,
    ANGLE,
    ASYMMETRY,
    PHASE,
    PERSPECTIVE,
    ANISOTROPY,
    WAVELENGTH,
    DECAY,
    TWIST,
    SQUEEZE,
    SCALE,
    OCTAVES,
    GLOBAL,
    DETAIL,
    COMPRESS,
    STRETCH,
    ZOOM;

    public String labelKey() {
        return "music_hud.warpPreset.param." + name();
    }
}
