package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import indi.etern.musichud.client.ui.render.gpu.SceneTextureFormat;

public interface SceneTextureAllocator {
    SceneAllocatedTexture allocate(String label, int width, int height, boolean halfFloat);

    boolean supportsHalfFloat();

    record SceneAllocatedTexture(GpuTexture texture, GpuTextureView view,
                                 SceneTextureFormat actualFormat) {
    }
}
