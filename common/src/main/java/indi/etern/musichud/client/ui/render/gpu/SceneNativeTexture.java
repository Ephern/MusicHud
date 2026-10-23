package indi.etern.musichud.client.ui.render.gpu;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * OpenGL 3.2 compatible owned texture. RGBA8 is a logical descriptor only for half-float
 * targets: use rendering/sampling, never CPU transfer on these half-float textures.
 */
final class SceneNativeTexture extends GlTexture {
    private static Boolean halfFloatColorBuffer;

    private SceneNativeTexture(int usage, String label, int width, int height, int id) {
        super(usage, label, TextureFormat.RGBA8, width, height, 1, 1, id);
    }

    static SceneNativeTexture create(int usage, String label, int width, int height, boolean halfFloat) {
        int id = GlStateManager._genTexture();
        int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GlStateManager._bindTexture(id);
            int internalFormat = halfFloat ? GL30.GL_RGBA16F : GL11.GL_RGBA8;
            int type = halfFloat ? GL30.GL_HALF_FLOAT : GL11.GL_UNSIGNED_BYTE;
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat,
                    width, height, 0, GL11.GL_RGBA, type, null);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            if (GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0,
                    GL11.GL_TEXTURE_INTERNAL_FORMAT) != internalFormat) {
                throw new IllegalStateException("Scene texture storage did not retain requested precision");
            }
            return new SceneNativeTexture(usage, label, width, height, id);
        } catch (RuntimeException | Error failure) {
            GlStateManager._deleteTexture(id);
            throw failure;
        } finally {
            GlStateManager._bindTexture(previousBinding);
        }
    }

    @Override
    public void flushModeChanges(int target) {
        if (!modesDirty) return;
        int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(id);
        try {
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, toGl(addressModeU));
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, toGl(addressModeV));
            int min = minFilter == FilterMode.LINEAR ? GL11.GL_LINEAR : GL11.GL_NEAREST;
            int mag = magFilter == FilterMode.LINEAR ? GL11.GL_LINEAR : GL11.GL_NEAREST;
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
            modesDirty = false;
        } finally {
            GlStateManager._bindTexture(previousBinding);
        }
    }

    static boolean supportsHalfFloatColorBuffer() {
        if (halfFloatColorBuffer != null) return halfFloatColorBuffer;
        boolean supported = false;
        int texture = 0;
        int framebuffer = 0;
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            GlStateManager.clearGlErrors();
            texture = GlStateManager._genTexture();
            GlStateManager._bindTexture(texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, 1, 1, 0,
                    GL11.GL_RGBA, GL30.GL_HALF_FLOAT, (ByteBuffer) null);
            if (GL11.glGetError() == GL11.GL_NO_ERROR) {
                framebuffer = GL30.glGenFramebuffers();
                GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                        GL11.GL_TEXTURE_2D, texture, 0);
                supported = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
                        == GL30.GL_FRAMEBUFFER_COMPLETE;
            }
        } catch (RuntimeException | Error failure) {
            supported = false;
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
            GlStateManager._bindTexture(previousTexture);
            if (texture != 0) GlStateManager._deleteTexture(texture);
        }
        halfFloatColorBuffer = supported;
        return supported;
    }

    private static int toGl(AddressMode mode) {
        return mode == AddressMode.REPEAT ? GL11.GL_REPEAT : GL13.GL_CLAMP_TO_EDGE;
    }

    @Override
    public void setUseMipmaps(boolean enabled) {
        if (enabled) throw new UnsupportedOperationException("Scene targets have one mip level");
        super.setUseMipmaps(false);
    }
}
