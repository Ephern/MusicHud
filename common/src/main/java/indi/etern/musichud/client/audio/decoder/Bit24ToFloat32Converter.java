package indi.etern.musichud.client.audio.decoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Converts 24-bit signed PCM samples (little-endian, 3 bytes/sample)
 * to IEEE 754 float32 PCM (little-endian, 4 bytes/sample) in the [-1.0, 1.0]
 * range, suitable for the {@code AL_EXT_FLOAT32} buffer formats.
 * <p>
 * Lossless: a 24-bit integer maps exactly onto the 24-bit significand of a
 * float32, so no dithering is required (unlike downconversion to 16-bit).
 */
public class Bit24ToFloat32Converter implements IResampler {

    // 2^23: scale of a 24-bit signed sample
    private static final float SCALE = 8388608.0f;

    @Override
    public byte[] resample(byte[] input) {
        if (input == null || input.length == 0) return new byte[0];

        int sampleCount = input.length / 3;
        ByteBuffer output = ByteBuffer.allocate(sampleCount * 4);
        output.order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < sampleCount; i++) {
            int offset = i * 3;

            int b0 = input[offset] & 0xFF;
            int b1 = input[offset + 1] & 0xFF;
            int b2 = input[offset + 2]; // byte, auto sign-extends to int

            int sample24 = (b2 << 16) | (b1 << 8) | b0;

            output.putFloat(sample24 / SCALE);
        }

        return output.array();
    }
}
