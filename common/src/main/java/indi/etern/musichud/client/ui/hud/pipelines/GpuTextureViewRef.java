package indi.etern.musichud.client.ui.hud.pipelines;

/**
 * Adapter: wraps a texture id behind the neutral {@link HudTextureRef}.
 */
public record GpuTextureViewRef(int id) implements HudTextureRef {
}
