package indi.etern.musichud.client.ui.render.gpu.legacy;

import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;

/** A scene texture reference that resolves to a raw GL texture name. */
public interface LegacyTextureIdRef extends SceneTextureRef {
    int textureId();
}
