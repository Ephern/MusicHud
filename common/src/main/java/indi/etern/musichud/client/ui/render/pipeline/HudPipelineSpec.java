package indi.etern.musichud.client.ui.render.pipeline;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Immutable description of a HUD shader pipeline. Backends resolve it into their own program type,
 * so merely loading {@link SceneRenderPipelines} never touches a graphics API.
 */
public record HudPipelineSpec(String name,
                              ResourceLocation vertexShader,
                              ResourceLocation fragmentShader,
                              List<String> uniformBlocks) implements ScenePipeline {
    public HudPipelineSpec {
        uniformBlocks = List.copyOf(uniformBlocks);
    }
}
