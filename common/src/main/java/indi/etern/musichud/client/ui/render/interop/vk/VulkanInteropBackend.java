package indi.etern.musichud.client.ui.render.interop.vk;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTexture;
import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.core.ColorSpaces;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.ImageProxy;
import icyllis.arc3d.engine.ImageProxyView;
import icyllis.arc3d.granite.GraniteImage;
import icyllis.arc3d.sketch.Image;
import icyllis.arc3d.vulkan.VKUtil;
import icyllis.arc3d.vulkan.VulkanBackendImage;
import icyllis.arc3d.vulkan.VulkanDevice;
import icyllis.arc3d.vulkan.VulkanImage;
import icyllis.arc3d.vulkan.VulkanImageDesc;
import icyllis.arc3d.vulkan.VulkanImageMutableState;
import icyllis.modernui.core.Core;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuTexture;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.Blaze3dBackends;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.SceneGpuTextureImpl;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.BlazeTextureViewRef;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.gl.NeoForgeValidationBridge;
import indi.etern.musichud.client.ui.render.interop.InteropCapabilities;
import indi.etern.musichud.client.ui.render.interop.InteropSource;
import indi.etern.musichud.client.ui.render.interop.InteropTarget;
import indi.etern.musichud.client.ui.render.interop.InteropUsage;
import indi.etern.musichud.client.ui.render.interop.SceneInteropBackend;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.vulkan.VK10;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vulkan bridge between arc3d and the game's render device. Both APIs use the same {@code VkDevice},
 * so a texture is shared by wrapping its {@code VkImage}; blaze3d keeps every texture in
 * {@code VK_IMAGE_LAYOUT_GENERAL}, which is what the wrapper advertises to arc3d.
 * <p>
 * Only export is supported: blaze3d's {@code VulkanGpuTexture} allocates its image through VMA and
 * has no wrap constructor, so an arc3d image cannot become a game texture.
 */
@SuppressWarnings("UnstableApiUsage")
public final class VulkanInteropBackend implements SceneInteropBackend {
    private static final Logger LOGGER = MusicHud.getLogger(VulkanInteropBackend.class);
    private static final InteropCapabilities EXPORT_ONLY = new InteropCapabilities(false, true);

    private static final int IMAGE_USAGE = VK10.VK_IMAGE_USAGE_SAMPLED_BIT
            | VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
    // blaze3d transitions every texture to VK_IMAGE_LAYOUT_GENERAL once at creation and never
    // leaves it, so that is the layout the wrapper has to advertise.
    private static final int IMAGE_LAYOUT = VK10.VK_IMAGE_LAYOUT_GENERAL;

    public static final VulkanInteropBackend INSTANCE = new VulkanInteropBackend();

    private final AtomicBoolean contextFailureLogged = new AtomicBoolean();
    private final AtomicBoolean castFailureLogged = new AtomicBoolean();
    private final AtomicBoolean importFailureLogged = new AtomicBoolean();

    private VulkanInteropBackend() {
    }

    /**
     * This backend is only selected for Vulkan devices. The check must not touch arc3d here: this is
     * called from the render thread, and arc3d's recording context only exists on the UI thread.
     */
    @Override
    public InteropCapabilities capabilities() {
        return VulkanTextureAlias.isSupported() ? InteropCapabilities.FULL : EXPORT_ONLY;
    }

