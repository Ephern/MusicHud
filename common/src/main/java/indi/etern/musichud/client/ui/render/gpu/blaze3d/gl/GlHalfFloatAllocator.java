package indi.etern.musichud.client.ui.render.gpu.blaze3d.gl;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import indi.etern.musichud.client.ui.render.gpu.SceneTextureFormat;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.SceneTextureAllocator;

/** Native OpenGL allocation for RGBA16F render targets. */
public final class GlHalfFloatAllocator implements SceneTextureAllocator {
    private static final int USAGE =
            GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;

    private final GpuDevice device;
    private final boolean supported;

    public GlHalfFloatAllocator(GpuDevice device) {
        this.device = device;
        this.supported = GlSupportProbe.supportsHalfFloatColorBuffer(device);
    }

    @Override
    public SceneAllocatedTexture allocate(String label, int width, int height, boolean halfFloat) {
        SceneNativeTexture nativeTexture = SceneNativeTexture.create(USAGE, label, width, height, halfFloat);
        try {
            nativeTexture.setAddressMode(AddressMode.CLAMP_TO_EDGE);
            nativeTexture.setTextureFilter(FilterMode.LINEAR, false);
            nativeTexture.flushModeChanges(0);
            GpuTexture wrapped = NeoForgeValidationBridge.wrap(device, nativeTexture);
            return new SceneAllocatedTexture(wrapped, device.createTextureView(wrapped),
                    halfFloat ? SceneTextureFormat.RGBA16F : SceneTextureFormat.RGBA8);
        } catch (RuntimeException | Error failure) {
            nativeTexture.close();
            throw failure;
        }
    }

    @Override
    public boolean supportsHalfFloat() {
        return supported;
    }
}
