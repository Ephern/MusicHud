package indi.etern.musichud.client.ui.hud.renderer;

/**
 * Immutable decision produced by {@link BackgroundFrameResolver}.
 * <p>
 * {@code FLUID_ONLY} skips scene capture, {@code SAMPLING_ONLY} skips the fluid layer, and
 * {@code MIXED} runs both pipelines before compositing.
 */
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
