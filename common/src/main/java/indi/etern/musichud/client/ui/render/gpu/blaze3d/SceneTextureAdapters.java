package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.blaze3d.textures.GpuTextureView;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;

public final class SceneTextureAdapters {
    private SceneTextureAdapters() {
    }

    public static SceneTextureRef wrap(GpuTextureView view) {
        return view == null ? null : new GpuTextureViewRef(view);
    }

    public static GpuTextureView view(SceneTextureRef ref) {
        if (ref instanceof GpuTextureViewRef(GpuTextureView view)) return view;
        if (ref instanceof SceneGpuTextureImpl texture) return texture.view();
        throw new IllegalArgumentException("Unsupported scene texture reference");
    }
}
