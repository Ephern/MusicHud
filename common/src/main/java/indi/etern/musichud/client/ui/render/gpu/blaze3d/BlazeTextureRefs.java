package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;
import net.minecraft.client.renderer.texture.DynamicTexture;

public final class BlazeTextureRefs {
    private BlazeTextureRefs() {
    }

    public static SceneTextureRef of(DynamicTexture texture) {
        return new GpuTextureViewRef(texture.getTextureView());
    }
}
