package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import indi.etern.musichud.client.ui.render.gpu.SceneTextureFormat;

final class BlazeTextureAllocator implements SceneTextureAllocator {
    private static final int USAGE =
            GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;

    private final GpuDevice device;

    BlazeTextureAllocator(GpuDevice device) {
        this.device = device;
    }

    @Override
    public SceneAllocatedTexture allocate(String label, int width, int height, boolean halfFloat) {
        GpuTexture texture = device.createTexture(label, USAGE,
                halfFloat ? GpuFormat.RGBA16_FLOAT : GpuFormat.RGBA8_UNORM,
                width, height, 1, 1);
        try {
            return new SceneAllocatedTexture(texture, device.createTextureView(texture),
                    halfFloat ? SceneTextureFormat.RGBA16F : SceneTextureFormat.RGBA8);
        } catch (RuntimeException | Error failure) {
            texture.close();
            throw failure;
        }
    }

    @Override
    public boolean supportsHalfFloat() {
        return true;
    }
}
