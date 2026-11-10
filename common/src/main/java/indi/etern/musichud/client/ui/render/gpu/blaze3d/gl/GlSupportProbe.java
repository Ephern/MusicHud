package indi.etern.musichud.client.ui.render.gpu.blaze3d.gl;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

final class GlSupportProbe {
    private static Boolean halfFloatColorBuffer;

    private GlSupportProbe() {
    }

    static boolean supportsHalfFloatColorBuffer(GpuDevice device) {
        RenderSystem.assertOnRenderThread();
        if (!NeoForgeValidationBridge.isGlDevice(device)) {
            return false;
        }
        if (halfFloatColorBuffer != null) {
            return halfFloatColorBuffer;
        }
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
        } catch (RuntimeException | Error ignored) {
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            if (framebuffer != 0) {
                GL30.glDeleteFramebuffers(framebuffer);
            }
            GlStateManager._bindTexture(previousTexture);
            if (texture != 0) {
                GlStateManager._deleteTexture(texture);
            }
        }
        halfFloatColorBuffer = supported;
        return supported;
    }
}
