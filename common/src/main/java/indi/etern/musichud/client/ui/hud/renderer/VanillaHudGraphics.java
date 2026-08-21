package indi.etern.musichud.client.ui.hud.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

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
    public PoseStack pose() {
        return graphics.pose();
    }

    @Override
    public void nextStratum() {
        // not implemented in 1.21.1
    }

    @Override
    public void blitTextured(String texturePath, int x, int y, int u0, int v0, int width, int height, int textureWidth, int textureHeight) {
        graphics.blit(ResourceLocation.parse(texturePath), x, y, width, height, (float)u0, (float)v0, textureWidth, textureHeight, textureWidth, textureHeight);
    }

    @Override
    public void blitTextured(String texturePath, int x, int y, int u0, int v0, int width, int height, int u1, int v1, int textureWidth, int textureHeight, int color) {
        float[] prevColor = RenderSystem.getShaderColor();

        applyArgbColor(graphics, color);
        graphics.blit(ResourceLocation.parse(texturePath), x, y, width, height, (float)u0, (float)v0, u1, v1, textureWidth, textureHeight);
        graphics.setColor(prevColor[0], prevColor[1], prevColor[2], prevColor[3]);
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

    private static void applyArgbColor(GuiGraphics graphics, int color) {
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        graphics.setColor(r, g, b, a);
    }
}
