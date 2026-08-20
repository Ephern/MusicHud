package indi.etern.musichud.client.audio;

import java.nio.ByteBuffer;

/**
 * A chunk of PCM data currently being played. {@code data} is a slice of the
 * underlying direct buffer and is only valid during the callback.
 */
public record PcmChunk(ByteBuffer data, int format, int sampleRate) {
}
