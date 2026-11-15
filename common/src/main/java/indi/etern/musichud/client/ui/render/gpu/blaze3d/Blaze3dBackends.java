package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/** Backend detection shared by the scene renderer and the interop factories. */
public final class Blaze3dBackends {
    private static final Field BACKEND = backendField();

    private Blaze3dBackends() {
    }

    public static boolean isOpenGl(GpuDevice device) {
        return device != null && "OpenGL".equals(device.getDeviceInfo().backendName());
    }

    public static boolean isVulkan(GpuDevice device) {
        return device != null && "Vulkan".equals(device.getDeviceInfo().backendName());
    }

    /**
     * The object a {@link GpuDevice} delegates to. {@code GpuDevice} keeps it private, and the
     * Vulkan backend API (command pools, image creation) only exists on that object.
     */
    public static @Nullable GpuDeviceBackend backend(GpuDevice device) {
        if (device == null || BACKEND == null) {
            return null;
        }
        try {
            return BACKEND.get(device) instanceof GpuDeviceBackend backend ? backend : null;
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
