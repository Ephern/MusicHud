package indi.etern.musichud.client.ui.render.pipeline;

import org.jetbrains.annotations.Nullable;

public record SceneTextureSetup(@Nullable SceneTextureRef primary, @Nullable SceneTextureRef secondary) {
    public static final SceneTextureSetup NONE = new SceneTextureSetup(null, null);

    public static SceneTextureSetup single(SceneTextureRef primary) {
        return new SceneTextureSetup(primary, null);
    }

    public static SceneTextureSetup doubleTexture(SceneTextureRef primary, SceneTextureRef secondary) {
        return new SceneTextureSetup(primary, secondary);
    }
}
