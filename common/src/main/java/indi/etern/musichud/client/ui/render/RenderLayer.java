package indi.etern.musichud.client.ui.render;

public interface RenderLayer {
    /** Advances time-based state; called every frame, also while the layer is not drawn. */
    default void update() {
    }

    void render(SceneRenderContext context);
}
