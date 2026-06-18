package indi.etern.musichud.client.audio.decoder;

/**
 * Resamples raw PCM audio data from a higher bit depth to a lower bit depth.
 */
public interface IResampler {
    /**
     * @param input raw audio bytes in source format (little-endian)
     * @return resampled audio bytes in target format (little-endian)
     */
    byte[] resample(byte[] input);
}
