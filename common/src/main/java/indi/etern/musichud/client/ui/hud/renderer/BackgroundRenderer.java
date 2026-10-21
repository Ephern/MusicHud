package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.render.*;
import indi.etern.musichud.client.ui.render.pipeline.*;
import indi.etern.musichud.client.ui.render.text.*;

import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.ui.hud.metadata.DynamicStatusUniform;
import indi.etern.musichud.client.ui.hud.metadata.SceneRenderData;
import indi.etern.musichud.client.ui.render.SceneStyle;
import indi.etern.musichud.client.ui.render.pipeline.SceneRenderPipelines;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureSetup;

public class BackgroundRenderer implements RenderLayer {
    private SceneRenderData currentData;
    private final DynamicStatusUniform dynamicStatusUniform = DynamicStatusUniform.getInstance();

    public void configure(SceneRenderData data) {
        this.currentData = data;
    }

    @Override
    public void render(SceneRenderContext context) {
        if (currentData == null) return;

        dynamicStatusUniform.setTransitionable(currentData.getTransitionableBackground());
        render(context, currentData.getHudStyle(), currentData.getTransitionableBackground().getMixed());
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
