package indi.etern.musichud.fabric.render;

import indi.etern.musichud.client.ui.render.SceneBackend;
import indi.etern.musichud.client.ui.render.ScenePipelineRenderer;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuContext;

/** Optional Fabric backend used only when VulkanMod is loading the game with Vulkan. */
public final class VulkanModBackend implements SceneBackend {
    private final VulkanModHudRenderer hud = new VulkanModHudRenderer();
    private VulkanModSceneGpuContext gpu;

    @Override
    public SceneGpuContext gpu() {
        if (gpu == null) {
            gpu = new VulkanModSceneGpuContext();
        }
        return gpu;
    }

    @Override
    public ScenePipelineRenderer pipelineRenderer() {
        return hud;
    }

    @Override
    public void recreateGpu() {
        VulkanModSceneGpuContext previous = gpu;
        gpu = null;
        if (previous != null) {
            previous.close();
        }
    }

    @Override
    public void endFrame() {
        if (gpu != null) {
            gpu.endFrame();
        }
    }

    @Override
    public void close() {
        if (gpu != null) {
            gpu.close();
            gpu = null;
        }
        hud.close();
    }
}
