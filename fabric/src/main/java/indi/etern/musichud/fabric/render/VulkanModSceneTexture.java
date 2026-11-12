package indi.etern.musichud.fabric.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuTexture;
import indi.etern.musichud.client.ui.render.gpu.SceneTextureFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/** Offscreen color target backed by VulkanMod's GL-compatible {@link TextureTarget}. */
final class VulkanModSceneTexture implements SceneGpuTexture, AutoCloseable {
    private final TextureTarget target;
    private final SceneTextureFormat requestedFormat;
    private boolean closed;

    VulkanModSceneTexture(int width, int height, SceneTextureFormat format) {
        this.target = new TextureTarget(width, height, false, false);
        this.target.setFilterMode(GL11.GL_LINEAR);
        GlStateManager._bindTexture(this.target.getColorTextureId());
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
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
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        target.destroyBuffers();
    }
}
