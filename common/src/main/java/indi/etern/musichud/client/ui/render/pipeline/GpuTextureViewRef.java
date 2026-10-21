package indi.etern.musichud.client.ui.render.pipeline;

import com.mojang.blaze3d.textures.GpuTextureView;

/**
 * Adapter: wraps a {@link GpuTextureView} behind the neutral {@link SceneTextureRef}.
 */
public record GpuTextureViewRef(GpuTextureView view) implements SceneTextureRef {
}
