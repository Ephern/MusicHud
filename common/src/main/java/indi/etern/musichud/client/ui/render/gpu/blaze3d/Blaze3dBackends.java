package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import indi.etern.musichud.MusicHud;
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
     * The object a {@link GpuDevice} delegates to. {@code FrontendGpuDevice} keeps it private, and
     * the Vulkan backend API (command pools, image creation) only exists on that object.
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
            Field field = FrontendGpuDevice.class.getDeclaredField("backend");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException failure) {
            MusicHud.getLogger(Blaze3dBackends.class)
                    .warn("Scene interop: cannot reach the game render backend");
            return null;
        }
    }
}
