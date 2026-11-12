package indi.etern.musichud.client.ui.render;

/**
 * Backend-owned drawing of HUD scene elements. The GL implementation uses the legacy raw-GL
 * program cache; the VulkanMod implementation resolves the same {@link
 * indi.etern.musichud.client.ui.render.pipeline.HudPipelineSpec} into a Vulkan pipeline.
 */
public interface ScenePipelineRenderer extends AutoCloseable {
    void submit(RenderLayer owner, SceneElementRenderState state);

    default void endFrame() {
    }

    @Override
    default void close() {
    }
}
