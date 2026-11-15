package indi.etern.musichud.client.ui.render.pipeline;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

public interface SceneUniform {
    String getUBOName();

    int getUBOSize();

    void write(Std140Writer writer);

    /**
     * Reports that the previously uploaded buffer no longer matches the current values. Buffers are
     * reused only when nothing is marked for this uniform in the current pass, so a uniform that
     * cannot tell whether it changed marks itself always.
     */
    default void checkDirty(DirtyMarker marker) {
        marker.markDirty();
    }

    default void write(@NotNull ByteBuffer byteBuffer) {
        write(ByteBufferStd140Writer.intoBuffer(byteBuffer));
    }
}
