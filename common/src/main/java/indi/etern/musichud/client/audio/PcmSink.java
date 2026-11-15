package indi.etern.musichud.client.audio;

/**
 * Consumes the PCM that is currently audible (position-corrected by the OpenAL sample offset), not
 * the data just queued. Called on the play worker thread: must not block or do heavy work.
 */
@FunctionalInterface
public interface PcmSink {
    void onPcm(PcmChunk chunk);
}
