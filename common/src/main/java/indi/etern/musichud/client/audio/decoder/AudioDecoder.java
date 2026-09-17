package indi.etern.musichud.client.audio.decoder;

public interface AudioDecoder extends AutoCloseable {
    byte[] readChunk(long maxSize);
    int getFormat();
    int getSampleRate();

    int getFrameSize();

    /**
     * Whether this decoder is currently emitting a discrete multichannel
     * ({@code AL_EXT_MCFORMATS}) stream that the OpenAL device may reject.
     */
    default boolean isDiscreteAttempt() {
        return false;
    }

    /**
     * One-shot switch from the discrete multichannel pipeline to the stereo
     * downmix pipeline, keeping the current stream position. No-op unless
     * {@link #isDiscreteAttempt()} is true.
     */
    default void fallbackToDownmix() {
    }

    void close();
}
