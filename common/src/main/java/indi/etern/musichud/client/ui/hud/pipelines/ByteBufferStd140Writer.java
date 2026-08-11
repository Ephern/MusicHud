package indi.etern.musichud.client.ui.hud.pipelines;

import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

import java.nio.ByteBuffer;

/**
 * A dependency-free {@link Std140Writer} that replicates the exact byte layout of
 * Minecraft's {@code Std140Builder}, so the same metadata beans work on every
 * supported version regardless of where the platform-specific std140 builder lives.
 */
public class ByteBufferStd140Writer implements Std140Writer {
    private final ByteBuffer buffer;
    private final int start;

    public ByteBufferStd140Writer(ByteBuffer buffer) {
        this.buffer = buffer;
        this.start = buffer.position();
    }

    public static ByteBufferStd140Writer intoBuffer(ByteBuffer buffer) {
        return new ByteBufferStd140Writer(buffer);
    }

    public ByteBuffer get() {
        return buffer.flip();
    }

    private ByteBufferStd140Writer align(int alignment) {
        int position = buffer.position();
        buffer.position(start + roundToward(position - start, alignment));
        return this;
    }

    private static int roundToward(int value, int alignment) {
        return value + (value % alignment == 0 ? 0 : alignment - value % alignment);
    }

    @Override
    public Std140Writer putFloat(float value) {
        align(4);
        buffer.putFloat(value);
        return this;
    }

    @Override
    public Std140Writer putInt(int value) {
        align(4);
        buffer.putInt(value);
        return this;
    }

    @Override
    public Std140Writer putVec2(float x, float y) {
        align(8);
        buffer.putFloat(x);
        buffer.putFloat(y);
        return this;
    }

    @Override
    public Std140Writer putVec3(float x, float y, float z) {
        align(16);
        buffer.putFloat(x);
        buffer.putFloat(y);
        buffer.putFloat(z);
        buffer.position(buffer.position() + 4);
        return this;
    }

    @Override
    public Std140Writer putVec3(Vector3fc vec) {
        align(16);
        vec.get(buffer);
        buffer.position(buffer.position() + 16);
        return this;
    }

    @Override
    public Std140Writer putVec4(float x, float y, float z, float w) {
        align(16);
        buffer.putFloat(x);
        buffer.putFloat(y);
        buffer.putFloat(z);
        buffer.putFloat(w);
        return this;
    }

    @Override
    public Std140Writer putVec4(Vector4fc vec) {
        align(16);
        vec.get(buffer);
        buffer.position(buffer.position() + 16);
        return this;
    }

    @Override
    public Std140Writer putMat4f(Matrix4fc mat) {
        align(16);
        mat.get(buffer);
        buffer.position(buffer.position() + 64);
        return this;
    }
}
