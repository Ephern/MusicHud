package indi.etern.musichud.client.ui.render;

import indi.etern.musichud.client.ui.render.gpu.SceneGpuContext;

/** Bundles the two graphics backends the HUD/scene layer needs from a platform. */
public interface SceneBackend extends AutoCloseable {
    SceneGpuContext gpu();

    ScenePipelineRenderer pipelineRenderer();

    /** Drops cached GPU resources; the next {@link #gpu()} / {@link #pipelineRenderer()} rebuilds. */
    default void recreateGpu() {
    }

    default void endFrame() {
    }

    @Override
    void close();
}
