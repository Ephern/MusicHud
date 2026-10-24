package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.ui.hud.metadata.DynamicStatusUniform;
import indi.etern.musichud.client.ui.hud.metadata.SceneRenderData;
import indi.etern.musichud.client.ui.hud.presentation.HudPresentationService;
import indi.etern.musichud.client.ui.render.CoverWarpSettings;
import indi.etern.musichud.client.ui.render.RenderLayer;
import indi.etern.musichud.client.ui.render.RenderService;
import indi.etern.musichud.client.ui.render.SceneElementRenderState;
import indi.etern.musichud.client.ui.render.SceneRenderContext;
import indi.etern.musichud.client.ui.render.SceneStyle;
import indi.etern.musichud.client.ui.render.gpu.SceneBlur;
import indi.etern.musichud.client.ui.render.gpu.SceneCapture;
import indi.etern.musichud.client.ui.render.gpu.SceneCoverLayers;
import indi.etern.musichud.client.ui.render.gpu.SceneCoverOverlay;
import indi.etern.musichud.client.ui.render.gpu.SceneCoverTransition;
import indi.etern.musichud.client.ui.render.gpu.SceneCoverWarp;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuContext;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuTexture;
import indi.etern.musichud.client.ui.render.pipeline.SceneRenderPipelines;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureSetup;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Sizes;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;
import indi.etern.musichud.client.utils.image.ImageTextureData;
import indi.etern.musichud.client.utils.ui.Transitionable;
import indi.etern.musichud.interfaces.ClientConfig;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

public class BackgroundRenderer implements RenderLayer {
    private static final BackgroundData HUD_IDLE_BACKGROUND = BackgroundData.solid(0x202020);
    private static final ClientConfig CLIENT_CONFIG = ClientConfig.getInstance();
    private static final Logger LOGGER = MusicHud.getLogger(BackgroundRenderer.class);
    private static final float DRIFT_FREQ_X1 = (float) Math.sqrt(1.5);
    private static final float DRIFT_FREQ_X2 = (float) Math.sqrt(4.3);
    private static final float DRIFT_FREQ_Y1 = (float) Math.sqrt(2.7);
    private static final float DRIFT_FREQ_Y2 = (float) Math.sqrt(4.93);
    private static final float ORBIT_DRIFT_FREQ_SCALE = (float) Math.sqrt(1.2);
    private final SceneCapture sceneCapture = new SceneCapture();
    private final SceneBlur backgroundBlur = new SceneBlur();
    private final SceneBlur fluidBlur = new SceneBlur();
    private final SceneCoverTransition sceneCoverTransition = new SceneCoverTransition();
    private final SceneCoverWarp sceneCoverWarp = new SceneCoverWarp();
    private final SceneCoverLayers sceneCoverLayers = new SceneCoverLayers();
    private final SceneCoverOverlay sceneCoverOverlay = new SceneCoverOverlay();
    private final boolean allowDebugPreview;
    private SceneRenderData currentData;
    private final DynamicStatusUniform dynamicStatusUniform = DynamicStatusUniform.getInstance();

    public BackgroundRenderer() {
        this(false);
    }

    public BackgroundRenderer(boolean allowDebugPreview) {
        this.allowDebugPreview = allowDebugPreview;
    }

    public void configure(SceneRenderData data) {
        this.currentData = data;
    }

    @Override
    public void render(SceneRenderContext context) {
        render(context, false, 0);
    }

    public void render(SceneRenderContext context, int blurRadius) {
        render(context, false, blurRadius);
    }

    public void render(SceneRenderContext context, boolean idle) {
        render(context, idle, 0);
    }

    public void render(SceneRenderContext context, boolean idle, int blurRadius) {
        if (currentData == null) return;

        var transition = currentData.getTransitionableBackground();
        dynamicStatusUniform.setTransitionable(transition);
        BackgroundData mixed = transition == null ? null : transition.getMixed();
        var image = mixed == null ? null : mixed.image();
        if (idle || image == null || (image.current == null && image.original == null)) {
            render(context, currentData.getHudStyle(), HUD_IDLE_BACKGROUND);
            return;
        }
        if (renderExpBackground(context, currentData.getHudStyle(), blurRadius)) {
            context.nextStratum();
            return;
        }
        render(context, currentData.getHudStyle(), HUD_IDLE_BACKGROUND);
    }

    public boolean renderExpBackground(SceneRenderContext context, SceneStyle style) {
        return renderExpBackground(context, style, 0);
    }

