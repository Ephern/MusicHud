package indi.etern.musichud.client.ui.render;

import indi.etern.musichud.client.ui.render.gpu.SceneGpuContext;
import indi.etern.musichud.client.ui.render.gpu.SceneRetirement;
import indi.etern.musichud.client.ui.render.gpu.legacy.LegacySceneGpuContext;

/** Default OpenGL backend used on Fabric/NeoForge when no optional backend is installed. */
final class GlSceneBackend implements SceneBackend {
    private final GlScenePipelineRenderer renderer = new GlScenePipelineRenderer();
    private SceneGpuContext gpu;

    @Override
    public SceneGpuContext gpu() {
        if (gpu == null) {
            gpu = new LegacySceneGpuContext();
        }
        return gpu;
    }

    @Override
    public ScenePipelineRenderer pipelineRenderer() {
        return renderer;
    }

    @Override
    public void recreateGpu() {
        SceneGpuContext previous = gpu;
        gpu = null;
        if (previous != null) {
            // A UI recording submitted after this frame may still sample the old targets.
            SceneRetirement.retire(previous::close);
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
    }
}
