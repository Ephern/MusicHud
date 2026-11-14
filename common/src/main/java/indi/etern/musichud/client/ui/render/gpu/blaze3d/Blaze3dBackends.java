package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.systems.GpuDevice;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.gl.NeoForgeValidationBridge;

/** Backend detection shared by the scene renderer and the interop factories. */
public final class Blaze3dBackends {
    static final String VULKAN_DEVICE = "net.vulkanmod.render.engine.VkGpuDevice";

    private Blaze3dBackends() {
    }

    public static boolean isOpenGl(GpuDevice device) {
        if (device == null) return false;
        if (NeoForgeValidationBridge.unwrapDevice(device) instanceof GlDevice) {
            return true;
        }
        return "OpenGL".equals(device.getBackendName());
    }

    public static boolean isVulkan(GpuDevice device) {
        return device != null && "Vulkan".equals(device.getBackendName());
    }

    public static boolean isVulkanModLoaded() {
        try {
            Class.forName("net.vulkanmod.vulkan.Renderer", false,
                    Blaze3dBackends.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * ModernUI's Vulkan bridge casts {@link com.mojang.blaze3d.systems.RenderSystem#getDevice()}
     * directly, so validation wrappers are not supported here.
     */
    public static boolean isVulkanModDevice(GpuDevice device) {
        return isVulkan(device) && VULKAN_DEVICE.equals(device.getClass().getName());
    }
}