    public boolean renderExpBackground(SceneRenderContext context, SceneStyle style, int blurRadius) {
        if (style == null || style.getWidth() <= 0 || style.getHeight() <= 0
                || style.getAlpha() <= 1e-4f) return false;

        SceneGpuContext gpu;
        try {
            gpu = RenderService.getInstance().gpu();
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
        int canvasLong = Math.max(1, Math.max(canvasWidth, canvasHeight));

        ImageTextureData coverImage = resolveCoverImage();
        float time = MusicHud.getRunningMillis() / 1000f;
        SceneCoverTransition.Frame coverFrame = sceneCoverTransition.update(
                gpu, coverImage, coverTargetLongEdge(region), time);
        CoverWarpSettings warpSettings = CoverWarpSettings.getInstance();
        CoverWarpSettings.Snapshot warpSnapshot = warpSettings.snapshot();
        SceneGpuTexture cover = coverFrame.cover();
        float layerOpacity = (float) CLIENT_CONFIG.getGuiBackgroundCoverLayerOpacity();
        float masterOpacity = (float) (CLIENT_CONFIG.getGuiBackgroundCoverOpacity()
                * coverFrame.opacityScale());
        int fluidBlurRadius = CLIENT_CONFIG.getGuiBackgroundFluidBlurRadius();

        CoverWarpSettings.PreviewMode previewMode = warpSettings.getPreviewMode();
        if (allowDebugPreview && previewMode != CoverWarpSettings.PreviewMode.OFF) {
            if (cover == null) return false;
            SceneGpuTexture preview;
            if (previewMode == CoverWarpSettings.PreviewMode.LAYERS) {
                preview = buildFluid(gpu, cover, region, canvasWidth, canvasHeight,
                        time, layerOpacity, warpSnapshot, fluidBlurRadius);
            } else {
                SceneGpuTexture sourceCover = cover;
                if (warpSettings.isTiledPreviewBlur() && fluidBlurRadius > 0) {
                    sourceCover = blurTexture(fluidBlur, gpu, cover,
                            fluidBlurRadius, canvasLong);
                }
                preview = sceneCoverWarp.apply(gpu, sourceCover, warpSnapshot,
                        coverFrame.animated());
            }
            if (preview == null) return false;
            submitCoverPreview(context, style, preview);
            return true;
        }

        boolean fluidWanted = cover != null && masterOpacity > 1e-4f && layerOpacity > 1e-4f;
        SceneGpuTexture fluid = null;
        boolean fluidAttempted = false;

        if (fluidWanted && masterOpacity >= 1f - 1e-4f) {
            fluidAttempted = true;
            fluid = buildFluid(gpu, cover, region, canvasWidth, canvasHeight,
                    time, layerOpacity, warpSnapshot, fluidBlurRadius);
            if (fluid != null) {
                submitExpBackground(context, style, fluid);
                return true;
            }
        }

        SceneGpuTexture captured = sceneCapture.capture(gpu, source, region);
        if (captured == null) return false;
        SceneGpuTexture background = captured;
        if (blurRadius > 0) {
            SceneBlur.PendingBlur blur = backgroundBlur.prepare(gpu,
                    captured.width(), captured.height(), blurRadius / 3f);
            if (blur != null && blur.apply(gpu, captured)) {
                background = blur.target();
            }
        }

        SceneGpuTexture output = background;
        if (fluidWanted) {
            if (!fluidAttempted) {
                fluid = buildFluid(gpu, cover, region, canvasWidth, canvasHeight,
                        time, layerOpacity, warpSnapshot, fluidBlurRadius);
            }
            if (fluid != null) {
                SceneGpuTexture composite = sceneCoverOverlay.apply(
                        gpu, background, fluid, masterOpacity);
                if (composite != null) {
                    output = composite;
                }
            }
        }

        submitExpBackground(context, style, output);
        return true;
    }

    private SceneGpuTexture buildFluid(SceneGpuContext gpu, SceneGpuTexture cover,
                                       SceneCapture.Region region, int canvasWidth,
                                       int canvasHeight, float time, float layerOpacity,
                                       CoverWarpSettings.Snapshot snapshot,
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
        SceneGpuTexture warped = sceneCoverWarp.apply(gpu, fluid, snapshot, true);
        return warped != null ? warped : fluid;
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

    private void submitCoverPreview(SceneRenderContext context, SceneStyle style,
                                    SceneGpuTexture texture) {
        context.submit(this, new SceneElementRenderState(
                SceneRenderPipelines.COVER_PREVIEW,
                SceneTextureSetup.single(texture),
                context.currentPose(),
                style,
                style,
                backgroundAdjustUniform()
        ));
    }

    private void submitExpBackground(SceneRenderContext context, SceneStyle style,
                                     SceneGpuTexture texture) {
        context.submit(this, new SceneElementRenderState(
                SceneRenderPipelines.EXP_BACKGROUND,
                SceneTextureSetup.single(texture),
                context.currentPose(),
                style,
                style,
                backgroundAdjustUniform()
        ));
    }

    private static BackgroundAdjustUniform backgroundAdjustUniform() {
        return new BackgroundAdjustUniform(
                (float) CLIENT_CONFIG.getGuiBackgroundSaturation());
    }

    private static SceneCoverLayers.CoverUniform createCoverUniform(
            SceneCapture.Region region, float time, SceneGpuTexture cover,
            float layerOpacity) {
        float rotationSpeed = (float) Math.toRadians(
                CLIENT_CONFIG.getGuiBackgroundCoverRotationSpeed());
        float driftSpeed = (float) CLIENT_CONFIG.getGuiBackgroundCoverDriftSpeed();
        float driftX = coverDriftX(CLIENT_CONFIG.getGuiBackgroundCoverDriftX(),
                time, rotationSpeed, driftSpeed, 1f);
        float driftY = coverDriftY(CLIENT_CONFIG.getGuiBackgroundCoverDriftY(),
                time, rotationSpeed, driftSpeed, 1f);
        float orbitAngle = rotationSpeed
                * SceneCoverLayers.THIRD_LAYER_ORBIT_SPEED_RATIO * time;
        float orbitCenterX = coverDriftX(
                CLIENT_CONFIG.getGuiBackgroundCoverOrbitDriftX(),
                time, rotationSpeed, driftSpeed, ORBIT_DRIFT_FREQ_SCALE);
        float orbitCenterY = coverDriftY(
                CLIENT_CONFIG.getGuiBackgroundCoverOrbitDriftY(),
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

    private static float coverDriftX(double amplitude, float time, float rotationSpeed,
                                     float driftSpeed, float frequencyScale) {
        float t = time * rotationSpeed * driftSpeed * frequencyScale;
        return (float) amplitude * (0.65f * (float) Math.sin(t * DRIFT_FREQ_X1 + 0.7f)
                + 0.35f * (float) Math.sin(t * DRIFT_FREQ_X2 + 2.1f));
    }

    private static float coverDriftY(double amplitude, float time, float rotationSpeed,
                                     float driftSpeed, float frequencyScale) {
        float t = time * rotationSpeed * driftSpeed * frequencyScale;
        return (float) amplitude * (0.65f * (float) Math.sin(t * DRIFT_FREQ_Y1 + 1.9f)
                + 0.35f * (float) Math.sin(t * DRIFT_FREQ_Y2 + 4.3f));
    }

    private static @Nullable ImageTextureData resolveCoverImage() {
        try {
            BackgroundData override = HudPresentationService.getInstance()
                    .getBackgroundOverrides().getTop();
            if (hasImage(override)) {
                return override.image().current;
            }
            Transitionable<BackgroundData> transition = HudPresentationService.getInstance()
                    .getBackgroundTransition();
            BackgroundData mixed = transition == null ? null : transition.getMixed();
            return hasImage(mixed) ? mixed.image().current : null;
        } catch (RuntimeException failure) {
            LOGGER.debug("While resolving the background cover image", failure);
            return null;
        }
    }

    private static boolean hasImage(@Nullable BackgroundData data) {
        return data != null && data.image() != null && data.image().current != null;
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

    public void render(SceneRenderContext context, SceneStyle style, BackgroundData backgroundData) {
        if (style == null || backgroundData == null) return;

        SceneElementRenderState hudRenderState = new SceneElementRenderState(
                SceneRenderPipelines.BACKGROUND,
                SceneTextureSetup.NONE,
                context.currentPose(),
                style,
                style,
                backgroundData,
                dynamicStatusUniform
        );

        context.submit(this, hudRenderState);
        context.nextStratum();
    }

    private record BackgroundAdjustUniform(float saturation) implements SceneUniform {
        private static final String UBO_NAME = "MHBackgroundAdjust";
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
            writer.putVec4(saturation, 0f, 0f, 0f);
        }

        @Override
        public boolean shouldUseBuffer(SceneUniform lastBuffered) {
            return false;
        }
    }
}
