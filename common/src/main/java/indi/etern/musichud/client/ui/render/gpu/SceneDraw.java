package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.client.ui.render.pipeline.ScenePipeline;

import java.util.List;
import java.util.Objects;

public record SceneDraw(SceneGpuTexture target, ScenePipeline pipeline,
                        List<SceneSamplerBinding> samplers,
                        List<SceneUniformBinding> uniforms) {
    public SceneDraw {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(pipeline, "pipeline");
        samplers = List.copyOf(samplers);
        uniforms = List.copyOf(uniforms);
    }
}
