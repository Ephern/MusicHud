package indi.etern.musichud.client.ui.render.interop.vk;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.core.ColorSpaces;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.engine.ISurface;
import icyllis.arc3d.engine.ImageProxy;
import icyllis.arc3d.engine.ImageProxyView;
import icyllis.arc3d.granite.GraniteImage;
import icyllis.arc3d.vulkan.VKUtil;
import icyllis.arc3d.vulkan.VulkanBackendImage;
import icyllis.arc3d.vulkan.VulkanDevice;
import icyllis.arc3d.vulkan.VulkanImage;
import icyllis.arc3d.vulkan.VulkanImageDesc;
import icyllis.arc3d.vulkan.VulkanImageMutableState;
import icyllis.modernui.core.Core;
import icyllis.modernui.mc.VulkanModIntegration;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuTexture;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.Blaze3dBackends;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.BlazeTextureViewRef;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.SceneGpuTextureImpl;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.gl.NeoForgeValidationBridge;
import indi.etern.musichud.client.ui.render.interop.InteropCapabilities;
import indi.etern.musichud.client.ui.render.interop.InteropSource;
import indi.etern.musichud.client.ui.render.interop.InteropTarget;
import indi.etern.musichud.client.ui.render.interop.InteropUsage;
import indi.etern.musichud.client.ui.render.interop.SceneInteropBackend;
import net.vulkanmod.render.engine.VkGpuTexture;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/** VulkanMod bridge. Exported textures are only safe to sample one frame after they were written. */
@SuppressWarnings("UnstableApiUsage")
public final class VkInteropBackend implements SceneInteropBackend {
    private static final int QUEUE_FAMILY_IGNORED = ~0;
    private static final int IMAGE_TYPE_2D = 0;
    private static final int IMAGE_TILING_OPTIMAL = 0;
    private static final int SHARING_MODE_EXCLUSIVE = 0;
    private static final AtomicBoolean IMPORT_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean EXPORT_LOGGED = new AtomicBoolean();

    private VkInteropBackend() {
    }

    public static @Nullable SceneInteropBackend create() {
        if (!Blaze3dBackends.isVulkanModLoaded()
                || !Blaze3dBackends.isVulkanModDevice(RenderSystem.getDevice())) {
            return null;
        }
        try {
            Class.forName("icyllis.modernui.mc.VulkanModIntegration", false,
                    VkInteropBackend.class.getClassLoader());
            return new VkInteropBackend();
        } catch (Throwable failure) {
            MusicHud.getLogger(VkInteropBackend.class)
                    .warn("VulkanMod interop is unavailable", failure);
            return null;
        }
    }

    @Override
    public InteropCapabilities capabilities() {
        return InteropCapabilities.FULL;
    }

    @Override
    public boolean delaysExportedTextures() {
        return true;
    }

    @Override
    public boolean requiresMidFrameSubmit() {
        return false;
    }

    @Override
    public @Nullable InteropSource importImage(@Nullable icyllis.arc3d.engine.Image image,
                                               InteropUsage usage) {
        if (!(image instanceof VulkanImage vulkanImage)) {
            if (image != null) image.unref();
            return null;
        }
        GpuTexture wrapped = null;
        GpuTextureView view = null;
        try {
            wrapped = VulkanModIntegration.wrapTextureImageFromArc3D(vulkanImage);
            view = RenderSystem.getDevice().createTextureView(wrapped);
            VulkanModIntegration.syncImageLayoutFromArc3D(wrapped, vulkanImage);
            if (IMPORT_LOGGED.compareAndSet(false, true)) {
                MusicHud.getLogger(VkInteropBackend.class)
                        .info("Imported arc3d image into VulkanMod: {}x{}",
                                vulkanImage.getWidth(), vulkanImage.getHeight());
            }
            return new Source(wrapped, view, vulkanImage,
                    vulkanImage.getWidth(), vulkanImage.getHeight());
        } catch (Throwable failure) {
            if (view != null) view.close();
            if (wrapped != null) {
                VulkanModIntegration.releaseWrappedTexture(wrapped);
            }
            vulkanImage.unref();
            MusicHud.getLogger(VkInteropBackend.class)
                    .debug("While importing an arc3d Vulkan image", failure);
            return null;
        }
    }

