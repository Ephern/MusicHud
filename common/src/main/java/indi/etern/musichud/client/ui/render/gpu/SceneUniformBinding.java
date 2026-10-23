package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;

import java.util.Objects;

public record SceneUniformBinding(String name, SceneUniform uniform) {
    public SceneUniformBinding {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(uniform, "uniform");
    }
}
