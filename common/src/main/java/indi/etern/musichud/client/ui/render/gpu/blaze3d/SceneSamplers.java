package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;

/**
 * Samplers for scene and UI textures. 1.21.11 removed the per-texture filter/address state, so every
 * consumer picks a sampler from the cache instead of mutating the texture at allocation.
 */
public final class SceneSamplers {
    private SceneSamplers() {
    }

    /** Bilinear, clamped, mip levels used when the texture has them. */
    public static GpuSampler linearClamp() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, true);
    }

    /** Bilinear without mip sampling, for single-level render targets. */
    public static GpuSampler linearClampSingleLevel() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, false);
    }

    /** Nearest, clamped: pixel-art text and upscaled GUI sprites. */
    public static GpuSampler nearestClamp() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }
}
