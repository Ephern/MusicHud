package indi.etern.musichud.client.audio.decoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Converts 24-bit signed PCM samples (little-endian, 3 bytes/sample)
 * to 16-bit signed PCM (little-endian, 2 bytes/sample) with TPDF dithering.
 * <p>
 * TPDF (Triangular Probability Density Function) dither eliminates
 * quantization harmonic distortion by adding 2-LSB peak-to-peak triangular noise
 * before truncation, decorrelating the quantization error from the signal.
 */
public class Bit24To16Resampler implements IResampler {

    // 1 LSB of 16-bit output in the 24-bit domain = 2^8 = 256
    // TPDF dither peak-to-peak = 2 LSB = 512, i.e. triangular PDF in [-256, +256]
    // rounding adds half-LSB = 128
    private static final int SCALE = 1 << 8;   // 256
    private static final int HALF = SCALE >> 1; // 128

    @Override
    public byte[] resample(byte[] input) {
        if (input == null || input.length == 0) return new byte[0];

        int sampleCount = input.length / 3;
        ByteBuffer output = ByteBuffer.allocate(sampleCount * 2);
        output.order(ByteOrder.LITTLE_ENDIAN);

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int i = 0; i < sampleCount; i++) {
            int offset = i * 3;

            int b0 = input[offset] & 0xFF;
            int b1 = input[offset + 1] & 0xFF;
            int b2 = input[offset + 2]; // byte, auto sign-extends to int

            int sample24 = (b2 << 16) | (b1 << 8) | b0;

            // TPDF dither: (u1 + u2 - 1) * SCALE, range [-256, +256], triangular PDF
            int dither = (int) ((rng.nextDouble() + rng.nextDouble() - 1.0) * SCALE);

            // apply dither + rounding, then truncate
            int sample16 = (sample24 + dither + HALF) >> 8;

            // clamp to 16-bit signed range
            if (sample16 > Short.MAX_VALUE) {
                sample16 = Short.MAX_VALUE;
            } else if (sample16 < Short.MIN_VALUE) {
                sample16 = Short.MIN_VALUE;
            }

            output.putShort((short) sample16);
        }

        return output.array();
    }
}
