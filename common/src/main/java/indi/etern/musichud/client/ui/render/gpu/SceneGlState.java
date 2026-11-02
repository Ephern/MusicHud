package indi.etern.musichud.client.ui.render.gpu;

import com.mojang.blaze3d.opengl.GlStateManager;
import icyllis.arc3d.engine.Engine;
import icyllis.modernui.core.Core;
import org.lwjgl.opengl.GL33C;

/**
 * Bridges the raw GL state between arc3d and Blaze3D. Both keep their own caches, so a backend switch
 * in the middle of a frame must invalidate the other side or it silently binds stale objects.
 */
public final class SceneGlState {
    private static final int ALL_STATE = Engine.GLBackendState.kRenderTarget
            | Engine.GLBackendState.kPixelStore
            | Engine.GLBackendState.kPipeline
            | Engine.GLBackendState.kTexture
            | Engine.GLBackendState.kStencil
            | Engine.GLBackendState.kRaster
            | Engine.GLBackendState.kBlend
            | Engine.GLBackendState.kView
            | Engine.GLBackendState.kMisc;

    private SceneGlState() {
    }

    /** Makes Blaze3D's cache match reality after arc3d has drawn. */
    public static void prepareBlaze3D() {
        for (int i = 0; i <= 3; i++) {
            GL33C.glBindSampler(i, 0);
        }
        GL33C.glDisable(GL33C.GL_STENCIL_TEST);
        GlStateManager._disableScissorTest();
        GL33C.glDisable(GL33C.GL_SCISSOR_TEST);
        GlStateManager._blendFuncSeparate(GL33C.GL_SRC_ALPHA, GL33C.GL_ONE_MINUS_SRC_ALPHA,
                GL33C.GL_ONE, GL33C.GL_ZERO);
        GL33C.glBlendFuncSeparate(GL33C.GL_SRC_ALPHA, GL33C.GL_ONE_MINUS_SRC_ALPHA,
                GL33C.GL_ONE, GL33C.GL_ZERO);
        GlStateManager._enableBlend();
        GL33C.glEnable(GL33C.GL_BLEND);
        GL33C.glBlendEquation(GL33C.GL_FUNC_ADD);
        GlStateManager._disableDepthTest();
        GL33C.glDisable(GL33C.GL_DEPTH_TEST);
        GlStateManager._depthFunc(GL33C.GL_LEQUAL);
        GL33C.glDepthFunc(GL33C.GL_LEQUAL);
        GlStateManager._depthMask(true);
        GL33C.glDepthMask(true);
        for (int i = 3; i >= 0; i--) {
            GlStateManager._activeTexture(GL33C.GL_TEXTURE0 + i);
            GlStateManager._bindTexture(0);
        }
        GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
        GlStateManager._disableCull();
    }

    /** Marks every arc3d cached state as dirty after Blaze3D has drawn. */
    public static void restoreArc3D() {
        try {
            Core.requireImmediateContext().resetContext(ALL_STATE);
        } catch (RuntimeException ignored) {
        }
    }
}
