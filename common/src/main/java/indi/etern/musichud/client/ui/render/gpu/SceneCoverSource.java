package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;

import java.util.Objects;

/** Equality uses {@link #identity()} only. */
public record SceneCoverSource(SceneTextureRef texture, int width, int height, Object identity) {
    @Override
    public boolean equals(Object other) {
        return other instanceof SceneCoverSource source
                && Objects.equals(identity, source.identity);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(identity);
    }
}
