package indi.etern.musichud.client.audio.decoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Converts 32-bit signed PCM to float32 in [-1.0, 1.0] for the {@code AL_EXT_FLOAT32} buffer
 * formats, so 32-bit sources are consumed natively instead of being dithered to 16-bit.
 */
public class Bit32ToFloat32Converter implements IResampler {

    // 2^31: scale of a 32-bit signed sample
    private static final float SCALE = 2147483648.0f;

    @Override
    public byte[] resample(byte[] input) {
        if (input == null || input.length == 0) return new byte[0];

        int sampleCount = input.length / 4;
        ByteBuffer output = ByteBuffer.allocate(sampleCount * 4);
        output.order(ByteOrder.LITTLE_ENDIAN);

        ByteBuffer inputBuf = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < sampleCount; i++) {
            int sample32 = inputBuf.getInt();
            output.putFloat(sample32 / SCALE);
        }

        return output.array();
    }
}
