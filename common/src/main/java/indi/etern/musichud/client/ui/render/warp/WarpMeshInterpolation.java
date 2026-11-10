package indi.etern.musichud.client.ui.render.warp;

public enum WarpMeshInterpolation {
    B_SPLINE,
    CATMULL_ROM;

    public String labelKey() {
        return "music_hud.warpPreset.interpolation." + name();
    }
}
