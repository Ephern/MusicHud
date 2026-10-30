package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.audio.SpectrumSnapshot;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.ui.hud.metadata.DynamicStatusUniform;
import indi.etern.musichud.client.ui.hud.metadata.SceneRenderData;
import indi.etern.musichud.client.ui.hud.presentation.BackgroundState;
import indi.etern.musichud.client.ui.hud.presentation.TrackArtwork;
import indi.etern.musichud.client.ui.hud.presentation.TrackArtworkStore;
import indi.etern.musichud.client.ui.render.AudioReactiveScale;
import indi.etern.musichud.client.ui.render.FluidBackgroundSettings;
import indi.etern.musichud.client.ui.render.RenderLayer;
import indi.etern.musichud.client.ui.render.RenderRuntime;
import indi.etern.musichud.client.ui.render.RenderStats;
import indi.etern.musichud.client.ui.render.SceneElementRenderState;
import indi.etern.musichud.client.ui.render.SceneRenderContext;
import indi.etern.musichud.client.ui.render.SceneStyle;
import indi.etern.musichud.client.ui.render.gpu.SceneBlur;
import indi.etern.musichud.client.ui.render.gpu.SceneBlurCrop;
import indi.etern.musichud.client.ui.render.gpu.SceneCapture;
import indi.etern.musichud.client.ui.render.gpu.SceneCoverLayers;
import indi.etern.musichud.client.ui.render.gpu.SceneCoverOverlay;
import indi.etern.musichud.client.ui.render.gpu.SceneCoverSource;
import indi.etern.musichud.client.ui.render.gpu.SceneCoverTransition;
import indi.etern.musichud.client.ui.render.gpu.SceneCoverWarpProcedural;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.BlazeSceneCoverSources;
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

public class BackgroundRenderer implements RenderLayer, TrackArtworkStore.Holder {
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
    private final SceneCoverLayers sceneCoverLayers = new SceneCoverLayers();
    private final SceneCoverOverlay sceneCoverOverlay = new SceneCoverOverlay();
    private SceneRenderData currentData;
    private final DynamicStatusUniform dynamicStatusUniform = DynamicStatusUniform.getInstance();
    /** Consecutive frames in which this renderer produced no background. */
    private int skippedFrames;

    public BackgroundRenderer(BackgroundTarget target) {
        this.frameResolver = new BackgroundFrameResolver(target);
    }

    public void configure(SceneRenderData data) {
        this.currentData = data;
    }

    /**
     * Releases every offscreen target of this background. Everything is re-created lazily on the
     * next rendered frame, so this only trades a one-frame rebuild for the idle memory footprint.
     */
    public void release() {
        sceneCapture.release();
        backgroundBlur.release();
        backgroundCrop.release();
        fluidBlur.release();
        sceneCoverTransition.release();
        sceneCoverWarpProcedural.reset();
        sceneCoverLayers.release();
        sceneCoverOverlay.release();
    }

    /**
     * Drops only the cover crossfade state. A renderer that stops rendering (closed screen, hidden
     * HUD) must not keep the current artwork alive, and its stale cover sources would otherwise be
     * sampled again after a long pause.
     */
    public void releaseCoverState() {
        sceneCoverTransition.release();
        skippedFrames = 0;
    }

    @Override
    public boolean holdsCover(TrackArtwork artwork) {
        return artwork != null && sceneCoverTransition.holds(artwork.background());
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

    /**
     * Builds the whole offscreen background chain. Timed here rather than in {@link #render} so the
     * GUI background, which the screen drives directly, shows up in the stats too.
     */
    public boolean renderFluidBackground(SceneRenderContext context, SceneStyle style) {
        long startNanos = System.nanoTime();
        try {
            TrackArtworkStore.getInstance().addHolder(this);
            boolean rendered = buildFluidBackground(context, style);
            if (rendered) {
                skippedFrames = 0;
            } else if (++skippedFrames >= 2) {
                // Two frames without a background mean this renderer is idle (hidden HUD, closed
                // screen); holding a cover source would block the artwork release indefinitely.
                sceneCoverTransition.release();
            }
            return rendered;
        } finally {
            RenderStats.addOffscreenNanos(System.nanoTime() - startNanos);
        }
    }

    private boolean buildFluidBackground(SceneRenderContext context, SceneStyle style) {
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
            long transitionStart = System.nanoTime();
            ResolvedCover resolvedCover = resolveCoverImages();
            if (resolvedCover != null) {
                coverFrame = sceneCoverTransition.update(
                        gpu, resolvedCover.source(), resolvedCover.changedAtMillis(),
                        coverTargetLongEdge(region), time);
                cover = coverFrame.cover();
                masterOpacity = fluidOpacityConfig * coverFrame.opacityScale();
            }
            RenderStats.phaseTransitionNanos(System.nanoTime() - transitionStart);
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
                float scale = audioReactiveScaleTimed();
                long submitStart = System.nanoTime();
                submitFluidBackground(context, style, fluid, true, 0f, scale);
                RenderStats.phaseSubmitNanos(System.nanoTime() - submitStart);
                return true;
            }
        }

