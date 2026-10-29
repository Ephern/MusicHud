package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuTexture;
import indi.etern.musichud.client.ui.render.gpu.SceneTextureFormat;

final class SceneGpuTextureImpl implements SceneGpuTexture, AutoCloseable {
    private final SceneTextureFormat format;
    private final SceneTextureFormat actualFormat;
    private final GpuTexture texture;
    private final GpuTextureView view;
    private boolean closed;

    SceneGpuTextureImpl(SceneTextureFormat format,
                        SceneTextureAllocator.SceneAllocatedTexture allocated,
                        int maxTextureSize) {
        RenderSystem.assertOnRenderThread();
        int width = allocated.texture().getWidth(0);
        int height = allocated.texture().getHeight(0);
        if (width < 1 || height < 1 || width > maxTextureSize || height > maxTextureSize) {
            allocated.view().close();
            allocated.texture().close();
            throw new IllegalArgumentException(
                    "Invalid scene target size: " + width + "x" + height);
        }
        this.format = format;
        this.actualFormat = allocated.actualFormat();
        this.texture = allocated.texture();
        this.view = allocated.view();
    }

    GpuTextureView view() {
        if (closed) throw new IllegalStateException("Scene target is closed");
        return view;
    }

    GpuTexture texture() {
        if (closed) throw new IllegalStateException("Scene target is closed");
        return texture;
    }

    void clear(int argb) {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("Scene target is closed");
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture, argb);
    }

    @Override
    public int width() {
        return texture.getWidth(0);
    }

    @Override
    public int height() {
        return texture.getHeight(0);
    }

    @Override
    public SceneTextureFormat format() {
        return format;
    }

    @Override
    public SceneTextureFormat actualFormat() {
        return actualFormat;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        try {
            view.close();
        } finally {
            texture.close();
        }
    }
}
