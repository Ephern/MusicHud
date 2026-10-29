package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;

import java.util.Objects;

public record SceneSamplerBinding(String name, SceneTextureRef texture, SceneSampler sampler) {
    public SceneSamplerBinding {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(sampler, "sampler");
    }
}
