package indi.etern.musichud.fabric.render;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.SceneBackendRegistry;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Guarded entry point. Nothing here references VulkanMod itself, so the class is safe to load on
 * Fabric without the mod; the factory only instantiates the Vulkan backend after registration.
 */
public final class VulkanModBackendRegistrar {
    private VulkanModBackendRegistrar() {
    }

    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded("vulkanmod")) {
            return;
        }
        try {
            SceneBackendRegistry.install(new VulkanModBackendFactory());
            MusicHud.getLogger(VulkanModBackendRegistrar.class)
                    .info("VulkanMod detected; using the Vulkan scene backend");
        } catch (Throwable failure) {
            MusicHud.getLogger(VulkanModBackendRegistrar.class)
                    .error("Failed to register the VulkanMod scene backend", failure);
        }
    }
}
