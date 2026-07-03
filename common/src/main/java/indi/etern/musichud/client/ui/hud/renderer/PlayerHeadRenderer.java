package indi.etern.musichud.client.ui.hud.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

public class PlayerHeadRenderer implements HudRenderer {
    private static final int SKIN_TEXTURE_SIZE = 64;
    @Getter
    private Layout layout;
    @Getter
    @Setter
    private ResourceLocation skinResource;

    public void configure(Layout layout) {
        this.layout = layout;
    }

    @Override
    public void render(HudRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (skinResource == null) return;

        Layout.AbsolutePosition absolutePosition = layout.calcAbsolutePosition(context);
        int w = (int) layout.getWidth();
        int h = (int) layout.getHeight();

        renderHead(context.getGraphics(), skinResource,
                absolutePosition.x(), absolutePosition.y(), w, h, 1);
    }

    public static void renderHead(GuiGraphics gr, ResourceLocation skin,
                                  float x, float y, int w, int h, float alpha) {
        if (alpha <= 0.003) {
            return;
        }
        float scale = 0.87f;
        float inset = (1 - scale) / 2;

        // Inner face layer (8,8 to 16,16) - slightly smaller for depth
        gr.setColor(1, 1, 1, Math.min(alpha, 1));
        PoseStack pose = gr.pose();
        pose.pushPose();
        pose.translate(x + w * inset, y + h * inset, 0);
        pose.scale(scale, scale, 1);
        gr.blit(skin,
                0, 0, w, h,
                8, 8, 8, 8,
                SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE);
        pose.popPose();

        pose.pushPose();
        pose.translate(x, y, 0);
        // Outer hat layer (40,8 to 48,16) - full size on top
        gr.blit(skin,
                0, 0, w, h,
                40, 8, 8, 8,
                SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE);
        pose.popPose();
        gr.setColor(255, 255, 255, 255);
    }
}