package indi.etern.musichud.client.ui.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL30;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.glGetTexParameteri;
import static org.lwjgl.opengl.GL11.glIsEnabled;
import static org.lwjgl.opengl.GL11.glTexParameteri;

public class VanillaSceneGraphics implements SceneGraphics {
    private final GuiGraphics graphics;

    public VanillaSceneGraphics(GuiGraphics graphics) {
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
    public void blitTextured(String texturePath, int x, int y, int u0, int v0, int width, int height, int u1, int v1, int textureWidth, int textureHeight, int color) {
        ResourceLocation rl = ResourceLocation.parse(texturePath);
        float[] prevColor = RenderSystem.getShaderColor().clone();

        // Only when the sampled source region is larger than the destination (i.e. downscaling)
        // does linear+mipmap sampling help; upscaled pixel-art (e.g. the 8x8 player face) must
        // stay NEAREST so it does not get blurred.
        if (u1 > width || v1 > height) {
            ensureLinearMipmap(rl);
        }

        withBlend(() -> {
            applyArgbColor(graphics, color);
            graphics.blit(
                    rl, x, y, width, height,
                    (float)u0, (float)v0, u1, v1, textureWidth, textureHeight);
        });
        graphics.setColor(prevColor[0], prevColor[1], prevColor[2], prevColor[3]);
    }

    /**
     * Makes the texture sample trilinearly (mipmapped) so downscaled blits (e.g. the 64x64 status
     * icons drawn into a ~9px slot) do not alias. Applied once and left in place: MC only uses
     * these textures here (GUI icon usage goes through ModernUI's own loader), so a restore is
     * unnecessary. A resource reload resets the filter to NEAREST and drops the mipmaps, which the
     * MIN_FILTER check below detects so they are rebuilt on the next blit.
     */
    private static void ensureLinearMipmap(ResourceLocation rl) {
        int id = Minecraft.getInstance().getTextureManager().getTexture(rl).getId();
        GlStateManager._bindTexture(id);
        if (glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER) != GL_LINEAR_MIPMAP_LINEAR) {
            GL30.glGenerateMipmap(GL_TEXTURE_2D);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        }
    }

    /**
     * Vanilla GUI blits rely on GL blend being enabled for the texture's/ColorModulator's alpha to
     * take effect (position_tex.fsh only multiplies by ColorModulator). The custom HUD pipeline
     * leaves blend disabled between draws, so restore the pre-existing state around the blit.
     */
    private static void withBlend(Runnable draw) {
        boolean blendWasEnabled = glIsEnabled(GL_BLEND);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        draw.run();
        if (!blendWasEnabled) {
            RenderSystem.disableBlend();
        }
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
