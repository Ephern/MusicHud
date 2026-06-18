package indi.etern.musichud.client.audio.decoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Converts 32-bit signed PCM samples (little-endian, 4 bytes/sample)
 * to 16-bit signed PCM (little-endian, 2 bytes/sample) with TPDF dithering.
 * <p>
 * TPDF (Triangular Probability Density Function) dither eliminates
 * quantization harmonic distortion by adding 2-LSB peak-to-peak triangular noise
 * before truncation, decorrelating the quantization error from the signal.
 */
public class Bit32To16Resampler implements IResampler {

    // 1 LSB of 16-bit output in the 32-bit domain = 2^16 = 65536
    // TPDF dither peak-to-peak = 2 LSB = 131072, i.e. triangular PDF in [-65536, +65536]
    private static final int SCALE = 1 << 16;   // 65536
    private static final int HALF = SCALE >> 1;  // 32768

    @Override
    public byte[] resample(byte[] input) {
        if (input == null || input.length == 0) return new byte[0];

        int sampleCount = input.length / 4;
        ByteBuffer output = ByteBuffer.allocate(sampleCount * 2);
        output.order(ByteOrder.LITTLE_ENDIAN);

        ByteBuffer inputBuf = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN);
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int i = 0; i < sampleCount; i++) {
            int sample32 = inputBuf.getInt();

            // TPDF dither: (u1 + u2 - 1) * SCALE, range [-65536, +65536], triangular PDF
            int dither = (int) ((rng.nextDouble() + rng.nextDouble() - 1.0) * SCALE);

            // apply dither + rounding, then truncate
            int sample16 = (sample32 + dither + HALF) >> 16;

            output.putShort((short) sample16);
        }

        return output.array();
    }
}
