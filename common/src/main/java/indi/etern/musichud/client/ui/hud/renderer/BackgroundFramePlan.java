package indi.etern.musichud.client.ui.hud.renderer;

public record BackgroundFramePlan(Path path, int sceneBlurRadius, int fluidBlurRadius,
                                  float masterOpacity, float samplingBlackOverlay) {
    public enum Path {
        FLUID_ONLY,
        SAMPLING_ONLY,
        MIXED
    }

    public boolean fluidWanted() {
        return path != Path.SAMPLING_ONLY;
    }

    public boolean samplingWanted() {
        return path != Path.FLUID_ONLY;
    }
}
