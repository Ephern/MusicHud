package indi.etern.musichud.client.audio.info;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTFloat32;
import org.lwjgl.openal.EXTMCFormats;

public enum AudioChannelLayout {
    MONO(1),
    STEREO(2),
    SURROUND_3_0(3),
    QUAD(4),
    SURROUND_5_0(5),
    SURROUND_5_1(6),
    SURROUND_6_1(7),
    SURROUND_7_1(8),
    MULTICHANNEL(-1),
    UNKNOWN(0);

    private final int channels;

    AudioChannelLayout(int channels) {
        this.channels = channels;
    }

    public int channelCount() {
        return channels;
    }

    public static AudioChannelLayout fromChannels(int channels) {
        return switch (channels) {
            case 1 -> MONO;
            case 2 -> STEREO;
            case 3 -> SURROUND_3_0;
            case 4 -> QUAD;
            case 5 -> SURROUND_5_0;
            case 6 -> SURROUND_5_1;
            case 7 -> SURROUND_6_1;
            case 8 -> SURROUND_7_1;
            default -> channels > 8 ? MULTICHANNEL : UNKNOWN;
        };
    }

    public static AudioChannelLayout fromOpenAlFormat(int format) {
        return switch (format) {
            case AL10.AL_FORMAT_MONO8, AL10.AL_FORMAT_MONO16,
                 EXTFloat32.AL_FORMAT_MONO_FLOAT32 -> MONO;
            case AL10.AL_FORMAT_STEREO8, AL10.AL_FORMAT_STEREO16,
                 EXTFloat32.AL_FORMAT_STEREO_FLOAT32 -> STEREO;
            case EXTMCFormats.AL_FORMAT_QUAD8, EXTMCFormats.AL_FORMAT_QUAD16 -> QUAD;
            case EXTMCFormats.AL_FORMAT_51CHN8, EXTMCFormats.AL_FORMAT_51CHN16 -> SURROUND_5_1;
            case EXTMCFormats.AL_FORMAT_61CHN8, EXTMCFormats.AL_FORMAT_61CHN16 -> SURROUND_6_1;
            case EXTMCFormats.AL_FORMAT_71CHN8, EXTMCFormats.AL_FORMAT_71CHN16 -> SURROUND_7_1;
            default -> UNKNOWN;
        };
    }
}
