package indi.etern.musichud.client.audio;

import java.nio.ByteBuffer;

/** Borrowed playing PCM window, valid only during the callback. Position is in decoded sample frames. */
public record PcmChunk(ByteBuffer data, int format, int sampleRate, long firstSampleFrame, float gain, long captureNanos) {
    public PcmChunk(ByteBuffer data, int format, int sampleRate, long firstSampleFrame, float gain) {
        this(data, format, sampleRate, firstSampleFrame, gain, 0);
    }
    public PcmChunk(ByteBuffer data, int format, int sampleRate, long firstSampleFrame) {
        this(data, format, sampleRate, firstSampleFrame, 1, 0);
    }
    /** Unpositioned chunks are treated as consecutive, non-overlapping data. */
    public PcmChunk(ByteBuffer data, int format, int sampleRate) {
        this(data, format, sampleRate, -1, 1, 0);
    }
}
