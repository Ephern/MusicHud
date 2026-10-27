package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.audio.SpectrumSnapshot;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundImages;
import indi.etern.musichud.client.ui.hud.metadata.DynamicStatusUniform;
import indi.etern.musichud.client.ui.hud.metadata.SceneRenderData;
import indi.etern.musichud.client.ui.hud.presentation.BackgroundState;
import indi.etern.musichud.client.ui.render.AudioReactiveScale;
import indi.etern.musichud.client.ui.render.BackgroundDebugOptions;
import indi.etern.musichud.client.ui.render.BackgroundToneMapping;
import indi.etern.musichud.client.ui.render.FluidBackgroundSettings;
import indi.etern.musichud.client.ui.render.RenderLayer;
import indi.etern.musichud.client.ui.render.RenderRuntime;
import indi.etern.musichud.client.ui.render.SceneElementRenderState;
import indi.etern.musichud.client.ui.render.SceneRenderContext;
import indi.etern.musichud.client.ui.render.SceneStyle;
import indi.etern.musichud.client.ui.render.gpu.SceneBlur;
import indi.etern.musichud.client.ui.render.gpu.SceneBlurCrop;
import indi.etern.musichud.client.ui.render.gpu.SceneCapture;
import indi.etern.musichud.client.ui.render.gpu.SceneCoverFluidScale;
import indi.etern.musichud.client.ui.render.gpu.SceneCoverLayers;
import indi.etern.musichud.client.ui.render.gpu.SceneCoverOverlay;
import indi.etern.musichud.client.ui.render.gpu.SceneCoverTransition;
import indi.etern.musichud.client.ui.render.gpu.SceneCoverWarpProcedural;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuContext;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuTexture;
import indi.etern.musichud.client.ui.render.pipeline.SceneRenderPipelines;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureSetup;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Sizes;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;
import indi.etern.musichud.client.utils.ui.Transitionable;
import indi.etern.musichud.interfaces.ClientConfig;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

public class BackgroundRenderer implements RenderLayer {
    private static final float EPSILON = 1e-4f;
    private static final ClientConfig CLIENT_CONFIG = ClientConfig.getInstance();
    private static final Logger LOGGER = MusicHud.getLogger(BackgroundRenderer.class);
    private static final float DRIFT_FREQ_X1 = (float) Math.sqrt(1.5);
    private static final float DRIFT_FREQ_X2 = (float) Math.sqrt(4.3);
    private static final float DRIFT_FREQ_Y1 = (float) Math.sqrt(2.7);
    private static final float DRIFT_FREQ_Y2 = (float) Math.sqrt(4.93);
    private static final float ORBIT_DRIFT_FREQ_SCALE = (float) Math.sqrt(1.2);
    private final BackgroundFrameResolver frameResolver;
    private final SceneCapture sceneCapture = new SceneCapture();
    private final SceneBlur backgroundBlur = new SceneBlur();
    private final SceneBlurCrop backgroundCrop = new SceneBlurCrop();
    private final SceneBlur fluidBlur = new SceneBlur();
    private final SceneCoverTransition sceneCoverTransition = new SceneCoverTransition();
    private final SceneCoverWarpProcedural sceneCoverWarpProcedural = new SceneCoverWarpProcedural();
    private final SceneCoverFluidScale sceneCoverFluidScale = new SceneCoverFluidScale();
    private final SceneCoverLayers sceneCoverLayers = new SceneCoverLayers();
    private final SceneCoverOverlay sceneCoverOverlay = new SceneCoverOverlay();
    private SceneRenderData currentData;
    private final DynamicStatusUniform dynamicStatusUniform = DynamicStatusUniform.getInstance();

    public BackgroundRenderer(BackgroundTarget target) {
        this.frameResolver = new BackgroundFrameResolver(target);
    }

    public void configure(SceneRenderData data) {
        this.currentData = data;
    }

    @Override
    public void render(SceneRenderContext context) {
        if (currentData == null) return;

        var transition = currentData.getTransitionableBackground();
        dynamicStatusUniform.setTransitionable(transition);
        if (renderFluidBackground(context, currentData.getHudStyle())) {
            context.nextStratum();
            return;
        }
        renderSolidFallback(context, currentData.getHudStyle());
    }

