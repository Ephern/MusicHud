package indi.etern.musichud.client.audio;

/**
 * Immutable normalized PCM band energy. Zero timestamp denotes an inactive/invalidated session;
 * timestampNanos measures FFT assembly freshness, not PCM content or DAC presentation time.
 */
public record SpectrumSnapshot(long generation, long sequence, long timestampNanos,
                               float low, float mid, float high, int sampleRate, int frames) {
    static SpectrumSnapshot silence(long generation, long sequence) {
        return new SpectrumSnapshot(generation, sequence, 0, 0, 0, 0, 0, 0);
    }
}
