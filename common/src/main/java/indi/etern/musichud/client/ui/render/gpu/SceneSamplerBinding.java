package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;

import java.util.Objects;

/**
 * Binds a texture to a named sampler slot of a scene pipeline.
 * <p>
 * Backends without sampler objects (the texture's filter is global state) apply the mode before
 * every draw, so a texture sampled by several passes in one frame cannot leak its filter.
 */
public record SceneSamplerBinding(String name, SceneTextureRef texture, SceneSampling sampling) {
    public SceneSamplerBinding {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(sampling, "sampling");
    }

    public SceneSamplerBinding(String name, SceneTextureRef texture) {
        this(name, texture, SceneSampling.LINEAR);
    }
}
