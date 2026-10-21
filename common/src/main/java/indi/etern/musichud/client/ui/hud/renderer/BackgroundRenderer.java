package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.render.*;
import indi.etern.musichud.client.ui.render.pipeline.*;
import indi.etern.musichud.client.ui.render.text.*;

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
    public void render(SceneRenderContext hudRenderContext) {
        if (currentData == null) return;

        SceneStyle hudStyle = currentData.getHudStyle();
        dynamicStatusUniform.setTransitionable(currentData.getTransitionableBackground());

        SceneElementRenderState hudRenderState = new SceneElementRenderState(
                SceneRenderPipelines.BACKGROUND,
                SceneTextureSetup.NONE,
                hudRenderContext.currentPose(),
                hudStyle,
                "background",
                hudStyle,
                currentData.getTransitionableBackground().getMixed(),
                dynamicStatusUniform
        );

        hudRenderContext.submit(hudRenderState);
        hudRenderContext.nextStratum();
    }
}