    @Override
    public @Nullable InteropTarget exportTexture(@Nullable SceneGpuTexture texture,
                                                 InteropUsage usage) {
        if (usage != InteropUsage.SAMPLE || !(texture instanceof SceneGpuTextureImpl impl)) {
            return null;
        }
        try {
            GpuTexture gpuTexture = NeoForgeValidationBridge.unwrapValidation(impl.gpuTexture());
            if (!(gpuTexture instanceof VkGpuTexture vkTexture)) {
                return null;
            }
            net.vulkanmod.vulkan.texture.VulkanImage vkImage = vkTexture.getVulkanImage();
            if (vkImage == null || vkImage.getId() == 0L) {
                return null;
            }
            if (!(Core.requireUiRecordingContext().getDevice() instanceof VulkanDevice device)) {
                return null;
            }
            int viewFormat = VKUtil.vkFormatToImageFormat(vkImage.format);
            if (viewFormat == 0) {
                viewFormat = Engine.ImageFormat.kRGBA8;
            }
            VulkanImageDesc desc = new VulkanImageDesc(
                    0, IMAGE_TYPE_2D, vkImage.format,
                    IMAGE_TILING_OPTIMAL, vkImage.usage,
                    SHARING_MODE_EXCLUSIVE,
                    Engine.ImageType.k2D, viewFormat,
                    vkImage.width, vkImage.height, 1, 1,
                    Math.max(1, vkImage.mipLevels), 1, ISurface.FLAG_SAMPLED_IMAGE);
            VulkanImageMutableState state =
                    new VulkanImageMutableState(vkImage.getCurrentLayout(), QUEUE_FAMILY_IGNORED);
            VulkanBackendImage backendImage =
                    new VulkanBackendImage(vkImage.getId(), desc, state, null);
            VulkanImage arcImage = VulkanImage.wrap(device, backendImage);
            if (arcImage == null) {
                return null;
            }
            ImageProxy proxy = ImageProxy.wrap(arcImage);
            try {
                GraniteImage image = new GraniteImage(Core.requireUiRecordingContext(),
                        new ImageProxyView(proxy),
                        ColorInfo.CT_RGBA_8888, ColorInfo.AT_PREMUL, ColorSpaces.SRGB);
                if (EXPORT_LOGGED.compareAndSet(false, true)) {
                    MusicHud.getLogger(VkInteropBackend.class)
                            .info("Exported scene texture to arc3d: {}x{} (delayed)",
                                    impl.width(), impl.height());
                }
                return new Target(image, impl.width(), impl.height());
            } catch (Throwable failure) {
                proxy.unref();
                throw failure;
            }
        } catch (Throwable failure) {
            MusicHud.getLogger(VkInteropBackend.class)
                    .debug("While exporting a scene texture to arc3d", failure);
            return null;
        }
    }

    @Override
    public void beginRead(InteropSource source) {
        if (source instanceof Source imported) {
            VulkanModIntegration.syncImageLayoutFromArc3D(imported.texture(), imported.arcImage());
        }
    }

    @Override
    public void endRead(InteropSource source) {
        if (source instanceof Source imported) {
            VulkanModIntegration.syncImageLayoutFromVulkan(imported.texture(), imported.arcImage());
        }
    }

    private record Source(GpuTexture texture, GpuTextureView view, VulkanImage arcImage,
                          int width, int height) implements InteropSource, BlazeTextureViewRef {
        @Override
        public void close() {
            try {
                view.close();
            } catch (Throwable ignored) {
            }
            try {
                VulkanModIntegration.releaseWrappedTexture(texture);
            } catch (Throwable ignored) {
            }
            arcImage.unref();
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
