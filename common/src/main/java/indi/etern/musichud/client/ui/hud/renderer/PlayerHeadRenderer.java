package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.hud.metadata.Layout;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

public class PlayerHeadRenderer {
    private static final int SKIN_TEXTURE_SIZE = 64;
    @Getter
    private Layout layout;
    @Getter
    @Setter
    private PlayerInfo playerInfo;

    static volatile PlayerHeadRenderer instance;

    public static PlayerHeadRenderer getInstance() {
        if (instance == null) {
            synchronized (PlayerHeadRenderer.class) {
                if (instance == null)
                    instance = new PlayerHeadRenderer();
            }
        }
        return instance;
    }

    public void configureLayout(Layout layout) {
        this.layout = layout;
    }

    public void render(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        if (playerInfo == null) return;

        ResourceLocation skinLocation = playerInfo.getSkin().body().texturePath();

        Layout.AbsolutePosition absolutePosition = layout.calcAbsolutePosition(guiGraphics);
        guiGraphics.pose().pushMatrix();
        guiGraphics.nextStratum();
        guiGraphics.pose().translate(absolutePosition.x(), absolutePosition.y());
        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                skinLocation,
                0, 0,
                8, 8,
                (int) layout.width, (int) layout.height,
                8, 8,
                SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE
        );
        guiGraphics.pose().popMatrix();
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(absolutePosition.x() - layout.width * 0.08f, absolutePosition.y() - layout.height * 0.08f);
        guiGraphics.pose().scale(1.16f);
        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                skinLocation,
                0, 0,
                40, 8,
                (int) layout.width, (int) layout.height,
                8, 8,
                SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE
        );
        guiGraphics.pose().popMatrix();
    }
}