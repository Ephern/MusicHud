package indi.etern.musichud.client.ui.render.interop;

import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;

/**
 * A foreign (arc3d) image borrowed for the current frame. Closing only returns the lease; the
 * underlying texture stays owned by the producer.
 */
public interface InteropSource extends SceneTextureRef {
    int width();

    int height();

    void close();
}
