package indi.etern.musichud.client.ui.render.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import indi.etern.musichud.interfaces.ClientConfig;

final class SceneGpuTextureImpl implements SceneGpuTexture {
    private final SceneTextureFormat format;
    private final SceneTextureFormat actualFormat;
    private final SceneNativeTarget target;

    SceneGpuTextureImpl(String label, int width, int height,
                        SceneTextureFormat format, SceneSamplerState sampler) {
        RenderSystem.assertOnRenderThread();
        this.format = format;
        boolean forceRgba8 = ClientConfig.getInstance().getForceRgba8Fallback();
        boolean halfFloat = format == SceneTextureFormat.RGBA16F
                && !forceRgba8
                && SceneNativeTexture.supportsHalfFloatColorBuffer();
        SceneNativeTarget created;
        try {
            created = new SceneNativeTarget(label, width, height, halfFloat);
        } catch (RuntimeException failure) {
            if (!halfFloat) throw failure;
            created = new SceneNativeTarget(label, width, height, false);
        }
        this.target = created;
        this.actualFormat = target.halfFloat() ? SceneTextureFormat.RGBA16F : SceneTextureFormat.RGBA8;
        try {
            target.setSampler(toAddressMode(sampler.addressU()), toAddressMode(sampler.addressV()),
                    toFilterMode(sampler.minFilter()), toFilterMode(sampler.magFilter()));
        } catch (RuntimeException | Error failure) {
            target.close();
            throw failure;
        }
    }

    private static AddressMode toAddressMode(SceneAddressMode mode) {
        return mode == SceneAddressMode.CLAMP_TO_EDGE ? AddressMode.CLAMP_TO_EDGE : AddressMode.REPEAT;
    }

    private static FilterMode toFilterMode(SceneFilterMode mode) {
        return mode == SceneFilterMode.LINEAR ? FilterMode.LINEAR : FilterMode.NEAREST;
    }

    GpuTextureView view() {
        return target.view();
    }

    SceneNativeTarget target() {
        return target;
    }

    @Override
    public int width() {
        return target.width();
    }

    @Override
    public int height() {
        return target.height();
    }

    @Override
    public SceneTextureFormat format() {
        return format;
    }

    @Override
    public SceneTextureFormat actualFormat() {
        return actualFormat;
    }
}
