package indi.etern.musichud.client.ui.components;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.FormatType;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.client.audio.info.AudioChannelLayout;
import indi.etern.musichud.client.audio.info.AudioPlaybackInfo;
import indi.etern.musichud.client.audio.info.AudioSource;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class AudioInfoFormat {
    private static final DateTimeFormatter TIME_WITH_HOUR = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIME_WITHOUT_HOUR = DateTimeFormatter.ofPattern("mm:ss");
    private static final String PLACEHOLDER = "—";

    private AudioInfoFormat() {
    }

    public static String quality(@Nullable Quality quality) {
        Quality canonical = quality == null ? Quality.NONE : quality.canonical();
        return canonical == Quality.NONE ? PLACEHOLDER : canonical.toString();
    }

    /** Short quality name for compact UI. */
    public static String shortQuality(@Nullable Quality quality) {
        Quality canonical = quality == null ? Quality.NONE : quality.canonical();
        return switch (canonical) {
            case STANDARD -> I18n.get(MusicHud.MOD_ID + ".config.common.primaryChosenQuality.STANDARD");
            case EX_HIGH, HIGHER -> "HQ";
            case LOSSLESS -> "SQ";
            case HIRES -> "Hi-Res";
            case JY_EFFECT -> "Spatial";
            case JY_MASTER -> "Master";
            case SKY -> "Surround";
            case DOLBY -> "Atmos";
            case VIVID -> "Vivid";
            case NONE -> PLACEHOLDER;
        };
    }

    public static String source(AudioSource source) {
        return I18n.get(MusicHud.MOD_ID + ".text.audioSource." + source.name());
    }

    public static String layout(AudioChannelLayout layout) {
        return switch (layout) {
            case MONO -> I18n.get(MusicHud.MOD_ID + ".text.audioLayout.MONO");
            case STEREO -> I18n.get(MusicHud.MOD_ID + ".text.audioLayout.STEREO");
            case SURROUND_3_0 -> "3.0";
            case QUAD -> I18n.get(MusicHud.MOD_ID + ".text.audioLayout.QUAD");
            case SURROUND_5_0 -> "5.0";
            case SURROUND_5_1 -> "5.1";
            case SURROUND_6_1 -> "6.1";
            case SURROUND_7_1 -> "7.1";
            case MULTICHANNEL -> I18n.get(MusicHud.MOD_ID + ".text.audioLayout.MULTICHANNEL");
            case UNKNOWN -> PLACEHOLDER;
        };
    }

    public static String bitrate(int bps) {
        return bps > 0 ? String.format(Locale.ROOT, "%.0f kbps", bps / 1000.0) : PLACEHOLDER;
    }

    public static String size(long bytes) {
        return bytes > 0 ? String.format(Locale.ROOT, "%.1f MiB", bytes / 1048576.0) : PLACEHOLDER;
    }

    public static String sampleRate(int hz) {
        if (hz <= 0) return PLACEHOLDER;
        return hz % 1000 == 0
                ? String.format(Locale.ROOT, "%d kHz", hz / 1000)
                : String.format(Locale.ROOT, "%.1f kHz", hz / 1000.0);
    }

    public static String bitDepth(int bits) {
        return bits > 0 ? bits + "-bit" : PLACEHOLDER;
    }

    public static String duration(long ms) {
        if (ms <= 0) return "--:--";
        Duration duration = Duration.ofMillis(ms);
        DateTimeFormatter formatter = duration.toHoursPart() >= 1 ? TIME_WITH_HOUR : TIME_WITHOUT_HOUR;
        return formatter.format(LocalTime.MIDNIGHT.plusSeconds(duration.toSeconds()));
    }

    public static String reportedStream(AudioPlaybackInfo info) {
        FormatType type = info.reportedType();
        String typeName = type == null || type == FormatType.AUTO ? PLACEHOLDER : type.name();
        return typeName + " · " + bitrate(info.reportedBitrateBps());
    }
}
