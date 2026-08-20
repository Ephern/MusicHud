package indi.etern.musichud.client.ui.hud.renderer;

import net.minecraft.client.gui.Font;
import org.joml.Matrix3x2fStack;

/**
 * Version-neutral facade over the platform GUI graphics context
 * ({@code GuiGraphics} on 1.21.x, {@code GuiGraphicsExtractor} on 26.x).
 */
public interface HudGraphics {
    Matrix3x2fStack pose();

    void nextStratum();

    void blitTextured(String texturePath, int x, int y, int u0, int v0, int width, int height, int textureWidth, int textureHeight);

    void blitTextured(String texturePath, int x, int y, int u0, int v0, int width, int height, int u1, int v1, int textureWidth, int textureHeight, int color);

    void drawString(Font font, String text, int x, int y, int color, boolean dropShadow);

    void fill(int fromX, int fromY, int toX, int toY, int color);

    int guiWidth();

    int guiHeight();

    void pushScissor(int fromX, int fromY, int toX, int toY);

    void popScissor();
}
