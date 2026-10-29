package indi.etern.musichud.client.ui.render.gpu.legacy;

import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;
import net.minecraft.client.renderer.texture.DynamicTexture;

public final class LegacyTextureRefs {
    private LegacyTextureRefs() {
    }

    public static SceneTextureRef of(DynamicTexture texture) {
        return new LegacyTextureRef(texture.getId());
    }
}
