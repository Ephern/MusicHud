package indi.etern.musichud.client.audio.info;

public record DecodedStreamInfo(
        AudioContainer container,
        AudioChannelLayout channelLayout,
        int sampleRate,
        int channels,
        int bitsPerSample,
        int bitrateBps,
        long totalSamples,
        boolean variableBitrate
) {
}
