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
 * A scene texture written on the render thread and sampled on the UI thread. The wrapper is
 * non-owning, so the producer must keep the texture alive while an image is referenced; consumers
 * drop the wrapper when the producer publishes a different texture.
 */
@SuppressWarnings("UnstableApiUsage")
public final class PublishedSceneTexture {
    private static final Logger LOGGER = MusicHud.getLogger(PublishedSceneTexture.class);

    private volatile SceneGpuTexture texture;
    private SceneGpuTexture wrappedSource;
    private Image wrappedImage;
    private int consumers;

    public void publish(@Nullable SceneGpuTexture texture) {
        this.texture = texture;
    }

    public @Nullable SceneGpuTexture texture() {
        return texture;
    }

    public synchronized void registerConsumer() {
        consumers++;
    }

    public synchronized void unregisterConsumer() {
        if (consumers > 0) {
            consumers--;
        }
        if (consumers == 0) {
            release();
        }
    }

    public synchronized @Nullable Image image() {
        SceneGpuTexture current = texture;
        if (current == null) {
            return null;
        }
        if (wrappedImage != null && wrappedSource == current) {
            return wrappedImage;
        }
        int glTextureId = current.glTextureId();
        int width = current.width();
        int height = current.height();
        if (glTextureId == 0 || width < 1 || height < 1) {
            return null;
        }
        release();
        try {
            GLDevice device = (GLDevice) Core.requireUiRecordingContext().getDevice();
            GLImageDesc desc = new GLImageDesc(GL11.GL_TEXTURE_2D, GL11.GL_RGBA8,
                    Engine.ImageType.k2D, ColorInfo.CT_RGBA_8888,
                    width, height, 1, 1, 1, 1, 0);
            GLTexture glTexture = GLTexture.wrap(device,
                    new GLBackendImage(glTextureId, desc, new GLTextureMutableState()));
            if (glTexture == null) {
                return null;
            }
            ImageProxy proxy = ImageProxy.wrap(glTexture);
            wrappedImage = new GraniteImage(Core.requireUiRecordingContext(),
                    new ImageProxyView(proxy),
                    ColorInfo.CT_RGBA_8888, ColorInfo.AT_PREMUL, ColorSpaces.SRGB);
            wrappedSource = current;
            return wrappedImage;
        } catch (Throwable failure) {
            LOGGER.debug("While wrapping a scene texture for arc3d", failure);
            return null;
        }
    }

    public synchronized void release() {
        if (wrappedImage != null) {
            wrappedImage.unref();
            wrappedImage = null;
        }
        wrappedSource = null;
    }
}
