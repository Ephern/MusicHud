package indi.etern.musichud.client.audio.info;

import indi.etern.musichud.client.audio.OpenAlSource;
import org.lwjgl.openal.EXTFloat32;

public record AudioOutputInfo(
        AudioChannelLayout channelLayout,
        int sampleRate,
        int channels,
        int bitsPerSample,
        boolean float32,
        boolean downmixed,
        boolean discrete
) {
    public static AudioOutputInfo describe(int format, int sampleRate, int sourceChannels, boolean discrete) {
        AudioChannelLayout layout = AudioChannelLayout.fromOpenAlFormat(format);
        int channels = Math.max(0, layout.channelCount());
        boolean float32 = format == EXTFloat32.AL_FORMAT_MONO_FLOAT32
                || format == EXTFloat32.AL_FORMAT_STEREO_FLOAT32;
        int bitsPerSample = float32
                ? 32
                : channels > 0 ? OpenAlSource.bytesPerSample(format) * 8 / channels : 0;
        boolean downmixed = sourceChannels > 2 && !discrete;
        return new AudioOutputInfo(layout, sampleRate, channels, bitsPerSample,
                float32, downmixed, discrete);
    }
}
