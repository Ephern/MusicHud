package indi.etern.musichud.client.audio.decoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Downmixes interleaved PCM with more than 2 channels to stereo.
 * <p>
 * Even channel counts ({@code N=2k}) are averaged pairwise: left is the mean
 * of even-indexed channels, right the mean of odd-indexed channels, which
 * keeps energy constant and avoids clipping from naive summation. A trailing
 * odd channel (typically center/LFE) is mixed into both sides at half weight.
 * Averaging uses integer accumulation with clamping to the source bit depth;
 * bit-depth conversion (if any) is a separate downstream stage.
 */
public class MultichannelToStereoMixer implements IResampler {
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

        int pairs = channels / 2;
        boolean hasOddTail = (channels % 2) != 0;
        // even channels contribute 1.0 each, odd tail contributes 0.5 to both sides
        double divisor = pairs + (hasOddTail ? 0.5 : 0);

        ByteBuffer in = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer out = ByteBuffer.allocate(frameCount * 2 * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN);

        for (int f = 0; f < frameCount; f++) {
            long left = 0;
            long right = 0;
            for (int c = 0; c < channels - (hasOddTail ? 1 : 0); c++) {
                int sample = readSample(in, f * frameBytes + c * bytesPerSample);
                if ((c & 1) == 0) {
                    left += sample;
                } else {
                    right += sample;
                }
            }
            double leftAvg = left / divisor;
            double rightAvg = right / divisor;
            if (hasOddTail) {
                // trailing channel (typically center/LFE) goes to both sides at half weight
                int tail = readSample(in, f * frameBytes + (channels - 1) * bytesPerSample);
                leftAvg += tail * 0.5 / divisor;
                rightAvg += tail * 0.5 / divisor;
            }
            writeSample(out, clamp((long) Math.round(leftAvg)));
            writeSample(out, clamp((long) Math.round(rightAvg)));
        }
        return out.array();
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
