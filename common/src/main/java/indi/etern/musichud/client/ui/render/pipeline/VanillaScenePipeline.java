package indi.etern.musichud.client.ui.render.pipeline;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;

/**
 * Adapter: wraps the platform {@link RenderPipeline} behind the neutral
 * {@link ScenePipeline} handle.
 */
public record VanillaScenePipeline(String name, RenderPipeline renderPipeline) implements ScenePipeline {
}
