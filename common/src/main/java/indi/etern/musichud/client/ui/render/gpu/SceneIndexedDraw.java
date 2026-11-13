package indi.etern.musichud.client.ui.render.gpu;

import java.util.Objects;

public record SceneIndexedDraw(SceneDraw draw, SceneMesh mesh) {
    public SceneIndexedDraw {
        Objects.requireNonNull(draw, "draw");
        Objects.requireNonNull(mesh, "mesh");
    }
}
