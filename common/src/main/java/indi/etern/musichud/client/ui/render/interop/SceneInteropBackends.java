package indi.etern.musichud.client.ui.render.interop;

import com.mojang.renderpearl.api.device.GpuDevice;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.Blaze3dBackends;
import indi.etern.musichud.client.ui.render.interop.gl.GlInteropBackend;
import indi.etern.musichud.client.ui.render.interop.vk.VulkanInteropBackend;

import java.util.concurrent.atomic.AtomicBoolean;

/** Chooses the interop backend for the current render device. */
public final class SceneInteropBackends {
    private static final String BACKEND_PROPERTY = "music_hud.debug.sceneBackend";
    private static final AtomicBoolean LOGGED = new AtomicBoolean();

    private SceneInteropBackends() {
    }

    public static SceneInteropBackend create(GpuDevice device) {
        SceneInteropBackend backend = select(device);
        if (LOGGED.compareAndSet(false, true)) {
            String name = backend == SceneInteropBackend.NOOP
                    ? "none" : backend.getClass().getSimpleName();
            // Capabilities are resolved lazily (arc3d's device appears after ModernUI starts).
            MusicHud.getLogger(SceneInteropBackends.class).info(
                    "Scene interop backend: {} (delayedExport={})",
                    name, backend.delaysExportedTextures());
        }
        return backend;
    }

    private static SceneInteropBackend select(GpuDevice device) {
        String requested = System.getProperty(BACKEND_PROPERTY, "auto");
        if ("none".equalsIgnoreCase(requested)) {
            return SceneInteropBackend.NOOP;
        }
        boolean openGl = Blaze3dBackends.isOpenGl(device);
        boolean vulkan = Blaze3dBackends.isVulkan(device);
        if ("gl".equalsIgnoreCase(requested)) {
            return openGl ? GlInteropBackend.INSTANCE : SceneInteropBackend.NOOP;
        }
        if ("vk".equalsIgnoreCase(requested)) {
            return vulkan ? VulkanInteropBackend.INSTANCE : SceneInteropBackend.NOOP;
        }
        if (openGl) {
            return GlInteropBackend.INSTANCE;
        }
        if (vulkan) {
            // Capabilities are resolved lazily: arc3d's device only exists after ModernUI starts.
            return VulkanInteropBackend.INSTANCE;
        }
        MusicHud.getLogger(SceneInteropBackends.class).warn(
                "Unknown render backend {}; scene effects that need the UI layer are disabled",
                device == null ? "none" : device.getDeviceInfo().backendName());
        return SceneInteropBackend.NOOP;
    }
}
