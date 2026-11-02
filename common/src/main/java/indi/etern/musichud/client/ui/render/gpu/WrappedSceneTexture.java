package indi.etern.musichud.client.ui.render.gpu;

import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import icyllis.arc3d.opengl.GLTexture;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.b3d.GlTexture_Wrapped;
import org.jetbrains.annotations.Nullable;

/**
 * Blaze3D-only view over an arc3d texture, so the scene pipelines can sample it. Takes ownership of
 * the passed reference.
 */
public final class WrappedSceneTexture implements SceneGpuTexture {
    private final GlTexture_Wrapped texture;
    private final GpuTextureView view;
    private final int handle;
    private final int width;
    private final int height;

    private WrappedSceneTexture(GlTexture_Wrapped texture, GpuTextureView view,
                                int handle, int width, int height) {
        this.texture = texture;
        this.view = view;
        this.handle = handle;
        this.width = width;
        this.height = height;
    }

    public static @Nullable WrappedSceneTexture wrap(@Nullable GLTexture source) {
        if (source == null) {
            return null;
        }
        GlTexture_Wrapped texture = null;
        try {
            texture = new GlTexture_Wrapped(source);
            // The blur pyramid relies on bilinear taps; arc3d's textures are always sampled by
            // sampler objects, so this only affects the Blaze3D view.
            texture.setTextureFilter(FilterMode.LINEAR, false);
            GpuTextureView view = MuiModApi.get().getRealGpuDevice().createTextureView(texture);
            return new WrappedSceneTexture(texture, view, source.getHandle(),
                    source.getWidth(), source.getHeight());
        } catch (Throwable failure) {
            if (texture != null) {
                texture.close();
            } else {
                source.unref();
            }
            return null;
        }
    }

    public GpuTextureView view() {
        return view;
    }

    public GLTexture sourceTexture() {
        return texture.source;
    }

    public int handle() {
        return handle;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public SceneTextureFormat format() {
        return SceneTextureFormat.RGBA8;
    }

    @Override
    public SceneTextureFormat actualFormat() {
        return SceneTextureFormat.RGBA8;
    }

    @Override
    public int glTextureId() {
        return handle;
    }

    public void close() {
        view.close();
        texture.close();
    }
}