    public boolean renderFluidBackground(SceneRenderContext context, SceneStyle style) {
        if (style == null || style.getWidth() <= 0 || style.getHeight() <= 0
                || style.getAlpha() <= EPSILON) return false;

        SceneGpuContext gpu;
        try {
            gpu = RenderRuntime.getInstance().gpu();
        } catch (RuntimeException failure) {
            LOGGER.debug("Scene capture is unavailable", failure);
            return false;
        }
        SceneGpuContext.MainColorSource source = gpu.mainColorSource();
        if (source == null) return false;
        SceneCapture.Region region = captureRegion(context, style, source);
        if (region == null) return false;

        int canvasWidth = (int) Math.ceil(region.width());
        int canvasHeight = (int) Math.ceil(region.height());
        float time = MusicHud.getRunningMillis() / 1000f;

        float fluidOpacityConfig = frameResolver.fluidOpacityConfig();
        SceneCoverTransition.Frame coverFrame;
        SceneGpuTexture cover = null;
        float masterOpacity = 0f;
        if (fluidOpacityConfig > EPSILON) {
            ResolvedCover resolvedCover = resolveCoverImages();
            if (resolvedCover != null) {
                coverFrame = sceneCoverTransition.update(
                        gpu, resolvedCover.images(), resolvedCover.changedAtMillis(),
                        coverTargetLongEdge(region), time);
                cover = coverFrame.cover();
                masterOpacity = fluidOpacityConfig * coverFrame.opacityScale();
            }
        }

        float layerOpacity = FluidBackgroundSettings.COVER_LAYER_OPACITY;
        BackgroundFramePlan plan = frameResolver.plan(
                style, source, cover != null, masterOpacity);
        boolean fluidWanted = plan.fluidWanted();
        boolean fluidAttempted = false;

        if (plan.path() == BackgroundFramePlan.Path.FLUID_ONLY) {
            fluidAttempted = true;
            SceneGpuTexture fluid = buildFluid(gpu, cover, region, canvasWidth, canvasHeight,
                    time, layerOpacity, plan.fluidBlurRadius());
            if (fluid != null) {
                submitFluidBackground(context, style, fluid, true, 0f);
                return true;
            }
        }

        CapturePlan capturePlan = capturePlan(region, source, plan.sceneBlurRadius());
        if (capturePlan == null) return false;
        SceneGpuTexture captured = sceneCapture.capture(gpu, source, capturePlan.captureRegion());
        if (captured == null) return false;
        SceneGpuTexture background = captured;
        int sceneBlurRadius = plan.sceneBlurRadius();
        if (sceneBlurRadius > 0) {
            SceneBlur.PendingBlur blur = backgroundBlur.prepare(gpu,
                    captured.width(), captured.height(), sceneBlurRadius / 3f);
            if (blur != null && blur.apply(gpu, captured)) {
                background = blur.target();
            }
        }
        if (capturePlan.needsCrop()) {
            SceneGpuTexture cropped = backgroundCrop.apply(gpu, background,
                    capturePlan.u0(), capturePlan.v0(), capturePlan.u1(), capturePlan.v1(),
                    capturePlan.targetWidth(), capturePlan.targetHeight());
            if (cropped == null) return false;
            background = cropped;
        }

        SceneGpuTexture output = background;
        boolean backgroundOnly = true;
        SceneUniform adjustUniform = backgroundAdjustUniform(false, 0f);
        if (fluidWanted) {
            if (!fluidAttempted) {
                SceneGpuTexture fluid = buildFluid(gpu, cover, region, canvasWidth, canvasHeight,
                        time, layerOpacity, plan.fluidBlurRadius());
                if (fluid != null) {
                    SceneGpuTexture composite = sceneCoverOverlay.apply(
                            gpu, background, fluid, plan.masterOpacity(),
                            plan.samplingBlackOverlay(), adjustUniform);
                    if (composite != null) {
                        output = composite;
                        backgroundOnly = false;
                    }
                }
            }
        }

        submitFluidBackground(context, style, output, false,
                backgroundOnly ? plan.samplingBlackOverlay() : 0f);
        return true;
    }

