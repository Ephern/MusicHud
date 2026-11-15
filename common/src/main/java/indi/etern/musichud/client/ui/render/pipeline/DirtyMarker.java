package indi.etern.musichud.client.ui.render.pipeline;

/**
 * Handed to {@link SceneUniform#checkDirty} so a uniform can report that the values it wrote to the
 * previous buffer are no longer current.
 */
@FunctionalInterface
public interface DirtyMarker {
    void markDirty();
}