        CapturePlan capturePlan = capturePlan(region, source, plan.sceneBlurRadius());
        if (capturePlan == null) return false;
        int downscale = capturePlan.downscale();
        SceneGpuTexture captured = sceneCapture.capture(gpu, source,
                capturePlan.captureRegion(), downscale);
        if (captured == null) return false;
        SceneGpuTexture background = captured;
        int sceneBlurRadius = plan.sceneBlurRadius();
        if (sceneBlurRadius > 0) {
            // Sigma is expressed in the (possibly downscaled) capture's pixel space.
            long blurAllocStart = System.nanoTime();
            SceneBlur.PendingBlur blur = backgroundBlur.prepare(gpu,
                    captured.width(), captured.height(), sceneBlurRadius / (3f * downscale));
            RenderStats.phaseBlurAllocNanos(System.nanoTime() - blurAllocStart);
            if (blur != null) {
                long blurPassStart = System.nanoTime();
                boolean applied = blur.apply(gpu, captured);
                RenderStats.phaseBlurPassNanos(System.nanoTime() - blurPassStart);
                if (applied) {
                    background = blur.target();
                }
            }
        }
        if (capturePlan.needsCrop()) {
            SceneGpuTexture cropped = backgroundCrop.apply(gpu, background,
                    capturePlan.u0(), capturePlan.v0(), capturePlan.u1(), capturePlan.v1(),
                    downscaled(capturePlan.targetWidth(), downscale),
                    downscaled(capturePlan.targetHeight(), downscale));
            if (cropped == null) return false;
            background = cropped;
        }

        SceneGpuTexture output = background;
        boolean backgroundOnly = true;
        // scaleOrGamma = 0 keeps the overlay shader on its linear tone-mapping mode; the fluid zoom
        // is applied to Sampler1 by the overlay shader through MHOverlay.z instead.
        SceneUniform adjustUniform = backgroundAdjustUniform(false, 0f, 0f);
        if (fluidWanted) {
            if (!fluidAttempted) {
                SceneGpuTexture fluid = buildFluid(gpu, cover, region, canvasWidth, canvasHeight,
                        time, layerOpacity, plan.fluidBlurRadius());
                if (fluid != null) {
                    float scale = audioReactiveScaleTimed();
                    SceneGpuTexture composite = sceneCoverOverlay.apply(
                            gpu, background, fluid, plan.masterOpacity(),
                            plan.samplingBlackOverlay(), adjustUniform,
                            scale);
                    if (composite != null) {
                        output = composite;
                        backgroundOnly = false;
                    }
                }
            }
        }

