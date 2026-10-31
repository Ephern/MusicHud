package indi.etern.musichud.client.ui.render.gpu.blaze3d.gl;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import indi.etern.musichud.MusicHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Builds mip chains for textures that are sampled minified, so downscaled icons and covers stop
 * aliasing. Raw GL lives in this package only.
 */
public final class GlTextureSampling {
    private static final Logger LOGGER = MusicHud.getLogger(GlTextureSampling.class);
    /** Keyed by texture instance: GL ids are recycled on reload, instances are not. */
    private static final Set<GpuTexture> PREPARED =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<GpuTexture> FAILED =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final float MINIFY_THRESHOLD = 1.25f;

    private GlTextureSampling() {
    }

    public static @Nullable AbstractTexture textureOf(ResourceLocation location) {
        try {
            return Minecraft.getInstance().getTextureManager().getTexture(location);
        } catch (RuntimeException missing) {
            return null;
        }
    }

    public static void prepareBlit(ResourceLocation location, int sourceWidth, int sourceHeight,
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
        // Callers may pass the slot size instead of the full texture size, so both are needed to
        // know how many texels the sampled region actually covers.
        float texelsWide = sourceWidth * gpu.getWidth(0) / (float) Math.max(1, textureWidth);
        float texelsHigh = sourceHeight * gpu.getHeight(0) / (float) Math.max(1, textureHeight);
        float scale = Math.max(1f, pixelScale);
        if (texelsWide >= destinationWidth * scale * MINIFY_THRESHOLD
                || texelsHigh >= destinationHeight * scale * MINIFY_THRESHOLD) {
            enableTrilinear(texture);
        }
    }

    /** Generates the mip chain and switches the texture to trilinear minification, once per texture. */
    public static void enableTrilinear(@Nullable AbstractTexture texture) {
        GpuTexture gpu = texture == null ? null : texture.getTexture();
        if (!(gpu instanceof GlTexture gl) || gl.isClosed()) {
            return;
        }
        if (PREPARED.contains(gpu) || FAILED.contains(gpu)) {
            return;
        }
        int id = gl.glId();
        int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GlStateManager._bindTexture(id);
            clearGlErrors();
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            if (GL11.glGetError() != GL11.GL_NO_ERROR) {
                FAILED.add(gpu);
                LOGGER.debug("No mip chain for texture {} ({}x{})",
                        id, gl.getWidth(0), gl.getHeight(0));
                return;
            }
            int levels = 32 - Integer.numberOfLeadingZeros(
                    Math.max(1, Math.max(gl.getWidth(0), gl.getHeight(0))));
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL,
                    Math.max(0, levels - 1));
            texture.setFilter(true, true);
            gl.flushModeChanges(GL11.GL_TEXTURE_2D);
            PREPARED.add(gpu);
            LOGGER.debug("Trilinear sampling enabled for texture {} ({}x{})",
                    id, gl.getWidth(0), gl.getHeight(0));
        } catch (RuntimeException failure) {
            FAILED.add(gpu);
            LOGGER.debug("While generating a mip chain for texture " + id, failure);
        } finally {
            GlStateManager._bindTexture(previousBinding);
        }
    }

    private static void clearGlErrors() {
        //noinspection StatementWithEmptyBody
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
        }
    }
}
