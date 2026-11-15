package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.blaze3d.textures.GpuTextureView;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;

/**
 * A scene texture reference backed by a game texture view. Implemented by every interop source that
 * the scene pipelines can sample.
 */
public interface BlazeTextureViewRef extends SceneTextureRef {
    GpuTextureView view();
}
