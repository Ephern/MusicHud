package indi.etern.musichud.client.ui.hud.pipelines;

import com.mojang.blaze3d.buffers.Std140Builder;
import net.minecraft.client.renderer.DynamicUniformStorage;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

public interface HudUniform extends DynamicUniformStorage.DynamicUniform {
    String getUBOName();
    int getUBOSize();
    void write(Std140Builder builder);
    boolean dataEquals(HudUniform other);
    default void write(@NotNull ByteBuffer byteBuffer) {
        write(Std140Builder.intoBuffer(byteBuffer));
    }
}
