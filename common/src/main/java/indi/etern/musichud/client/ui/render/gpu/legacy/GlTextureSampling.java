package indi.etern.musichud.client.ui.render.gpu.legacy;

import com.mojang.blaze3d.platform.GlStateManager;
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

/** Mip chains for textures sampled minified. */
public final class GlTextureSampling {
    private static final Logger LOGGER = MusicHud.getLogger(GlTextureSampling.class);
    private static final Set<AbstractTexture> LINEAR = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<AbstractTexture> PREPARED = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<AbstractTexture> FAILED = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    private static final float MINIFY_THRESHOLD = 1.25f;

    private GlTextureSampling() {
    }

    public static void prepareBlit(ResourceLocation location, int sourceWidth, int sourceHeight,
                                   int textureWidth, int textureHeight,
                                   int destinationWidth, int destinationHeight, float pixelScale) {
        if (destinationWidth <= 0 || destinationHeight <= 0) {
            return;
        }
        AbstractTexture texture = textureOf(location);
        if (texture == null) {
            return;
        }
        int[] size = level0Size(texture);
        if (size == null) {
            return;
        }
        // Callers may pass the slot size, so the texel count comes from the real texture size.
        float texelsWide = sourceWidth * size[0] / (float) Math.max(1, textureWidth);
        float texelsHigh = sourceHeight * size[1] / (float) Math.max(1, textureHeight);
        float scale = Math.max(1f, pixelScale);
        if (texelsWide >= destinationWidth * scale * MINIFY_THRESHOLD
                || texelsHigh >= destinationHeight * scale * MINIFY_THRESHOLD) {
            enableTrilinear(texture, size[0], size[1]);
        }
    }

    /** Switches the texture to bilinear filtering with clamped addressing. */
    public static void ensureLinear(@Nullable AbstractTexture texture) {
        if (texture == null || !LINEAR.add(texture)) {
            return;
        }
        withBound(texture, () -> {
            if (GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER)
                    != GL11.GL_LINEAR) {
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
                        GL11.GL_LINEAR);
            }
            if (GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER)
                    != GL11.GL_LINEAR
                    && GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER)
                    != GL11.GL_LINEAR_MIPMAP_LINEAR) {
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
                        GL11.GL_LINEAR);
            }
            // TextureUtil uploads with GL_REPEAT, which bleeds opposite edges into the cover.
            if (GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S)
                    != GL12.GL_CLAMP_TO_EDGE) {
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S,
                        GL12.GL_CLAMP_TO_EDGE);
            }
            if (GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T)
                    != GL12.GL_CLAMP_TO_EDGE) {
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T,
                        GL12.GL_CLAMP_TO_EDGE);
            }
        });
    }

    /** Generates the mip chain and switches to trilinear minification. */
    private static void enableTrilinear(AbstractTexture texture, int texWidth, int texHeight) {
        if (PREPARED.contains(texture) || FAILED.contains(texture)) {
            return;
        }
        int levels = 31 - Integer.numberOfLeadingZeros(Math.max(1, Math.max(texWidth, texHeight)));
        withBound(texture, () -> {
            int minFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            if (minFilter == GL11.GL_LINEAR_MIPMAP_LINEAR
                    && GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL)
                    >= levels) {
                PREPARED.add(texture);
                return;
            }
            // TextureUtil pins MAX_LEVEL/MAX_LOD to 0, which would make an incomplete texture.
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, levels);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD,
                    (float) levels);
            //noinspection StatementWithEmptyBody
            while (GL11.glGetError() != GL11.GL_NO_ERROR) {
                // Drain stale errors so the generateMipmap result can be checked.
            }
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            if (GL11.glGetError() != GL11.GL_NO_ERROR) {
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, 0f);
                FAILED.add(texture);
                LOGGER.debug("No mip chain for texture {} ({}x{})", texture.getId(),
                        texWidth, texHeight);
                return;
            }
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
                    GL11.GL_LINEAR_MIPMAP_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
                    GL11.GL_LINEAR);
            PREPARED.add(texture);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Trilinear sampling enabled for texture {} ({}x{}, {} levels)",
                        texture.getId(), texWidth, texHeight, levels + 1);
            }
        });
    }

    private static @Nullable AbstractTexture textureOf(ResourceLocation location) {
        try {
            return Minecraft.getInstance().getTextureManager().getTexture(location);
        } catch (RuntimeException missing) {
            return null;
        }
    }

    /** @return level 0 size, or null when the texture has no storage yet. */
    private static int @Nullable [] level0Size(AbstractTexture texture) {
        int[] size = new int[2];
        boolean[] found = new boolean[1];
        withBound(texture, () -> {
            size[0] = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            size[1] = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            found[0] = size[0] > 0 && size[1] > 0;
        });
        return found[0] ? size : null;
    }

    private static void withBound(AbstractTexture texture, Runnable action) {
        int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(texture.getId());
        try {
            action.run();
        } finally {
            GlStateManager._bindTexture(previousBinding);
        }
    }
}
