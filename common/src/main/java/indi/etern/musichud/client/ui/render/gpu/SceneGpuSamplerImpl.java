package indi.etern.musichud.client.ui.render.gpu;

import java.util.Objects;

record SceneGpuSamplerImpl(SceneSamplerState state) implements SceneSampler {
    SceneGpuSamplerImpl {
        Objects.requireNonNull(state, "state");
    }
}
