package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.hud.metadata.DynamicStatusUniform;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import indi.etern.musichud.client.ui.hud.metadata.ProgressBarData;
import indi.etern.musichud.client.ui.hud.pipelines.HudRenderPipelines;
import indi.etern.musichud.client.ui.hud.pipelines.HudRenderState;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2f;

public class ProgressRenderer implements HudRenderer {
    private static volatile ProgressRenderer instance;
    private final DynamicStatusUniform hudDynamicStatus = DynamicStatusUniform.getInstance();
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
        if (progressData == null || progressData.getLayout().height <= 0) return;

        Layout layout = progressData.getLayout();
        hudRenderContext.submitHudRenderState(
                new HudRenderState(
                        HudRenderPipelines.PROGRESS_BAR,
                        TextureSetup.noTexture(),
                        new Matrix3x2f(hudRenderContext.currentPose()),
                        layout,
                        layout,
                        progressData,
                        hudDynamicStatus
                )
        );
    }
}
