package indi.etern.musichud.fabric.render;

import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.core.ColorSpaces;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.ISurface;
import icyllis.arc3d.engine.ImageProxy;
import icyllis.arc3d.engine.ImageProxyView;
import icyllis.arc3d.granite.GraniteImage;
import icyllis.arc3d.vulkan.VulkanBackendImage;
import icyllis.arc3d.vulkan.VulkanDevice;
import icyllis.arc3d.vulkan.VulkanImageDesc;
import icyllis.arc3d.vulkan.VulkanImageMutableState;
import icyllis.modernui.core.Core;
import icyllis.modernui.mc.VulkanModIntegration;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuTexture;
import indi.etern.musichud.client.ui.render.gpu.legacy.LegacyTextureIdRef;
import indi.etern.musichud.client.ui.render.interop.InteropCapabilities;
import indi.etern.musichud.client.ui.render.interop.InteropSource;
import indi.etern.musichud.client.ui.render.interop.InteropTarget;
import indi.etern.musichud.client.ui.render.interop.InteropUsage;
import indi.etern.musichud.client.ui.render.interop.SceneInteropBackend;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.vulkan.VK10;

/** Bridges VulkanMod's VkGlTexture world and arc3d's Vulkan images. */
@SuppressWarnings("UnstableApiUsage")
final class VulkanInteropBackend implements SceneInteropBackend {
    static final VulkanInteropBackend INSTANCE = new VulkanInteropBackend();

    private VulkanInteropBackend() {
    }

    @Override
    public InteropCapabilities capabilities() {
        return InteropCapabilities.FULL;
    }

    /** Splitting the UI recording breaks VulkanMod's layer submission, so resolve the blur per frame. */
    @Override
    public boolean supportsMidFrameBlur() {
        return false;
    }

    @Override
    public @Nullable InteropSource importImage(@Nullable icyllis.arc3d.engine.Image image,
                                               InteropUsage usage) {
        if (!(image instanceof icyllis.arc3d.vulkan.VulkanImage vulkan)) {
            if (image != null) {
                image.unref();
            }
            return null;
        }
        try {
            VulkanModIntegration.Layer layer = new VulkanModIntegration.Layer(vulkan);
            layer.prepareForSampling();
            int textureId = layer.getTextureId();
            return new Source(layer, new VulkanTextureRef(textureId),
                    image.getWidth(), image.getHeight());
        } catch (Throwable failure) {
            MusicHud.getLogger(VulkanInteropBackend.class)
                    .warn("While importing an arc3d image into VulkanMod", failure);
            image.unref();
            return null;
        }
    }

    @Override
    public @Nullable InteropTarget exportTexture(@Nullable SceneGpuTexture texture,
                                                 InteropUsage usage) {
        if (!(texture instanceof VulkanModSceneTexture sceneTexture)) {
            return null;
        }
        try {
            net.vulkanmod.gl.VkGlTexture glTexture =
                    net.vulkanmod.gl.VkGlTexture.getTexture(sceneTexture.textureId());
            if (glTexture == null) {
                return null;
            }
            net.vulkanmod.vulkan.texture.VulkanImage source = glTexture.getVulkanImage();
            if (source == null) {
                return null;
            }
            VulkanDevice device = (VulkanDevice) Core.requireUiRecordingContext().getDevice();
            VulkanImageDesc desc = new VulkanImageDesc(
                    0,
                    VK10.VK_IMAGE_TYPE_2D,
                    source.format,
                    VK10.VK_IMAGE_TILING_OPTIMAL,
                    source.usage,
                    VK10.VK_SHARING_MODE_EXCLUSIVE,
                    Engine.ImageType.k2D,
                    ColorInfo.CT_RGBA_8888,
                    source.width, source.height, 1, source.arrayLayers, source.mipLevels, 1,
                    ISurface.FLAG_SAMPLED_IMAGE);
            VulkanImageMutableState state = new VulkanImageMutableState(
                    VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, 0);
            VulkanBackendImage backend = new VulkanBackendImage(source.getId(), desc, state, null);
            icyllis.arc3d.vulkan.VulkanImage wrapped =
                    icyllis.arc3d.vulkan.VulkanImage.wrap(device, backend);
            if (wrapped == null) {
                return null;
            }
            ImageProxy proxy = ImageProxy.wrap(wrapped);
            GraniteImage granite = new GraniteImage(Core.requireUiRecordingContext(),
                    new ImageProxyView(proxy),
                    ColorInfo.CT_RGBA_8888, ColorInfo.AT_PREMUL, ColorSpaces.SRGB);
            return new Target(granite, sceneTexture.width(), sceneTexture.height());
        } catch (Throwable failure) {
            MusicHud.getLogger(VulkanInteropBackend.class)
                    .error("While exporting a scene texture to arc3d", failure);
            return null;
        }
    }

    @Override
    public void beginRead(InteropSource source) {
        if (source instanceof Source imported) {
            imported.layer.prepareForSampling();
        }
    }

    private record Source(VulkanModIntegration.Layer layer, VulkanTextureRef texture,
                          int width, int height) implements InteropSource, LegacyTextureIdRef {
        @Override
        public int textureId() {
            return texture.id();
        }

        @Override
        public void close() {
            layer.close();
        }
    }

    private record Target(GraniteImage image, int width, int height) implements InteropTarget {
        @Override
        public icyllis.arc3d.sketch.Image arcImage() {
            return image;
        }

        @Override
        public void close() {
            image.unref();
        }
    }
}
