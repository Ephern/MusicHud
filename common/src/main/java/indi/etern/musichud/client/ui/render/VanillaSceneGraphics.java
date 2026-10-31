package indi.etern.musichud.client.ui.render;

import indi.etern.musichud.client.ui.render.gpu.blaze3d.gl.GlTextureSampling;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3x2fStack;

public class VanillaSceneGraphics implements SceneGraphics {
    private final GuiGraphics graphics;

    public VanillaSceneGraphics(GuiGraphics graphics) {
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
    public void blitTextured(String texturePath, int x, int y, int u0, int v0, int width, int height, int u1, int v1, int textureWidth, int textureHeight, int color) {
        ResourceLocation location = ResourceLocation.parse(texturePath);
        // Upscaled blits (the 8x8 player face) keep their magnification filter.
        GlTextureSampling.prepareBlit(location, u1, v1, textureWidth, textureHeight, width, height,
                guiScale());
        graphics.blit(RenderPipelines.GUI_TEXTURED, location, x, y, u0, v0, width, height, u1, v1, textureWidth, textureHeight, color);
    }

    /**
     * Physical pixels per GUI unit, so a blit's GUI-space destination can be compared with the
     * texture's texel count.
     */
    private static float guiScale() {
        Minecraft minecraft = Minecraft.getInstance();
        // Minecraft.getInstance() returns null during launch on some versions
        //noinspection ConstantValue
        if (minecraft == null) {
            return 1f;
        }
        Window window = minecraft.getWindow();
        //noinspection ConstantValue
        return window == null ? 1f : (float) window.getGuiScale();
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
