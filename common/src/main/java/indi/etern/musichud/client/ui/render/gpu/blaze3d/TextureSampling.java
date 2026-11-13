package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.gl.GlTextureMips;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** Texture filtering helpers shared by both render backends. */
public final class TextureSampling {
    private static final float MINIFY_THRESHOLD = 1.25f;

    private TextureSampling() {
    }

    public static @Nullable AbstractTexture textureOf(Identifier location) {
        try {
            return Minecraft.getInstance().getTextureManager().getTexture(location);
        } catch (RuntimeException missing) {
            return null;
        }
    }

    public static void prepareBlit(Identifier location, int sourceWidth, int sourceHeight,
                                   int textureWidth, int textureHeight,
                                   int destinationWidth, int destinationHeight, float pixelScale) {
        if (destinationWidth <= 0 || destinationHeight <= 0) {
            return;
        }
        AbstractTexture texture = textureOf(location);
        GpuTexture gpu = texture == null ? null : texture.getTexture();
        if (gpu == null) {
            return;
        }
        // Callers may pass the slot size, so the texel count comes from the real texture size.
        float texelsWide = sourceWidth * gpu.getWidth(0) / (float) Math.max(1, textureWidth);
        float texelsHigh = sourceHeight * gpu.getHeight(0) / (float) Math.max(1, textureHeight);
        float scale = Math.max(1f, pixelScale);
        if (texelsWide >= destinationWidth * scale * MINIFY_THRESHOLD
                || texelsHigh >= destinationHeight * scale * MINIFY_THRESHOLD) {
            if (Blaze3dBackends.isOpenGl(RenderSystem.getDevice())) {
                GlTextureMips.enableTrilinear(texture);
            }
        }
    }

    /** Kept for callers that want bilinear sampling; 1.21.11 picks the sampler at the bind site. */
    public static void ensureLinear(@Nullable AbstractTexture texture) {
    }
}
