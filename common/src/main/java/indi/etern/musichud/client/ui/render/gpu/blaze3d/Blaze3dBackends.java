package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.blaze3d.systems.GpuDevice;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/** Backend detection shared by the scene renderer and the interop factories. */
public final class Blaze3dBackends {
    static final String VULKAN_DEVICE = "net.vulkanmod.render.engine.VkGpuDevice";

    private static final Field BACKEND = backendField();

    private Blaze3dBackends() {
    }

    public static boolean isOpenGl(GpuDevice device) {
        return device != null && "OpenGL".equals(device.getBackendName());
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
     * ModernUI's Vulkan bridge casts the raw device, so a validation wrapper is not supported here.
     */
    public static boolean isVulkanModDevice(GpuDevice device) {
        if (!isVulkan(device)) return false;
        if (VULKAN_DEVICE.equals(device.getClass().getName())) return true;
        Object backend = backend(device);
        return backend != null && VULKAN_DEVICE.equals(backend.getClass().getName());
    }

    /** The object a render backend implements; {@link GpuDevice} only delegates to it. */
    private static @Nullable Object backend(GpuDevice device) {
        if (device == null || BACKEND == null) return null;
        try {
            Object backend = BACKEND.get(device);
            return backend == device ? null : backend;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static @Nullable Field backendField() {
        try {
            Field field = GpuDevice.class.getDeclaredField("backend");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException ignored) {
            return null;
        }
    }
}
