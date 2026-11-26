package indi.etern.musichud.client.audio.info;

public record AudioStreamInfo(
        AudioContainer container,
        AudioChannelLayout channelLayout,
        int sampleRate,
        int channels,
        int bitsPerSample,
        int bitrateBps,
        long totalSamples,
        long durationMs,
        boolean variableBitrate
) {
}
