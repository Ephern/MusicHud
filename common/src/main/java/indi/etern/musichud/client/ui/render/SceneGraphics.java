package indi.etern.musichud.client.ui.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;

public interface SceneGraphics {
    PoseStack pose();

    void nextStratum();

    void blitTextured(String texturePath, int x, int y, int u0, int v0, int width, int height, int u1, int v1, int textureWidth, int textureHeight, int color);

    void drawString(Font font, String text, int x, int y, int color, boolean dropShadow);

    void fill(int fromX, int fromY, int toX, int toY, int color);

    int guiWidth();

    int guiHeight();

    void pushScissor(int fromX, int fromY, int toX, int toY);

    void popScissor();
}
