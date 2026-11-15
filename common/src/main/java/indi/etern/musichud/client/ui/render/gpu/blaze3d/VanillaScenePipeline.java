package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import indi.etern.musichud.client.ui.render.pipeline.ScenePipeline;

public record VanillaScenePipeline(String name, RenderPipeline renderPipeline) implements ScenePipeline {
}
