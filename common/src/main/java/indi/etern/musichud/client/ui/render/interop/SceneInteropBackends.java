package indi.etern.musichud.client.ui.render.interop;

import com.mojang.blaze3d.systems.GpuDevice;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.Blaze3dBackends;
import indi.etern.musichud.client.ui.render.interop.gl.GlInteropBackend;

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
            MusicHud.getLogger(SceneInteropBackends.class).info(
                    "Scene interop backend: {} (capabilities={}, delayedExport={})",
                    name, backend.capabilities(), backend.delaysExportedTextures());
        }
        return backend;
    }

    private static SceneInteropBackend select(GpuDevice device) {
        String requested = System.getProperty(BACKEND_PROPERTY, "auto");
        if ("none".equalsIgnoreCase(requested)) {
            return SceneInteropBackend.NOOP;
        }
        boolean vulkan = Blaze3dBackends.isVulkanModLoaded()
                && Blaze3dBackends.isVulkanModDevice(device);
        boolean openGl = Blaze3dBackends.isOpenGl(device);
        if ("gl".equalsIgnoreCase(requested)) {
            return openGl ? GlInteropBackend.INSTANCE : SceneInteropBackend.NOOP;
        }
        if ("vk".equalsIgnoreCase(requested)) {
            return vulkan ? createVulkan() : SceneInteropBackend.NOOP;
        }
        if (openGl) {
            return GlInteropBackend.INSTANCE;
        }
        if (vulkan) {
            return createVulkan();
        }
        if (Blaze3dBackends.isVulkan(device)) {
            MusicHud.getLogger(SceneInteropBackends.class).warn(
                    "Vulkan renderer without a supported interop bridge; scene effects that "
                            + "need the UI layer are disabled");
        }
        return SceneInteropBackend.NOOP;
    }

    private static SceneInteropBackend createVulkan() {
        // VkInteropBackend links against VulkanMod, so it must only be resolved when Vulkan is live.
        try {
            Class<?> type = Class.forName(
                    "indi.etern.musichud.client.ui.render.interop.vk.VkInteropBackend",
                    true, SceneInteropBackends.class.getClassLoader());
            Object backend = type.getMethod("create").invoke(null);
            return backend instanceof SceneInteropBackend result
                    ? result : SceneInteropBackend.NOOP;
        } catch (Throwable failure) {
            MusicHud.getLogger(SceneInteropBackends.class)
                    .warn("VulkanMod interop could not be created", failure);
            return SceneInteropBackend.NOOP;
        }
    }
}
