package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.hud.metadata.Layout;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

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
                absolutePosition.x(), absolutePosition.y(), w, h);
    }

    public static void renderHead(GuiGraphics gr, ResourceLocation skin,
                                   float x, float y, int w, int h) {
        float scale = 0.87f;
        float inset = (1 - scale) / 2;
        gr.nextStratum();

        // Inner face layer (8,8 to 16,16) - slightly smaller for depth
        gr.pose().pushMatrix();
        gr.pose().translate(x + w * inset, y + h * inset);
        gr.pose().scale(scale);
        gr.blit(skin,
                0, 0, 8, 8,
                w, h, 8, 8,
                SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE);
        gr.pose().popMatrix();

        gr.pose().pushMatrix();
        gr.pose().translate(x, y);
        // Outer hat layer (40,8 to 48,16) - full size on top
        gr.blit(skin,
                0, 0, 40, 8,
                w, h, 8, 8,
                SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE);
        gr.pose().popMatrix();
    }
}