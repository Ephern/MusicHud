package indi.etern.musichud.client.ui.hud.pipelines;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Std140BufferWriter {
    private final ByteBuffer buffer;

    private Std140BufferWriter(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public static Std140BufferWriter intoBuffer(ByteBuffer buffer) {
        return new Std140BufferWriter(buffer);
    }

    public static Std140BufferWriter allocate(int capacity) {
        return new Std140BufferWriter(ByteBuffer.allocate(capacity).order(ByteOrder.nativeOrder()));
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public int position() {
        return buffer.position();
    }

    public void position(int pos) {
        buffer.position(pos);
    }

    // std140: mat4 = 4 * vec4 = 64 bytes, aligned to 16
    public Std140BufferWriter putMat4f(Matrix4f mat) {
        align(16);
        mat.get(buffer);
        return this;
    }

    // std140: vec3 = 12 bytes, then padded to 16 bytes (next vec4 alignment)
    public Std140BufferWriter putVec3(float x, float y, float z) {
        align(16);
        buffer.putFloat(x).putFloat(y).putFloat(z);
        padTo(16);
        return this;
    }

    // std140: vec2 = 8 bytes, aligned to 8
    public Std140BufferWriter putVec2(float x, float y) {
        align(8);
        buffer.putFloat(x).putFloat(y);
        return this;
    }

    // std140: float = 4 bytes, aligned to 4
    public Std140BufferWriter putFloat(float f) {
        align(4);
        buffer.putFloat(f);
        return this;
    }

    // std140: int = 4 bytes, aligned to 4
    public Std140BufferWriter putInt(int i) {
        align(4);
        buffer.putInt(i);
        return this;
    }

    // std140: vec4 = 16 bytes, aligned to 16
    public Std140BufferWriter putVec4(float x, float y, float z, float w) {
        align(16);
        buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
        return this;
    }

    public Std140BufferWriter align(int alignment) {
        int pos = buffer.position();
        int remainder = pos % alignment;
        if (remainder != 0) {
            int pad = alignment - remainder;
            buffer.position(pos + pad);
        }
        return this;
    }

    private void padTo(int alignment) {
        int remainder = buffer.position() % alignment;
        if (remainder != 0) {
            int pad = alignment - remainder;
            buffer.position(buffer.position() + pad);
        }
    }

    // Size calculation helper (replaces Std140SizeCalculator)
    public static class Calculator {
        private int size;

        public Calculator putMat4f() {
            size = align16(size) + 64;
            return this;
        }

        public Calculator putVec3() {
            size = align16(size) + 12 + pad16rem12();
            return this;
        }

        public Calculator putVec4() {
            size = align16(size) + 16;
            return this;
        }

        public Calculator putVec2() {
            int remainder = size % 8;
            if (remainder != 0) size += 8 - remainder;
            size += 8;
            return this;
        }

        public Calculator putFloat() {
            int remainder = size % 4;
            if (remainder != 0) size += 4 - remainder;
            size += 4;
            return this;
        }

        public Calculator align(int alignment) {
            int remainder = size % alignment;
            if (remainder != 0) {
                size += alignment - remainder;
            }
            return this;
        }

        public int get() {
            return size;
        }

        private static int align16(int v) {
            int rem = v % 16;
            return rem != 0 ? v + 16 - rem : v;
        }

        private static int pad16rem12() {
            // vec3 is 12 bytes, padded to 16
            return 4;
        }
    }
}
