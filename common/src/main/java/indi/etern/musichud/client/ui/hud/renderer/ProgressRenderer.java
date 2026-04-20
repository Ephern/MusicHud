package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.hud.metadata.Layout;
import indi.etern.musichud.client.ui.hud.metadata.ProgressBarData;
import indi.etern.musichud.client.ui.hud.piplines.HudRenderPipelines;
import indi.etern.musichud.client.ui.hud.piplines.ProgressBarRenderState;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2f;

public class ProgressRenderer implements HudRenderer {
    private static volatile ProgressRenderer instance;
    @Setter
    @Getter
    private ProgressBarData progressData;

    public static ProgressRenderer getInstance() {
        if (instance == null) {
            synchronized (ProgressRenderer.class) {
                if (instance == null)
                    instance = new ProgressRenderer();
            }
        }
        return instance;
    }

    public void configure(ProgressBarData data) {
        this.progressData = data;
    }

    @Override
    public void render(HudRenderContext hudRenderContext) {
        if (progressData == null || progressData.getLayout().height <= 0) {
            return;
        }

        hudRenderContext.writeUniformData("HudProgressParams", progressData);

        Layout layout = progressData.getLayout();
        hudRenderContext.submitGuiElementRenderState(
                new ProgressBarRenderState(
                        HudRenderPipelines.PROGRESS_BAR,
                        TextureSetup.noTexture(),
                        new Matrix3x2f(hudRenderContext.currentPose()),
                        layout.width, layout.height,
                        progressData.fillColorLeft, progressData.fillColorRight, progressData.backgroundColor
                )
        );
    }
}
