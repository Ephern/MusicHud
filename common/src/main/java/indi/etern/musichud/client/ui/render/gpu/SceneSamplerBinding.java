package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;

import java.util.Objects;

/**
 * Binds a texture to a named sampler slot of a scene pipeline.
 * <p>
 * Filtering and address mode are properties of the bound texture (the render backend applies
 * {@code LINEAR}/{@code CLAMP_TO_EDGE} when the scene target is allocated), so no per-binding
 * sampler state exists in the version-neutral API.
 */
public record SceneSamplerBinding(String name, SceneTextureRef texture) {
    public SceneSamplerBinding {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(texture, "texture");
    }
}
