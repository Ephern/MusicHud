package indi.etern.musichud.client.ui.render.interop;

import icyllis.arc3d.sketch.Image;
import org.jetbrains.annotations.Nullable;

/**
 * One of our textures exposed to arc3d. Closing releases only the wrapper, never the texture.
 */
public interface InteropTarget extends AutoCloseable {
    int width();

    int height();

    /** Non-owning arc3d view; valid until {@link #close()}. */
    @Nullable Image arcImage();

    @Override
    void close();
}
