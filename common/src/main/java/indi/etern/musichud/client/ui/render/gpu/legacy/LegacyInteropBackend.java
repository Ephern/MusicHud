package indi.etern.musichud.client.ui.render.gpu.legacy;

import com.mojang.blaze3d.platform.GlStateManager;
import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.core.ColorSpaces;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.ImageProxy;
import icyllis.arc3d.engine.ImageProxyView;
import icyllis.arc3d.granite.GraniteImage;
import icyllis.arc3d.opengl.GLBackendImage;
import icyllis.arc3d.opengl.GLDevice;
import icyllis.arc3d.opengl.GLImageDesc;
import icyllis.arc3d.opengl.GLTexture;
import icyllis.arc3d.opengl.GLTextureMutableState;
import icyllis.arc3d.sketch.Image;
import icyllis.modernui.core.Core;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuTexture;
import indi.etern.musichud.client.ui.render.interop.InteropCapabilities;
import indi.etern.musichud.client.ui.render.interop.InteropSource;
import indi.etern.musichud.client.ui.render.interop.InteropTarget;
import indi.etern.musichud.client.ui.render.interop.InteropUsage;
import indi.etern.musichud.client.ui.render.interop.SceneInteropBackend;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL33C;

/**
 * Legacy (1.21.1) version of the arc3d/game GL bridge. Both sides share one GL context, so a share
 * is just a texture name; the remaining work is keeping the two state caches from lying to each
 * other around our raw-GL passes.
 */
@SuppressWarnings("UnstableApiUsage")
public final class LegacyInteropBackend implements SceneInteropBackend {
    public static final LegacyInteropBackend INSTANCE = new LegacyInteropBackend();

    private static final int ALL_STATE = Engine.GLBackendState.kRenderTarget
            | Engine.GLBackendState.kPixelStore
            | Engine.GLBackendState.kPipeline
            | Engine.GLBackendState.kTexture
            | Engine.GLBackendState.kStencil
            | Engine.GLBackendState.kRaster
            | Engine.GLBackendState.kBlend
            | Engine.GLBackendState.kView
            | Engine.GLBackendState.kMisc;

    private LegacyInteropBackend() {
    }

    @Override
    public InteropCapabilities capabilities() {
        return InteropCapabilities.FULL;
    }

    @Override
    public @Nullable InteropSource importImage(@Nullable icyllis.arc3d.engine.Image image,
                                               InteropUsage usage) {
        if (!(image instanceof GLTexture texture)) {
            if (image != null) {
                image.unref();
            }
            return null;
        }
        int handle = texture.getHandle();
        if (handle == 0 || !GL11.glIsTexture(handle)) {
            texture.unref();
            return null;
        }
        // The blur pyramid relies on bilinear taps.
        try (LegacyGlStateScope ignored = LegacyGlStateScope.capture()) {
            GlStateManager._bindTexture(handle);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        }
        return new Source(texture, handle, texture.getWidth(), texture.getHeight());
    }

    @Override
    public @Nullable InteropTarget exportTexture(@Nullable SceneGpuTexture texture,
                                                 InteropUsage usage) {
        if (!(texture instanceof LegacySceneTexture legacy)) {
            return null;
        }
        int handle = legacy.textureId();
        int width = legacy.width();
        int height = legacy.height();
        if (handle == 0 || width < 1 || height < 1) {
            return null;
        }
        try {
            GLDevice device = (GLDevice) Core.requireUiRecordingContext().getDevice();
            GLImageDesc desc = new GLImageDesc(GL11.GL_TEXTURE_2D, GL11.GL_RGBA8,
                    Engine.ImageType.k2D, ColorInfo.CT_RGBA_8888,
                    width, height, 1, 1, 1, 1, 0);
            GLTexture glTexture = GLTexture.wrap(device,
                    new GLBackendImage(handle, desc, new GLTextureMutableState()));
            if (glTexture == null) {
                return null;
            }
            ImageProxy proxy = ImageProxy.wrap(glTexture);
            GraniteImage image = new GraniteImage(Core.requireUiRecordingContext(),
                    new ImageProxyView(proxy),
                    ColorInfo.CT_RGBA_8888, ColorInfo.AT_PREMUL, ColorSpaces.SRGB);
            return new Target(image, width, height);
        } catch (Throwable failure) {
            return null;
        }
    }

    /**
     * Puts the GL state where our raw-GL passes expect it. arc3d leaves its sampler objects bound
     * after a submission, and a bound sampler overrides the filter/address mode of our textures.
     */
    @Override
    public void prepareGameGraphics() {
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

    /** Invalidates arc3d's cached GL state after our passes touched it. */
    @Override
    public void restoreArc3d() {
        try {
            Core.requireImmediateContext().resetContext(ALL_STATE);
        } catch (RuntimeException ignored) {
        }
    }

    private record Source(GLTexture texture, int handle, int width, int height)
            implements InteropSource, LegacyTextureIdRef {
        @Override
        public int textureId() {
            return handle;
        }

        @Override
        public void close() {
            texture.unref();
        }
    }

    private record Target(GraniteImage image, int width, int height) implements InteropTarget {
        @Override
        public Image arcImage() {
            return image;
        }

        @Override
        public void close() {
            image.unref();
        }
    }
}
