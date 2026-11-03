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

/**
 * Legacy (1.21.1) version of the arc3d/game GL bridge. Both sides share one GL context, so a share
 * is just a texture name; the only work is tagging the name for our pipelines and re-wrapping it
 * for arc3d.
 */
@SuppressWarnings("UnstableApiUsage")
public final class LegacyInteropBackend implements SceneInteropBackend {
    public static final LegacyInteropBackend INSTANCE = new LegacyInteropBackend();

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
