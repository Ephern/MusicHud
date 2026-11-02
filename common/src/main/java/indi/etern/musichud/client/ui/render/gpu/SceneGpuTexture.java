package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;

public interface SceneGpuTexture extends SceneTextureRef {
    int width();

    int height();

    SceneTextureFormat format();

    SceneTextureFormat actualFormat();

    /** Native OpenGL texture name, or 0 when the backend cannot expose one. */
    int glTextureId();
}
