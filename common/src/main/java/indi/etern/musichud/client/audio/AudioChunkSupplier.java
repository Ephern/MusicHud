package indi.etern.musichud.client.audio;

import org.jetbrains.annotations.Nullable;

/**
 * Supplies PCM chunks for the OpenAL source; {@code null} means no data is
 * currently available (underrun or end of stream).
 */
@FunctionalInterface
public interface AudioChunkSupplier {
    byte @Nullable [] next();
}
