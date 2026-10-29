package indi.etern.musichud.client.ui.render.gpu.legacy;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

final class LegacyGlStateScope implements AutoCloseable {
    private final int program;
    private final int activeTexture;
    private final int[] texture0 = new int[1];
    private final int[] texture1 = new int[1];
    private final int drawFramebuffer;
    private final int readFramebuffer;
    private final int[] viewport = new int[4];
    private final boolean scissor;
    private final int[] scissorBox = new int[4];
    private final boolean blend;
    private final int[] blendSrcRgb = new int[1];
    private final int[] blendDstRgb = new int[1];
    private final int[] blendSrcAlpha = new int[1];
    private final int[] blendDstAlpha = new int[1];
    private final boolean depth;
    private final boolean cull;
    private final int vertexArray;
    private final int arrayBuffer;
    private final int elementArrayBuffer;

    private LegacyGlStateScope() {
        program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        texture0[0] = textureBinding(GL13.GL_TEXTURE0);
        texture1[0] = textureBinding(GL13.GL_TEXTURE1);
        drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        GL11.glGetIntegerv(GL14.GL_BLEND_SRC_RGB, blendSrcRgb);
        GL11.glGetIntegerv(GL14.GL_BLEND_DST_RGB, blendDstRgb);
        GL11.glGetIntegerv(GL14.GL_BLEND_SRC_ALPHA, blendSrcAlpha);
        GL11.glGetIntegerv(GL14.GL_BLEND_DST_ALPHA, blendDstAlpha);
        depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        elementArrayBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
    }

    static LegacyGlStateScope capture() {
        return new LegacyGlStateScope();
    }

    private static int textureBinding(int unit) {
        GlStateManager._activeTexture(unit);
        return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    }

    @Override
    public void close() {
        GL20.glUseProgram(program);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(texture0[0]);
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        GlStateManager._bindTexture(texture1[0]);
        GlStateManager._activeTexture(activeTexture);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        if (scissor) {
            GlStateManager._enableScissorTest();
            GlStateManager._scissorBox(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        } else {
            GlStateManager._disableScissorTest();
        }
        if (blend) {
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(blendSrcRgb[0], blendDstRgb[0],
                    blendSrcAlpha[0], blendDstAlpha[0]);
        } else {
            GlStateManager._disableBlend();
        }
        if (depth) {
            GlStateManager._enableDepthTest();
        } else {
            GlStateManager._disableDepthTest();
        }
        if (cull) {
            GlStateManager._enableCull();
        } else {
            GlStateManager._disableCull();
        }
        GlStateManager._glBindVertexArray(vertexArray);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementArrayBuffer);
    }
}
