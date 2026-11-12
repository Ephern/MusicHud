package indi.etern.musichud.fabric.render;

import indi.etern.musichud.client.ui.render.SceneBackend;
import indi.etern.musichud.client.ui.render.SceneBackendFactory;

/** Neutral factory; loaded only after {@link VulkanModBackendRegistrar} sees the mod. */
public final class VulkanModBackendFactory implements SceneBackendFactory {
    @Override
    public SceneBackend create() {
        return new VulkanModBackend();
    }
}