        // The audio zoom is folded into the display sampling / overlay sampling: the display must
        // never zoom the whole composite, otherwise the captured scene would move as well.
        long submitStart = System.nanoTime();
        submitFluidBackground(context, style, output, false,
                backgroundOnly ? plan.samplingBlackOverlay() : 0f, 1f);
        RenderStats.phaseSubmitNanos(System.nanoTime() - submitStart);
        return true;
    }

    /** {@link #audioReactiveScale()} with its own timing, since it takes the audio player lock. */
    private static float audioReactiveScaleTimed() {
        long start = System.nanoTime();
        try {
            return audioReactiveScale();
        } finally {
            RenderStats.phaseAudioNanos(System.nanoTime() - start);
        }
    }

    private SceneGpuTexture buildFluid(SceneGpuContext gpu, SceneGpuTexture cover,
                                       SceneCapture.Region region, int canvasWidth,
                                       int canvasHeight, float time, float layerOpacity,
                                       int fluidBlurRadius) {
        long layersStart = System.nanoTime();
        SceneCoverLayers.CoverUniform uniform = createCoverUniform(
                region, time, cover, layerOpacity);
        SceneGpuTexture layers = sceneCoverLayers.apply(gpu, cover, uniform,
                canvasWidth, canvasHeight);
        RenderStats.phaseLayersNanos(System.nanoTime() - layersStart);
        if (layers == null) return null;
        SceneGpuTexture fluid = layers;
        if (fluidBlurRadius > 0) {
            long blurStart = System.nanoTime();
            fluid = blurTexture(fluidBlur, gpu, layers, fluidBlurRadius,
                    Math.max(1, Math.max(canvasWidth, canvasHeight)));
            RenderStats.phaseBlurNanos(System.nanoTime() - blurStart);
        }
        long warpStart = System.nanoTime();
        SceneGpuTexture warped = sceneCoverWarpProcedural.apply(gpu, fluid, time);
        RenderStats.phaseWarpNanos(System.nanoTime() - warpStart);
        return warped != null ? warped : fluid;
    }

    private static SceneGpuTexture blurTexture(SceneBlur blur, SceneGpuContext gpu,
                                               SceneGpuTexture source, int radius,
                                               int canvasLong) {
        int sourceLong = Math.max(source.width(), source.height());
        float scale = sourceLong / (float) Math.max(1, canvasLong);
        float sigma = (radius / 3f) * scale;
        long allocStart = System.nanoTime();
        SceneBlur.PendingBlur pending = blur.prepare(gpu, source.width(), source.height(), sigma);
        RenderStats.phaseBlurAllocNanos(System.nanoTime() - allocStart);
        if (pending == null) return source;
        long passStart = System.nanoTime();
        boolean applied = pending.apply(gpu, source);
        RenderStats.phaseBlurPassNanos(System.nanoTime() - passStart);
        return applied ? pending.target() : source;
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
                                       float blackOverlay, float fluidScale) {
        context.submit(this, new SceneElementRenderState(
                SceneRenderPipelines.FLUID_BACKGROUND,
                SceneTextureSetup.single(texture),
                context.currentPose(),
                style,
                style,
                backgroundAdjustUniform(applyColorAdjust, blackOverlay, fluidScale)
        ));
    }

    private static BackgroundAdjustUniform backgroundAdjustUniform(boolean applyColorAdjust,
                                                                    float blackOverlay,
                                                                    float fluidScale) {
        return new BackgroundAdjustUniform(
                FluidBackgroundSettings.BACKGROUND_NATURAL_SATURATION,
                FluidBackgroundSettings.HIGHLIGHT_DARKENING,
                FluidBackgroundSettings.HIGHLIGHT_KNEE,
                FluidBackgroundSettings.HIGHLIGHT_FULL,
                FluidBackgroundSettings.BACKGROUND_OVERALL_BRIGHTNESS,
                applyColorAdjust,
                blackOverlay,
                fluidScale);
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
        return Math.clamp((int) Math.ceil(shortEdge * FluidBackgroundSettings.COVER_TARGET_LONG_EDGE_SCALE),
                FluidBackgroundSettings.COVER_TARGET_MIN_LONG_EDGE,
                FluidBackgroundSettings.COVER_TARGET_MAX_LONG_EDGE);
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
                    return new ResolvedCover(BlazeSceneCoverSources.of(override.image()),
                            backgroundState.getEffectiveBackgroundChangeTimeMillis());
                }
            }
            Transitionable<BackgroundData> transition = backgroundState.getTransition();
            BackgroundData mixed = transition.getMixed();
            long changedAt = frameResolver.usesOverrides()
                    ? backgroundState.getEffectiveBackgroundChangeTimeMillis()
                    : backgroundState.getBaseEffectiveChangeTimeMillis();
            return new ResolvedCover(hasImage(mixed)
                    ? BlazeSceneCoverSources.of(mixed.image()) : null, changedAt);
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
                targetWidth, targetHeight, needsCrop, captureDownscale(blurRadius));
    }

    /**
     * The captured backdrop is blurred with {@code blurRadius} screen pixels, so grabbing it at
     * full resolution is pure oversampling: the capture, the blur pyramid sized from it and the
     * crop target are all taken at 1/downscale. Bounded to {1, 2, 4} to keep the box filter cheap.
     */
    private static int captureDownscale(int blurRadius) {
        if (blurRadius >= 64) return 4;
        if (blurRadius >= 24) return 2;
        return 1;
    }

    private static int downscaled(int pixels, int downscale) {
        return Math.max(1, (pixels + downscale - 1) / downscale);
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

    /**
     * Overlay/second-stage background adjustment. {@code scaleOrGamma} is read as the fluid zoom by
     * the display shader ({@code fluid_background.fsh}) and as the gamma tone-mapping mode by the
     * overlay shader ({@code scene_cover_overlay.fsh}); both passes share this std140 block.
     */
    private record BackgroundAdjustUniform(float saturation, float highlightDarkening,
                                           float highlightKnee, float highlightFull,
                                           float overallBrightness, boolean applyColorAdjust,
                                           float blackOverlay, float scaleOrGamma)
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
                    blackOverlay, scaleOrGamma);
        }

        @Override
        public boolean shouldUseBuffer(SceneUniform lastBuffered) {
            return false;
        }
    }

    private record ResolvedCover(SceneCoverSource source, long changedAtMillis) {
    }

    private record CapturePlan(SceneCapture.Region captureRegion,
                               float u0, float v0, float u1, float v1,
                               int targetWidth, int targetHeight,
                               boolean needsCrop, int downscale) {
    }
}