    private SceneGpuTexture buildFluid(SceneGpuContext gpu, SceneGpuTexture cover,
                                       SceneCapture.Region region, int canvasWidth,
                                       int canvasHeight, float time, float layerOpacity,
                                       int fluidBlurRadius) {
        SceneCoverLayers.CoverUniform uniform = createCoverUniform(
                region, time, cover, layerOpacity);
        SceneGpuTexture layers = sceneCoverLayers.apply(gpu, cover, uniform,
                canvasWidth, canvasHeight);
        if (layers == null) return null;
        SceneGpuTexture fluid = layers;
        if (fluidBlurRadius > 0) {
            fluid = blurTexture(fluidBlur, gpu, layers, fluidBlurRadius,
                    Math.max(1, Math.max(canvasWidth, canvasHeight)));
        }
        SceneGpuTexture warped = sceneCoverWarpProcedural.apply(gpu, fluid, time);
        SceneGpuTexture result = warped != null ? warped : fluid;
        return sceneCoverFluidScale.apply(gpu, result, audioReactiveScale());
    }

    private static SceneGpuTexture blurTexture(SceneBlur blur, SceneGpuContext gpu,
                                               SceneGpuTexture source, int radius,
                                               int canvasLong) {
        int sourceLong = Math.max(source.width(), source.height());
        float scale = sourceLong / (float) Math.max(1, canvasLong);
        float sigma = (radius / 3f) * scale;
        SceneBlur.PendingBlur pending = blur.prepare(gpu, source.width(), source.height(), sigma);
        if (pending != null && pending.apply(gpu, source)) {
            return pending.target();
        }
        return source;
    }

    private static float audioReactiveScale() {
        try {
            SpectrumSnapshot spectrum = StreamAudioPlayer.getInstance().getSpectrum();
            return AudioReactiveScale.compute(spectrum, FluidBackgroundSettings.AUDIO_SCALE_AMOUNT,
                    FluidBackgroundSettings.AUDIO_LOW_WEIGHT,
                    FluidBackgroundSettings.AUDIO_MID_WEIGHT);
        } catch (RuntimeException failure) {
            LOGGER.debug("While reading the audio spectrum", failure);
            return 1f;
        }
    }

    private void submitFluidBackground(SceneRenderContext context, SceneStyle style,
                                       SceneGpuTexture texture, boolean applyColorAdjust,
                                       float blackOverlay) {
        context.submit(this, new SceneElementRenderState(
                SceneRenderPipelines.FLUID_BACKGROUND,
                SceneTextureSetup.single(texture),
                context.currentPose(),
                style,
                style,
                backgroundAdjustUniform(applyColorAdjust, blackOverlay)
        ));
    }

    private static BackgroundAdjustUniform backgroundAdjustUniform(boolean applyColorAdjust,
                                                                    float blackOverlay) {
        return new BackgroundAdjustUniform(
                (float) CLIENT_CONFIG.getGuiBackgroundSaturation(),
                FluidBackgroundSettings.HIGHLIGHT_DARKENING,
                FluidBackgroundSettings.HIGHLIGHT_KNEE,
                FluidBackgroundSettings.HIGHLIGHT_FULL,
                (float) CLIENT_CONFIG.getGuiBackgroundOverallBrightness(),
                applyColorAdjust,
                blackOverlay,
                BackgroundDebugOptions.toneMapping == BackgroundToneMapping.GAMMA ? 1f : 0f);
    }

