package indi.etern.musichud.client.ui.render.gpu.legacy;

import com.mojang.blaze3d.pipeline.TextureTarget;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuTexture;
import indi.etern.musichud.client.ui.render.gpu.SceneTextureFormat;

final class LegacySceneTexture implements SceneGpuTexture, AutoCloseable {
    private final TextureTarget target;
    private final SceneTextureFormat requestedFormat;

    LegacySceneTexture(int width, int height, SceneTextureFormat format) {
        this.target = new TextureTarget(width, height, false, false);
        this.target.setFilterMode(org.lwjgl.opengl.GL11.GL_LINEAR);
        this.requestedFormat = format;
    }

    int textureId() {
        return target.getColorTextureId();
    }

    void bind() {
        target.bindWrite(true);
    }

    void unbind() {
        target.unbindWrite();
    }

    @Override
    public int width() {
        return target.width;
    }

    @Override
    public int height() {
        return target.height;
    }

    @Override
    public SceneTextureFormat format() {
        return requestedFormat;
    }

    @Override
    public SceneTextureFormat actualFormat() {
        return SceneTextureFormat.RGBA8;
    }

    @Override
    public void close() {
        target.destroyBuffers();
    }
}