    @Override
    public @Nullable InteropSource importImage(@Nullable icyllis.arc3d.engine.Image image,
                                               InteropUsage usage) {
        if (!(image instanceof VulkanImage vulkanImage) || !VulkanTextureAlias.isSupported()) {
            if (image != null) {
                image.unref();
            }
            return null;
        }
        long handle = vulkanImage.vkImage();
        VulkanImageDesc desc = vulkanImage.getVulkanDesc();
        int width = desc.getWidth();
        int height = desc.getHeight();
        if (handle == VK10.VK_NULL_HANDLE || width < 1 || height < 1) {
            image.unref();
            return null;
        }
        try {
            GpuDevice blazeDevice = NeoForgeValidationBridge.unwrapDevice(RenderSystem.getDevice());
            if (!(Blaze3dBackends.backend(blazeDevice)
                    instanceof com.mojang.renderpearl.backend.vulkan.VulkanDevice device)) {
                image.unref();
                return null;
            }
            GpuTexture alias = VulkanTextureAlias.create(device, handle, width, height,
                    GpuFormat.RGBA8_UNORM);
            if (alias == null) {
                image.unref();
                return null;
            }
            GpuTextureView view = device.createTextureView(alias, 0, 1);
            // arc3d leaves a rendered target as a color attachment; blaze3d samples without
            // barriers, so the layout has to be fixed before that pass runs.
            int layout = vulkanImage.getVulkanMutableState().getImageLayout();
            if (layout != VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                VulkanImageBarrier.transitionToSampled(device, handle, layout);
                vulkanImage.getVulkanMutableState()
                        .setImageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            }
            return new Source(view, alias, vulkanImage, width, height);
        } catch (Throwable failure) {
            if (importFailureLogged.compareAndSet(false, true)) {
                LOGGER.warn("Scene interop: cannot sample an arc3d texture", failure);
            }
            image.unref();
            return null;
        }
    }

    @Override
    public boolean delaysExportedTextures() {
        return true;
    }

    @Override
    public @Nullable InteropTarget exportTexture(@Nullable SceneGpuTexture texture, InteropUsage usage) {
        if (!(texture instanceof SceneGpuTextureImpl impl)) {
            return null;
        }
        if (!(NeoForgeValidationBridge.unwrap(impl.gpuTexture()) instanceof VulkanGpuTexture gpuTexture)) {
            if (castFailureLogged.compareAndSet(false, true)) {
                LOGGER.warn("Scene interop: texture is not a Vulkan texture ({})",
                        impl.gpuTexture().getClass().getName());
            }
            return null;
        }
        long image = gpuTexture.vkImage();
        int width = impl.width();
        int height = impl.height();
        if (image == VK10.VK_NULL_HANDLE || width < 1 || height < 1) {
            return null;
        }
        // Nothing that arc3d samples in this path (background bake, panel backdrop) is 16-bit.
        if (gpuTexture.getFormat() != GpuFormat.RGBA8_UNORM) {
            return null;
        }
        try {
            if (!(Core.requireUiRecordingContext().getDevice() instanceof VulkanDevice device)) {
                if (castFailureLogged.compareAndSet(false, true)) {
                    LOGGER.warn("Scene interop: arc3d is not running on Vulkan ({}); textures stay "
                                    + "on the game side",
                            Core.requireUiRecordingContext().getDevice().getClass().getName());
                }
                return null;
            }
            int vkFormat = VulkanConst.toVk(gpuTexture.getFormat());
            VulkanImageDesc desc = new VulkanImageDesc(
                    0,
                    VK10.VK_IMAGE_TYPE_2D,
                    vkFormat,
                    VK10.VK_IMAGE_TILING_OPTIMAL,
                    IMAGE_USAGE,
                    VK10.VK_SHARING_MODE_EXCLUSIVE,
                    Engine.ImageType.k2D,
                    VKUtil.vkFormatToImageFormat(vkFormat),
                    width, height, 1, 1, 1, 1, 0);
            // VK_QUEUE_FAMILY_IGNORED: the game and arc3d record on the same queue.
            VulkanImageMutableState state = new VulkanImageMutableState(IMAGE_LAYOUT, -1);
            VulkanImage vulkanImage = VulkanImage.wrap(device,
                    new VulkanBackendImage(image, desc, state, null));
            if (vulkanImage == null) {
                return null;
            }
            ImageProxy proxy = ImageProxy.wrap(vulkanImage);
            GraniteImage arcImage = new GraniteImage(Core.requireUiRecordingContext(),
                    new ImageProxyView(proxy),
                    ColorInfo.CT_RGBA_8888, ColorInfo.AT_PREMUL, ColorSpaces.SRGB);
            return new Target(arcImage, width, height);
        } catch (Throwable failure) {
            if (contextFailureLogged.compareAndSet(false, true)) {
                LOGGER.warn("Scene interop: cannot reach arc3d's Vulkan device", failure);
            }
            return null;
        }
    }

    private record Source(GpuTextureView view, GpuTexture texture, VulkanImage image,
                          int width, int height) implements InteropSource, BlazeTextureViewRef {
        @Override
        public void close() {
            view.close();
            texture.close();
            image.unref();
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
