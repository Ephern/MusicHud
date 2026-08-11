package indi.etern.musichud.client.ui.hud.pipelines;

import org.jetbrains.annotations.Nullable;

/**
 * Version-neutral texture binding description for a HUD element.
 * <p>
 * The concrete texture objects live behind the opaque {@link HudTextureRef} handles;
 * the version-specific render context resolves them to platform texture views.
 */
public record HudTextureSetup(@Nullable HudTextureRef primary, @Nullable HudTextureRef secondary) {
    public static final HudTextureSetup NONE = new HudTextureSetup(null, null);

    public static HudTextureSetup single(HudTextureRef primary) {
        return new HudTextureSetup(primary, null);
    }

    public static HudTextureSetup doubleTexture(HudTextureRef primary, HudTextureRef secondary) {
        return new HudTextureSetup(primary, secondary);
    }
}
