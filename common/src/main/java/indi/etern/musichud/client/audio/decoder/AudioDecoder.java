package indi.etern.musichud.client.audio.decoder;

import indi.etern.musichud.client.audio.info.AudioOutputInfo;
import indi.etern.musichud.client.audio.info.DecodedStreamInfo;
import org.jetbrains.annotations.Nullable;

public interface AudioDecoder extends AutoCloseable {
    byte[] readChunk(long maxSize);
    int getFormat();
    int getSampleRate();

    int getFrameSize();

    /** Source stream metadata; null until the decoder has parsed its first header/frame. */
    default @Nullable DecodedStreamInfo getStreamInfo() {
        return null;
    }

    /** Actual OpenAL output format; null until the decoder is initialized. */
    default @Nullable AudioOutputInfo getOutputInfo() {
        return null;
    }

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
