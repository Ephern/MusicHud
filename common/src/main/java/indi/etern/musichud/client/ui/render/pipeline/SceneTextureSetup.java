package indi.etern.musichud.client.ui.render.pipeline;

import org.jetbrains.annotations.Nullable;

/**
 * Version-neutral texture binding description for a HUD element.
 * <p>
 * The concrete texture objects live behind the opaque {@link SceneTextureRef} handles;
 * the version-specific render context resolves them to platform texture views.
 */
public record SceneTextureSetup(@Nullable SceneTextureRef primary, @Nullable SceneTextureRef secondary) {
    public static final SceneTextureSetup NONE = new SceneTextureSetup(null, null);

    public static SceneTextureSetup single(SceneTextureRef primary) {
        return new SceneTextureSetup(primary, null);
    }

    public static SceneTextureSetup doubleTexture(SceneTextureRef primary, SceneTextureRef secondary) {
        return new SceneTextureSetup(primary, secondary);
    }
}
