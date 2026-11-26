package indi.etern.musichud.client.audio.info;

import indi.etern.musichud.beans.music.FormatType;
import indi.etern.musichud.beans.music.Quality;
import org.jetbrains.annotations.Nullable;

public record AudioPlaybackInfo(
        long revision,
        long musicId,
        Quality requestedQuality,
        Quality reportedQuality,
        Quality estimatedQuality,
        Quality effectiveQuality,
        boolean qualityMismatch,
        AudioSource source,
        FormatType reportedType,
        int reportedBitrateBps,
        long sizeBytes,
        @Nullable AudioStreamInfo stream,
        @Nullable AudioOutputInfo output
) {
}
