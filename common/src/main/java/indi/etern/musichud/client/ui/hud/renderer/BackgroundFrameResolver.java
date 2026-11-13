package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.render.FluidBackgroundSettings;
import indi.etern.musichud.client.ui.render.SceneStyle;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuContext;
import indi.etern.musichud.interfaces.ClientConfig;

public final class BackgroundFrameResolver {
    private static final float EPSILON = 1e-4f;
    private static final ClientConfig CONFIG = ClientConfig.getInstance();

    private final BackgroundTarget target;

    public BackgroundFrameResolver(BackgroundTarget target) {
        this.target = target;
    }

    public boolean usesOverrides() {
        return target == BackgroundTarget.GUI;
    }

    public float fluidOpacityConfig() {
        return (float) (target == BackgroundTarget.GUI
                ? CONFIG.getGuiBackgroundFluidOpacity()
                : CONFIG.getHudBackgroundFluidOpacity());
    }

    public BackgroundFramePlan plan(SceneStyle style,
                                    SceneGpuContext.MainColorSource source,
                                    boolean hasCover,
                                    float masterOpacity) {
        BackgroundFramePlan.Path path;
        if (!hasCover || masterOpacity <= EPSILON) {
            path = BackgroundFramePlan.Path.SAMPLING_ONLY;
        } else if (masterOpacity >= 1f - EPSILON) {
            path = BackgroundFramePlan.Path.FLUID_ONLY;
        } else {
            path = BackgroundFramePlan.Path.MIXED;
        }
        return new BackgroundFramePlan(
                path,
                sceneBlurRadius(),
                fluidBlurRadius(style, source),
                masterOpacity,
                samplingBlackOverlay());
    }

    private int sceneBlurRadius() {
        return target == BackgroundTarget.GUI
                ? CONFIG.getGuiBackgroundBlurRadius()
                : CONFIG.getHudBackgroundBlurRadius();
    }

    private int fluidBlurRadius(SceneStyle style, SceneGpuContext.MainColorSource source) {
        if (target == BackgroundTarget.GUI) {
            return FluidBackgroundSettings.GUI_FLUID_BLUR_RADIUS;
        }
        int lowerBound = Math.min(FluidBackgroundSettings.HUD_FLUID_BLUR_RADIUS,
                FluidBackgroundSettings.GUI_FLUID_BLUR_RADIUS);
        float referenceHeight = FluidBackgroundSettings.HUD_FLUID_RADIUS_REFERENCE_HEIGHT;
        float guiHeight = Math.max(1f, source.height() / (float) Math.max(1, source.guiScale()));
        float t = (style.getHeight() - referenceHeight) / Math.max(1f, guiHeight - referenceHeight);
        t = Math.clamp(t, 0f, 1f);
        float soft = softPlus(t, FluidBackgroundSettings.HUD_FLUID_RADIUS_SOFTNESS);
        return Math.round(lowerBound + (FluidBackgroundSettings.GUI_FLUID_BLUR_RADIUS - lowerBound)
                * Math.clamp(soft, 0f, 1f));
    }

    private float samplingBlackOverlay() {
        return (float) (target == BackgroundTarget.GUI
                ? CONFIG.getGuiBackgroundSamplingBlackOverlay()
                : CONFIG.getHudBackgroundSamplingBlackOverlay());
    }

    private static float softPlus(float value, float softness) {
        return (float) (Math.log1p(Math.exp(value * softness)) / softness);
    }
}
