package indi.etern.musichud.client.ui.render.gpu.blaze3d.gl;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/** Half-float storage exposed through an RGBA8 texture descriptor. */
final class SceneNativeTexture extends GlTexture {
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
}
