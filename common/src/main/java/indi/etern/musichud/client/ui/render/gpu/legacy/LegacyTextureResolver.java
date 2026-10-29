package indi.etern.musichud.client.ui.render.gpu.legacy;

import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;

public final class LegacyTextureResolver {
    private LegacyTextureResolver() {
    }

    public static int id(SceneTextureRef ref) {
        if (ref instanceof LegacyTextureRef legacy) return legacy.id();
        if (ref instanceof LegacySceneTexture texture) return texture.textureId();
        throw new IllegalArgumentException("Unsupported scene texture reference");
    }
}
