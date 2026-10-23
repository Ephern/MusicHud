package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.ui.hud.metadata.DynamicStatusUniform;
import indi.etern.musichud.client.ui.hud.metadata.SceneRenderData;
import indi.etern.musichud.client.ui.render.RenderLayer;
import indi.etern.musichud.client.ui.render.SceneElementRenderState;
import indi.etern.musichud.client.ui.render.SceneRenderContext;
import indi.etern.musichud.client.ui.render.SceneStyle;
import indi.etern.musichud.client.ui.render.pipeline.SceneRenderPipelines;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureSetup;

public class BackgroundRenderer implements RenderLayer {
    private static final BackgroundData HUD_IDLE_BACKGROUND = BackgroundData.solid(0x202020);
    private SceneRenderData currentData;
    private final DynamicStatusUniform dynamicStatusUniform = DynamicStatusUniform.getInstance();

    public void configure(SceneRenderData data) {
        this.currentData = data;
    }

    @Override
    public void render(SceneRenderContext context) {
        render(context, false);
    }

    public void render(SceneRenderContext context, boolean idle) {
        if (currentData == null) return;

        var transition = currentData.getTransitionableBackground();
        dynamicStatusUniform.setTransitionable(transition);
        BackgroundData mixed = transition == null ? null : transition.getMixed();
        var image = mixed == null ? null : mixed.image();
        if (idle || image == null || (image.current == null && image.original == null)) {
            render(context, currentData.getHudStyle(), HUD_IDLE_BACKGROUND);
            return;
        }
        if (renderExpBackground(context, currentData.getHudStyle())) {
            context.nextStratum();
        }
    }

    public boolean renderExpBackground(SceneRenderContext context, SceneStyle style) {
        if (style == null || style.getWidth() <= 0 || style.getHeight() <= 0
                || style.getAlpha() <= 1e-4f) return false;
        context.submit(this, new SceneElementRenderState(
                SceneRenderPipelines.EXP_BACKGROUND,
                SceneTextureSetup.NONE,
                context.currentPose(),
                style,
                style
        ));
        return true;
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
