package indi.etern.musichud.client.audio;

/**
 * Consumes the PCM data currently being played (position-corrected via the
 * OpenAL sample offset), not the data just fed into the source queue, so
 * visualizers stay in sync with what is actually audible.
 * <p>
 * Called on the play worker thread; implementations must not block or do heavy
 * work (run FFT/rendering on another thread).
 */
@FunctionalInterface
public interface PcmSink {
    void onPcm(PcmChunk chunk);
}
