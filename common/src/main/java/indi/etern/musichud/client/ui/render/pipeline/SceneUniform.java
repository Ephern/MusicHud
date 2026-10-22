package indi.etern.musichud.client.ui.render.pipeline;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

/**
 * Version-neutral handle for a uniform buffer object backed by a shader uniform block.
 * <p>
 * High-level HUD code only depends on {@link #getUBOName()}, {@link #getUBOSize()},
 * {@link #write(Std140Writer)} and {@link #shouldUseBuffer(SceneUniform)}. The concrete
 * upload/bind mechanism is owned by the version-specific render context.
 */
public interface SceneUniform {
    String getUBOName();

    int getUBOSize();

    void write(Std140Writer writer);

    /**
     * @return {@code true} if the previously uploaded uniform (given as {@code lastBuffered})
     *         is still valid for this frame, allowing the caller to skip re-uploading.
     */
    boolean shouldUseBuffer(SceneUniform lastBuffered);

    default void write(@NotNull ByteBuffer byteBuffer) {
        write(ByteBufferStd140Writer.intoBuffer(byteBuffer));
    }
}
