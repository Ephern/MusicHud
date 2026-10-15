package indi.etern.musichud.client.audio.decoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Downmixes interleaved PCM with more than 2 channels to stereo.
 * <p>
 * Channel layouts follow the standard WAV/FLAC ordering
 * ({@code FL, FR, FC, LFE, BL/BC, BR, SL, SR}). Center and surround channels are
 * folded into stereo using the conventional -3 dB ({@code 1/sqrt(2)}) coefficients,
 * while the LFE channel is discarded. Each side is then peak-normalized by the sum
 * of the absolute coefficients, so even fully correlated full-scale channels cannot
 * overflow, and clamped to the source bit depth as a final safeguard. Bit-depth
 * conversion (if any) is a separate downstream stage.
 */
public class MultichannelToStereoMixer implements IResampler {
    private static final double SQRT1_2 = 0.7071067811865476;
    private static final double HALF = 0.5;

    /**
     * Per channel-count downmix gains: {@code GAINS[channels] = {leftGains, rightGains}}.
     * Channel order is {@code FL, FR, FC, LFE, BL/BC, BR, SL, SR}; LFE is always zero.
     */
    private static final double[][][] GAINS = new double[9][][];

    static {
        GAINS[3] = new double[][]{
                {1.0, 0.0, SQRT1_2, 0.0, 0.0, 0.0, 0.0, 0.0},
                {0.0, 1.0, SQRT1_2, 0.0, 0.0, 0.0, 0.0, 0.0},
        };
        GAINS[4] = new double[][]{
                {1.0, 0.0, SQRT1_2, 0.0, 0.0, 0.0, 0.0, 0.0},
                {0.0, 1.0, 0.0, SQRT1_2, 0.0, 0.0, 0.0, 0.0},
        };
        GAINS[5] = new double[][]{
                {1.0, 0.0, SQRT1_2, SQRT1_2, 0.0, 0.0, 0.0, 0.0},
                {0.0, 1.0, SQRT1_2, 0.0, SQRT1_2, 0.0, 0.0, 0.0},
        };
        GAINS[6] = new double[][]{
                {1.0, 0.0, SQRT1_2, 0.0, SQRT1_2, 0.0, 0.0, 0.0},
                {0.0, 1.0, SQRT1_2, 0.0, 0.0, SQRT1_2, 0.0, 0.0},
        };
        GAINS[7] = new double[][]{
                {1.0, 0.0, SQRT1_2, 0.0, 0.0, SQRT1_2, 0.0, 0.0},
                {0.0, 1.0, SQRT1_2, 0.0, 0.0, 0.0, SQRT1_2, 0.0},
        };
        GAINS[8] = new double[][]{
                {1.0, 0.0, SQRT1_2, 0.0, HALF, 0.0, HALF, 0.0},
                {0.0, 1.0, SQRT1_2, 0.0, 0.0, HALF, 0.0, HALF},
        };
    }

    private final int channels;
    private final int bytesPerSample;
    private final boolean unsigned8;

    /**
     * @param channels       source channel count (must be &gt;2)
     * @param bytesPerSample source bytes per sample (1/2/3/4)
     * @param unsigned8      true if 8-bit samples are unsigned (WAV), false if signed (FLAC)
     */
    public MultichannelToStereoMixer(int channels, int bytesPerSample, boolean unsigned8) {
        if (channels <= 2) {
            throw new IllegalArgumentException("channels must be > 2");
        }
        if (bytesPerSample < 1 || bytesPerSample > 4) {
            throw new IllegalArgumentException("unsupported bytes per sample: " + bytesPerSample);
        }
        this.channels = channels;
        this.bytesPerSample = bytesPerSample;
        this.unsigned8 = unsigned8;
    }

    @Override
    public byte[] resample(byte[] input) {
        if (input == null || input.length == 0) return new byte[0];
        int frameBytes = channels * bytesPerSample;
        int frameCount = input.length / frameBytes;
        if (frameCount == 0) return new byte[0];

        ByteBuffer in = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer out = ByteBuffer.allocate(frameCount * 2 * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN);

        if (channels <= 8) {
            double[] leftGains = GAINS[channels][0];
            double[] rightGains = GAINS[channels][1];
            double normalization = peakNormalization(leftGains);
            for (int f = 0; f < frameCount; f++) {
                int base = f * frameBytes;
                double left = 0;
                double right = 0;
                for (int c = 0; c < channels; c++) {
                    int sample = readSample(in, base + c * bytesPerSample);
                    left += leftGains[c] * sample;
                    right += rightGains[c] * sample;
                }
                writeSample(out, clamp(Math.round(left * normalization)));
                writeSample(out, clamp(Math.round(right * normalization)));
            }
        } else {
            // Fallback for non-standard channel counts: pairwise averaging with an odd tail.
            int pairs = channels / 2;
            boolean hasOddTail = (channels % 2) != 0;
            double divisor = pairs + (hasOddTail ? 0.5 : 0);
            for (int f = 0; f < frameCount; f++) {
                int base = f * frameBytes;
                double left = 0;
                double right = 0;
                for (int c = 0; c < channels - (hasOddTail ? 1 : 0); c++) {
                    int sample = readSample(in, base + c * bytesPerSample);
                    if ((c & 1) == 0) {
                        left += sample;
                    } else {
                        right += sample;
                    }
                }
                if (hasOddTail) {
                    int tail = readSample(in, base + (channels - 1) * bytesPerSample);
                    left += tail * 0.5;
                    right += tail * 0.5;
                }
                writeSample(out, clamp(Math.round(left / divisor)));
                writeSample(out, clamp(Math.round(right / divisor)));
            }
        }
        return out.array();
    }

    private static double peakNormalization(double[] gains) {
        double sum = 0;
        for (double gain : gains) {
            sum += Math.abs(gain);
        }
        return sum > 0 ? 1.0 / sum : 1.0;
    }

    private int readSample(ByteBuffer in, int offset) {
        return switch (bytesPerSample) {
            case 1 -> {
                int v = in.get(offset);
                yield unsigned8 ? (v & 0xFF) - 128 : v;
            }
            case 2 -> in.getShort(offset);
            case 3 -> {
                int b0 = in.get(offset) & 0xFF;
                int b1 = in.get(offset + 1) & 0xFF;
                int b2 = in.get(offset + 2);
                yield (b2 << 16) | (b1 << 8) | b0;
            }
            case 4 -> in.getInt(offset);
            default -> throw new IllegalStateException();
        };
    }

    private void writeSample(ByteBuffer out, int sample) {
        switch (bytesPerSample) {
            case 1 -> out.put(unsigned8 ? (byte) (sample + 128) : (byte) sample);
            case 2 -> out.putShort((short) sample);
            case 3 -> {
                out.put((byte) (sample & 0xFF));
                out.put((byte) ((sample >> 8) & 0xFF));
                out.put((byte) ((sample >> 16) & 0xFF));
            }
            case 4 -> out.putInt(sample);
        }
    }

    private int clamp(long sample) {
        return switch (bytesPerSample) {
            case 1 -> (int) Math.clamp(sample, -128, 127);
            case 2 -> (int) Math.clamp(sample, Short.MIN_VALUE, Short.MAX_VALUE);
            case 3 -> (int) Math.clamp(sample, -(1 << 23), (1 << 23) - 1);
            case 4 -> sample > Integer.MAX_VALUE ? Integer.MAX_VALUE : (sample < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) sample);
            default -> throw new IllegalStateException();
        };
    }
}
