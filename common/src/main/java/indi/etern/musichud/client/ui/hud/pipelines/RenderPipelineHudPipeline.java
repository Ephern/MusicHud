package indi.etern.musichud.client.ui.hud.pipelines;

import com.mojang.blaze3d.pipeline.RenderPipeline;

/**
 * 1.21.6-1.21.8 adapter: wraps the platform {@link RenderPipeline} behind the neutral
 * {@link HudPipeline} handle.
 */
public record RenderPipelineHudPipeline(String name, RenderPipeline renderPipeline) implements HudPipeline {
}
