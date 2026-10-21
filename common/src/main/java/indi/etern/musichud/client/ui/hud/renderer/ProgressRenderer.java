package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.render.*;
import indi.etern.musichud.client.ui.render.pipeline.*;
import indi.etern.musichud.client.ui.render.text.*;

import indi.etern.musichud.client.ui.hud.metadata.DynamicStatusUniform;
import indi.etern.musichud.client.ui.render.SceneStyle;
import indi.etern.musichud.client.ui.hud.metadata.ProgressBarData;
import indi.etern.musichud.client.ui.render.pipeline.SceneRenderPipelines;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureSetup;
import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix3x2f;

public class ProgressRenderer implements RenderLayer {
    private final DynamicStatusUniform hudDynamicStatus = DynamicStatusUniform.getInstance();
    @Setter
    @Getter
    private ProgressBarData progressData;

    public void configure(ProgressBarData data) {
        this.progressData = data;
    }

    @Override
    public void render(SceneRenderContext hudRenderContext) {
        if (progressData == null || progressData.getHudStyle().getHeight() <= 0) return;

        SceneStyle hudStyle = progressData.getHudStyle();
        hudRenderContext.submit(
                new SceneElementRenderState(
                        SceneRenderPipelines.PROGRESS_BAR,
                        SceneTextureSetup.NONE,
                        new Matrix3x2f(hudRenderContext.currentPose()),
                        hudStyle,
                        "progress",
                        hudStyle,
                        progressData,
                        hudDynamicStatus
                )
        );
    }
}
