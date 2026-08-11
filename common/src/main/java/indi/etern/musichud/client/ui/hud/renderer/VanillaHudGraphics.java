package indi.etern.musichud.client.ui.hud.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3x2fStack;

/**
 * Adapter: exposes a {@link GuiGraphics} behind the neutral {@link HudGraphics}.
 */
public class VanillaHudGraphics implements HudGraphics {
    private final GuiGraphics graphics;

    public VanillaHudGraphics(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    public GuiGraphics vanilla() {
        return graphics;
    }

    @Override
    public Matrix3x2fStack pose() {
        return graphics.pose();
    }

    @Override
    public void nextStratum() {
        graphics.nextStratum();
    }

    @Override
    public void blitTextured(String texturePath, int x, int y, int u0, int v0, int width, int height, int textureWidth, int textureHeight) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, ResourceLocation.parse(texturePath), x, y, u0, v0, width, height, textureWidth, textureHeight);
    }

    @Override
    public void blitTextured(String texturePath, int x, int y, int u0, int v0, int width, int height, int u1, int v1, int textureWidth, int textureHeight, int color) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, ResourceLocation.parse(texturePath), x, y, u0, v0, width, height, u1, v1, textureWidth, textureHeight, color);
    }

    @Override
    public void drawString(Font font, String text, int x, int y, int color, boolean dropShadow) {
        graphics.drawString(font, text, x, y, color, dropShadow);
    }

    @Override
    public void fill(int fromX, int fromY, int toX, int toY, int color) {
        graphics.fill(fromX, fromY, toX, toY, color);
    }

    @Override
    public int guiWidth() {
        return graphics.guiWidth();
    }

    @Override
    public int guiHeight() {
        return graphics.guiHeight();
    }

    @Override
    public void pushScissor(int fromX, int fromY, int toX, int toY) {
        graphics.enableScissor(fromX, fromY, toX, toY);
    }

    @Override
    public void popScissor() {
        graphics.disableScissor();
    }
}
