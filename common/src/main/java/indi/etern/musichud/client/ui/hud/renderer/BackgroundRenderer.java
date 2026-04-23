package indi.etern.musichud.client.ui.hud.renderer;

import icyllis.modernui.mc.ModernUIMod;
import indi.etern.musichud.client.ui.hud.metadata.HudRenderData;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import indi.etern.musichud.client.ui.hud.metadata.ThemedColors;
import indi.etern.musichud.client.ui.hud.pipelines.HudRenderPipelines;
import indi.etern.musichud.client.ui.hud.pipelines.HudRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;

public class BackgroundRenderer implements HudRenderer {
    private static volatile BackgroundRenderer instance;
    private HudRenderData currentData;

    public static BackgroundRenderer getInstance() {
        if (instance == null) {
            synchronized (BackgroundRenderer.class) {
                if (instance == null)
                    instance = new BackgroundRenderer();
            }
        }
        return instance;
    }

    public void configure(HudRenderData data) {
        this.currentData = data;
    }

    private static final int SWATCH_SIZE = 20;
    private static final int PADDING = 4;
    private static final int START_X = 4;
    private static final int START_Y = 4;

    public void drawColorDebug(HudRenderContext renderContext, ThemedColors colors) {
        if (colors == null) return;

        int currentY = START_Y;
        drawColorDebugLine(renderContext, currentY, colors.primary, "Primary");
        currentY += SWATCH_SIZE + PADDING;
        drawColorDebugLine(renderContext, currentY, colors.secondary, "Secondary");
        currentY += SWATCH_SIZE + PADDING;
        drawColorDebugLine(renderContext, currentY, colors.bright, "Bright");
        currentY += SWATCH_SIZE + PADDING;
        drawColorDebugLine(renderContext, currentY, colors.dark, "Dark");
    }

    private static void drawColorDebugLine(HudRenderContext renderContext, int currentY, int color, String label) {
        // 绘制色块背景（黑色边框+色块）
        renderContext.fill(START_X, currentY, START_X + SWATCH_SIZE, currentY + SWATCH_SIZE, 0xFF000000); // 黑色边框背景
        renderContext.fill(START_X + 1, currentY + 1, START_X + SWATCH_SIZE - 1, currentY + SWATCH_SIZE - 1, color);

        // 绘制文字（颜色值 + 标签）
        String hex = String.format("#%06X", color & 0x00FFFFFF);
        renderContext.drawString(Minecraft.getInstance().font, label + ": " + hex,
                START_X + SWATCH_SIZE + PADDING, currentY + (SWATCH_SIZE - 8) / 2, 0xFFFFFFFF, true);
    }

    @Override
    public void render(HudRenderContext hudRenderContext) {
        if (currentData == null) {
            return;
        }
        hudRenderContext.writeUniformData("HudBackgroundParams", currentData);

        Layout layout = currentData.getLayout();
        HudRenderState hudRenderState = new HudRenderState(
                HudRenderPipelines.BACKGROUND,
                TextureSetup.noTexture(),
                hudRenderContext.currentPose(),
                layout.width, layout.height
        );

        if (ModernUIMod.isDeveloperMode()) {
            drawColorDebug(hudRenderContext, currentData.getTransitionableBackground().getCurrent().color());
        }

        hudRenderContext.submitGuiElementRenderState(hudRenderState);
        hudRenderContext.nextStratum();
    }
}