package indi.etern.musichud.client.ui.render.pipeline;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

public interface SceneUniform {
    String getUBOName();

    int getUBOSize();

    void write(Std140Writer writer);

    /** Returns true when the previously uploaded buffer can be reused. */
    boolean shouldUseBuffer(SceneUniform lastBuffered);

    default void write(@NotNull ByteBuffer byteBuffer) {
        write(ByteBufferStd140Writer.intoBuffer(byteBuffer));
    }
}
