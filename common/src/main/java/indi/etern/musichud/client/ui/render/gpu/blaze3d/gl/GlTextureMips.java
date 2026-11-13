package indi.etern.musichud.client.ui.render.gpu.blaze3d.gl;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.Blaze3dBackends;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** OpenGL-only mip chain generation for textures sampled minified. */
public final class GlTextureMips {
    private static final Logger LOGGER = MusicHud.getLogger(GlTextureMips.class);
    private static final Set<GpuTexture> PREPARED =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<GpuTexture> FAILED =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private GlTextureMips() {
    }

    /** Generates the mip chain and switches to trilinear minification. */
    public static void enableTrilinear(@Nullable AbstractTexture texture) {
        if (!Blaze3dBackends.isOpenGl(RenderSystem.getDevice())) {
            return;
        }
        GpuTexture gpu = texture == null ? null : texture.getTexture();
        GpuTexture real = gpu == null ? null : NeoForgeValidationBridge.unwrap(gpu);
        if (!(real instanceof GlTexture gl) || gl.isClosed()) {
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