    private static SceneCoverLayers.CoverUniform createCoverUniform(
            SceneCapture.Region region, float time, SceneGpuTexture cover,
            float layerOpacity) {
        float rotationSpeed = (float) Math.toRadians(
                FluidBackgroundSettings.COVER_ROTATION_SPEED_DEGREES_PER_SECOND);
        float driftSpeed = FluidBackgroundSettings.COVER_DRIFT_SPEED;
        float driftX = coverDriftX(FluidBackgroundSettings.COVER_DRIFT_X,
                time, rotationSpeed, driftSpeed, 1f);
        float driftY = coverDriftY(FluidBackgroundSettings.COVER_DRIFT_Y,
                time, rotationSpeed, driftSpeed, 1f);
        float orbitAngle = rotationSpeed
                * SceneCoverLayers.THIRD_LAYER_ORBIT_SPEED_RATIO * time;
        float orbitCenterX = coverDriftX(
                FluidBackgroundSettings.COVER_ORBIT_DRIFT_X,
                time, rotationSpeed, driftSpeed, ORBIT_DRIFT_FREQ_SCALE);
        float orbitCenterY = coverDriftY(
                FluidBackgroundSettings.COVER_ORBIT_DRIFT_Y,
                time, rotationSpeed, driftSpeed, ORBIT_DRIFT_FREQ_SCALE);
        float thirdCenterX = orbitCenterX + (float) Math.cos(orbitAngle)
                * SceneCoverLayers.THIRD_LAYER_ORBIT_RADIUS;
        float thirdCenterY = orbitCenterY + (float) Math.sin(orbitAngle)
                * SceneCoverLayers.THIRD_LAYER_ORBIT_RADIUS;
        return SceneCoverLayers.CoverUniform.create(
                rotationSpeed,
                layerOpacity,
                time,
                driftX,
                driftY,
                thirdCenterX,
                thirdCenterY,
                region.width() / region.height(),
                cover.width(),
                cover.height());
    }

    private static int coverTargetLongEdge(SceneCapture.Region region) {
        float shortEdge = Math.min(region.width(), region.height());
        return Math.clamp((int) Math.ceil(shortEdge * 0.5f), 64, 640);
    }

    private static float coverDriftX(float amplitude, float time, float rotationSpeed,
                                     float driftSpeed, float frequencyScale) {
        float t = time * rotationSpeed * driftSpeed * frequencyScale;
        return amplitude * (0.65f * (float) Math.sin(t * DRIFT_FREQ_X1 + 0.7f)
                + 0.35f * (float) Math.sin(t * DRIFT_FREQ_X2 + 2.1f));
    }

    private static float coverDriftY(float amplitude, float time, float rotationSpeed,
                                     float driftSpeed, float frequencyScale) {
        float t = time * rotationSpeed * driftSpeed * frequencyScale;
        return amplitude * (0.65f * (float) Math.sin(t * DRIFT_FREQ_Y1 + 1.9f)
                + 0.35f * (float) Math.sin(t * DRIFT_FREQ_Y2 + 4.3f));
    }

    private @Nullable ResolvedCover resolveCoverImages() {
        try {
            BackgroundState backgroundState = BackgroundState.getInstance();
            if (frameResolver.usesOverrides()) {
                BackgroundData override = backgroundState.getOverrides().getTop();
                if (hasImage(override)) {
                    return new ResolvedCover(override.image(),
                            backgroundState.getEffectiveBackgroundChangeTimeMillis());
                }
            }
            Transitionable<BackgroundData> transition = backgroundState.getTransition();
            BackgroundData mixed = transition.getMixed();
            if (!hasImage(mixed)) return null;
            return new ResolvedCover(mixed.image(), frameResolver.usesOverrides()
                    ? backgroundState.getEffectiveBackgroundChangeTimeMillis()
                    : backgroundState.getBaseEffectiveChangeTimeMillis());
        } catch (RuntimeException failure) {
            LOGGER.debug("While resolving the background cover image", failure);
            return null;
        }
    }

    private static boolean hasImage(@Nullable BackgroundData data) {
        return data != null && data.image() != null
                && (data.image().current != null || data.image().original != null);
    }

    private static @Nullable SceneCapture.Region captureRegion(SceneRenderContext context, SceneStyle style,
                                                                SceneGpuContext.MainColorSource source) {
        SceneStyle.AbsolutePosition center = style.calcAbsoluteCenterPosition(context);
        float scale = source.guiScale();
        float width = style.getWidth() * scale;
        float height = style.getHeight() * scale;
        if (width <= 0f || height <= 0f) return null;
        float left = (center.x() - style.getWidth() / 2f) * scale;
        float top = (center.y() - style.getHeight() / 2f) * scale;
        return new SceneCapture.Region(left, top, width, height);
    }

