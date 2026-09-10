package indi.etern.musichud.client.audio.decoder;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTMCFormats;

/**
 * Maps (channel count, bits per sample) to discrete multichannel OpenAL
 * buffer formats ({@code AL_EXT_MCFORMATS}).
 * <p>
 * Only 8-bit and 16-bit variants are used: the extension exposes no float32
 * multichannel formats, so 24/32-bit sources must be resampled to 16-bit
 * before taking the discrete path. Channel counts without a standard layout
 * (3, 5, &gt;8) have no mapping and must go through the stereo downmix path.
 */
public final class MultichannelFormats {
    private MultichannelFormats() {
    }

    /**
     * @param channels       source channel count (&gt;2 expected)
     * @param bitsPerSample  effective bits per sample (8 or 16)
     * @return OpenAL format constant, or -1 if no discrete format exists
     */
    public static int discreteFormat(int channels, int bitsPerSample) {
        return switch (channels) {
            case 4 -> switch (bitsPerSample) {
                case 8 -> EXTMCFormats.AL_FORMAT_QUAD8;
                case 16 -> EXTMCFormats.AL_FORMAT_QUAD16;
                default -> -1;
            };
            case 6 -> switch (bitsPerSample) {
                case 8 -> EXTMCFormats.AL_FORMAT_51CHN8;
                case 16 -> EXTMCFormats.AL_FORMAT_51CHN16;
                default -> -1;
            };
            case 7 -> switch (bitsPerSample) {
                case 8 -> EXTMCFormats.AL_FORMAT_61CHN8;
                case 16 -> EXTMCFormats.AL_FORMAT_61CHN16;
                default -> -1;
            };
            case 8 -> switch (bitsPerSample) {
                case 8 -> EXTMCFormats.AL_FORMAT_71CHN8;
                case 16 -> EXTMCFormats.AL_FORMAT_71CHN16;
                default -> -1;
            };
            default -> -1;
        };
    }

    /**
     * @return channel count carried by an OpenAL buffer format, for both core
     * formats ({@link AL10}) and discrete multichannel formats
     */
    public static int channelCount(int format) {
        if (format == AL10.AL_FORMAT_MONO8 || format == AL10.AL_FORMAT_MONO16) {
            return 1;
        }
        if (format == EXTMCFormats.AL_FORMAT_QUAD8 || format == EXTMCFormats.AL_FORMAT_QUAD16) {
            return 4;
        }
        if (format == EXTMCFormats.AL_FORMAT_51CHN8 || format == EXTMCFormats.AL_FORMAT_51CHN16) {
            return 6;
        }
        if (format == EXTMCFormats.AL_FORMAT_61CHN8 || format == EXTMCFormats.AL_FORMAT_61CHN16) {
            return 7;
        }
        if (format == EXTMCFormats.AL_FORMAT_71CHN8 || format == EXTMCFormats.AL_FORMAT_71CHN16) {
            return 8;
        }
        return 2;
    }

    public static boolean isDiscreteFormat(int format) {
        return channelCount(format) > 2;
    }
}
