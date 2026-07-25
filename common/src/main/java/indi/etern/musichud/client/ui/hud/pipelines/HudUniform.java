package indi.etern.musichud.client.ui.hud.pipelines;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

public interface HudUniform {
    String getUBOName();
    int getUBOSize();
    void write(Std140BufferWriter writer);
    boolean shouldUseBuffer(HudUniform lastBuffered);
    default void write(@NotNull ByteBuffer byteBuffer) {
        write(Std140BufferWriter.intoBuffer(byteBuffer));
    }
}