    private static @Nullable CapturePlan capturePlan(SceneCapture.Region hudRegion,
                                                      SceneGpuContext.MainColorSource source,
                                                      int blurRadius) {
        if (hudRegion == null) return null;
        float padding = blurRadius > 0 ? blurRadius + 2f : 0f;
        float left = hudRegion.left();
        float top = hudRegion.top();
        float right = left + hudRegion.width();
        float bottom = top + hudRegion.height();

        float captureLeft = Math.max(0f, left - padding);
        float captureTop = Math.max(0f, top - padding);
        float captureRight = Math.min(source.width(), right + padding);
        float captureBottom = Math.min(source.height(), bottom + padding);
        if (captureRight - captureLeft < 1f || captureBottom - captureTop < 1f) {
            return null;
        }

        SceneCapture.Region captureRegion = new SceneCapture.Region(
                captureLeft, captureTop, captureRight - captureLeft, captureBottom - captureTop);
        float captureWidth = captureRegion.width();
        float captureHeight = captureRegion.height();
        float u0 = (left - captureLeft) / captureWidth;
        float v0 = (top - captureTop) / captureHeight;
        float u1 = (right - captureLeft) / captureWidth;
        float v1 = (bottom - captureTop) / captureHeight;
        int targetWidth = Math.max(1, (int) Math.ceil(hudRegion.width()));
        int targetHeight = Math.max(1, (int) Math.ceil(hudRegion.height()));
        boolean needsCrop = Math.abs(captureWidth - hudRegion.width()) > 0.01f
                || Math.abs(captureHeight - hudRegion.height()) > 0.01f
                || Math.abs(captureLeft - left) > 0.01f
                || Math.abs(captureTop - top) > 0.01f;
        return new CapturePlan(captureRegion, u0, v0, u1, v1,
                targetWidth, targetHeight, needsCrop);
    }

    private void renderSolidFallback(SceneRenderContext context, SceneStyle style) {
        if (style == null || style.getAlpha() <= EPSILON) return;
        float overlay = (float) CLIENT_CONFIG.getHudBackgroundSamplingBlackOverlay();
        if (overlay <= EPSILON) return;
        context.submit(this, new SceneElementRenderState(
                SceneRenderPipelines.SOLID_BACKGROUND,
                SceneTextureSetup.NONE,
                context.currentPose(),
                style,
                style,
                new SolidBackgroundUniform(0f, 0f, 0f, overlay)
        ));
        context.nextStratum();
    }

    private record SolidBackgroundUniform(float red, float green, float blue, float alpha)
            implements SceneUniform {
        private static final String UBO_NAME = "MHSolidColor";
        private static final int UBO_SIZE = Std140Sizes.calc().putVec4().get();

        @Override
        public String getUBOName() {
            return UBO_NAME;
        }

        @Override
        public int getUBOSize() {
            return UBO_SIZE;
        }

        @Override
        public void write(Std140Writer writer) {
            writer.putVec4(red, green, blue, alpha);
        }

        @Override
        public boolean shouldUseBuffer(SceneUniform lastBuffered) {
            return false;
        }
    }

    private record BackgroundAdjustUniform(float saturation, float highlightDarkening,
                                           float highlightKnee, float highlightFull,
                                           float overallBrightness, boolean applyColorAdjust,
                                           float blackOverlay, float gammaToneMapping)
            implements SceneUniform {
        private static final String UBO_NAME = "MHBackgroundAdjust";
        private static final int UBO_SIZE = Std140Sizes.calc()
                .putVec4().putVec4().get();

        @Override
        public String getUBOName() {
            return UBO_NAME;
        }

        @Override
        public int getUBOSize() {
            return UBO_SIZE;
        }

        @Override
        public void write(Std140Writer writer) {
            writer.putVec4(saturation, highlightDarkening, highlightKnee, highlightFull);
            writer.putVec4(overallBrightness, applyColorAdjust ? 1f : 0f,
                    blackOverlay, gammaToneMapping);
        }

        @Override
        public boolean shouldUseBuffer(SceneUniform lastBuffered) {
            return false;
        }
    }

    private record ResolvedCover(BackgroundImages images, long changedAtMillis) {
    }

    private record CapturePlan(SceneCapture.Region captureRegion,
                               float u0, float v0, float u1, float v1,
                               int targetWidth, int targetHeight,
                               boolean needsCrop) {
    }
}
