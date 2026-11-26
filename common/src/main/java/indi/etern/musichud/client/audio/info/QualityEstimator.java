package indi.etern.musichud.client.audio.info;

import indi.etern.musichud.beans.music.Quality;
import org.jetbrains.annotations.Nullable;

public final class QualityEstimator {
    private QualityEstimator() {
    }

    public static Quality estimate(@Nullable AudioStreamInfo stream) {
        if (stream == null) {
            return Quality.NONE;
        }
        return switch (stream.container()) {
            case FLAC, WAV -> {
                if (stream.bitsPerSample() <= 0 || stream.sampleRate() <= 0) {
                    yield Quality.NONE;
                }
                yield stream.bitsPerSample() > 16 || stream.sampleRate() > 48_000
                        ? Quality.HIRES : Quality.LOSSLESS;
            }
            case MP3 -> {
                int bitrate = stream.bitrateBps();
                if (bitrate <= 0) {
                    yield Quality.NONE;
                }
                yield bitrate >= 256_000 ? Quality.EX_HIGH
                        : bitrate >= 160_000 ? Quality.HIGHER
                        : Quality.STANDARD;
            }
            case UNKNOWN -> Quality.NONE;
        };
    }

    /**
     * Conservative display value: the report wins unless the decoded stream proves it was lossy.
     * A 16-bit/44.1kHz lossless file can still downgrade a reported Hi-Res, but a lossless file
     * never upgrades a reported lossy/immersive level.
     */
    public static Quality effective(Quality reportedQuality,
                                    Quality estimatedQuality,
                                    @Nullable AudioStreamInfo stream) {
        Quality reported = reportedQuality == null ? Quality.NONE : reportedQuality.canonical();
        Quality estimated = estimatedQuality == null ? Quality.NONE : estimatedQuality.canonical();
        if (reported == Quality.NONE) {
            return estimated;
        }
        if (estimated == Quality.NONE || stream == null) {
            return reported;
        }
        if (stream.container() == AudioContainer.MP3 && reported.rank() >= Quality.LOSSLESS.rank()) {
            return estimated;
        }
        if (reported == Quality.HIRES && estimated == Quality.LOSSLESS) {
            return Quality.LOSSLESS;
        }
        return reported;
    }

    public static boolean isMismatch(Quality reportedQuality,
                                     Quality estimatedQuality,
                                     Quality effectiveQuality) {
        Quality reported = reportedQuality == null ? Quality.NONE : reportedQuality.canonical();
        Quality estimated = estimatedQuality == null ? Quality.NONE : estimatedQuality.canonical();
        Quality effective = effectiveQuality == null ? Quality.NONE : effectiveQuality.canonical();
        return reported != Quality.NONE && estimated != Quality.NONE && effective != reported;
    }
}
