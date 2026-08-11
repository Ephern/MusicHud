package indi.etern.musichud.client.ui.hud.pipelines;

import com.mojang.blaze3d.textures.GpuTextureView;

/**
 * 1.21.6-1.21.8 adapter: wraps a {@link GpuTextureView} behind the neutral {@link HudTextureRef}.
 */
public record GpuTextureViewRef(GpuTextureView view) implements HudTextureRef {
}
