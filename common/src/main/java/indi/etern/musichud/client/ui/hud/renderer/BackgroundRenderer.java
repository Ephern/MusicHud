package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.ui.hud.metadata.DynamicStatusUniform;
import indi.etern.musichud.client.ui.hud.metadata.SceneRenderData;
import indi.etern.musichud.client.ui.render.RenderLayer;
import indi.etern.musichud.client.ui.render.RenderService;
import indi.etern.musichud.client.ui.render.SceneElementRenderState;
import indi.etern.musichud.client.ui.render.SceneRenderContext;
import indi.etern.musichud.client.ui.render.SceneStyle;
import indi.etern.musichud.client.ui.render.gpu.SceneBlur;
import indi.etern.musichud.client.ui.render.gpu.SceneCapture;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuContext;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuTexture;
import indi.etern.musichud.client.ui.render.pipeline.SceneRenderPipelines;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureSetup;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

public class BackgroundRenderer implements RenderLayer {
    private static final BackgroundData HUD_IDLE_BACKGROUND = BackgroundData.solid(0x202020);
    private static final Logger LOGGER = MusicHud.getLogger(BackgroundRenderer.class);
    private final SceneCapture sceneCapture = new SceneCapture();
    private final SceneBlur sceneBlur = new SceneBlur();
    private SceneRenderData currentData;
    private final DynamicStatusUniform dynamicStatusUniform = DynamicStatusUniform.getInstance();

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

        SceneGpuTexture captured = sceneCapture.capture(gpu, source, region);
        if (captured == null) return false;
        SceneGpuTexture output = captured;
        if (blurRadius > 0) {
            SceneBlur.PendingBlur blur = sceneBlur.prepare(gpu,
                    captured.width(), captured.height(), blurRadius / 3f);
            if (blur != null && blur.apply(gpu, captured)) {
                output = blur.target();
            }
        }

        context.submit(this, new SceneElementRenderState(
                SceneRenderPipelines.EXP_BACKGROUND,
                SceneTextureSetup.single(output),
                context.currentPose(),
                style,
                style
        ));
        return true;
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
}
