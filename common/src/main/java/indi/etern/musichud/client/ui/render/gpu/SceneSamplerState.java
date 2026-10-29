package indi.etern.musichud.client.ui.render.gpu;

import java.util.Objects;

public record SceneSamplerState(SceneAddressMode addressU, SceneAddressMode addressV,
                                SceneFilterMode minFilter, SceneFilterMode magFilter) {
    public SceneSamplerState {
        Objects.requireNonNull(addressU, "addressU");
        Objects.requireNonNull(addressV, "addressV");
        Objects.requireNonNull(minFilter, "minFilter");
        Objects.requireNonNull(magFilter, "magFilter");
    }

    public static SceneSamplerState linearClamp() {
        return new SceneSamplerState(SceneAddressMode.CLAMP_TO_EDGE, SceneAddressMode.CLAMP_TO_EDGE,
                SceneFilterMode.LINEAR, SceneFilterMode.LINEAR);
    }
}
