package indi.etern.musichud.client.ui.render.gpu;

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
import indi.etern.musichud.MusicHud;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

/**
 * Publishes the baked GUI background from the render thread and exposes it to the UI thread as an
 * arc3d image. The bake target is reused across frames, so its wrapper stays valid until the
 * background size changes.
 */
public final class MinecraftBackgroundTexture {
    private static final Logger LOGGER = MusicHud.getLogger(MinecraftBackgroundTexture.class);

    private static volatile SceneGpuTexture texture;

    private static volatile int consumerCount;
    private static Image wrapped;
    private static SceneGpuTexture wrappedSource;

    private MinecraftBackgroundTexture() {
    }

    public static void publish(SceneGpuTexture baked) {
        texture = baked;
    }

    public static void clear() {
        texture = null;
    }

    public static synchronized void registerConsumer() {
        consumerCount++;
    }

    public static synchronized void unregisterConsumer() {
        if (consumerCount > 0) {
            consumerCount--;
        }
        if (consumerCount == 0) {
            releaseWrapped();
        }
    }

    public static synchronized @Nullable Image image() {
        SceneGpuTexture current = texture;
        if (current == null) {
            return null;
        }
        if (wrappedSource == current && wrapped != null) {
            return wrapped;
        }
        int glTextureId = current.glTextureId();
        if (glTextureId == 0) {
            return null;
        }
        try {
            GLDevice device = (GLDevice) Core.requireUiRecordingContext().getDevice();
            GLImageDesc desc = new GLImageDesc(GL11.GL_TEXTURE_2D, GL11.GL_RGBA8,
                    Engine.ImageType.k2D, ColorInfo.CT_RGBA_8888,
                    current.width(), current.height(), 1, 1, 1, 1, 0);
            GLTexture glTexture = GLTexture.wrap(device,
                    new GLBackendImage(glTextureId, desc, new GLTextureMutableState()));
            if (glTexture == null) {
                return null;
            }
            ImageProxy proxy = ImageProxy.wrap(glTexture);
            GraniteImage image = new GraniteImage(Core.requireUiRecordingContext(),
                    new ImageProxyView(proxy),
                    ColorInfo.CT_RGBA_8888, ColorInfo.AT_PREMUL, ColorSpaces.SRGB);
            releaseWrapped();
            wrapped = image;
            wrappedSource = current;
            return image;
        } catch (Throwable failure) {
            LOGGER.debug("While wrapping the baked GUI background for arc3d", failure);
            return null;
        }
    }

    private static void releaseWrapped() {
        if (wrapped != null) {
            wrapped.unref();
            wrapped = null;
        }
        wrappedSource = null;
    }
}
