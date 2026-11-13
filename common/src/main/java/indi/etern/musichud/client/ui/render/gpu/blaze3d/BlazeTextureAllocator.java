package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
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
        GpuTexture texture = device.createTexture(label, USAGE, TextureFormat.RGBA8,
                width, height, 1, 1);
        try {
            texture.setAddressMode(AddressMode.CLAMP_TO_EDGE);
            texture.setTextureFilter(FilterMode.LINEAR, false);
            return new SceneAllocatedTexture(texture, device.createTextureView(texture),
                    SceneTextureFormat.RGBA8);
        } catch (RuntimeException | Error failure) {
            texture.close();
            throw failure;
        }
    }

    @Override
    public boolean supportsHalfFloat() {
        return false;
    }
}
