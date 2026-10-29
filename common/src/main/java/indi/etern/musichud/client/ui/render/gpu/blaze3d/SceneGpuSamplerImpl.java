package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import indi.etern.musichud.client.ui.render.gpu.SceneSampler;
import indi.etern.musichud.client.ui.render.gpu.SceneSamplerState;

import java.util.Objects;

record SceneGpuSamplerImpl(SceneSamplerState state) implements SceneSampler {
    SceneGpuSamplerImpl {
        Objects.requireNonNull(state, "state");
    }
}
