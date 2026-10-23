package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

public final class SceneRawUniform implements SceneUniform {
    private final String name;
    private final int size;
    private final Consumer<ByteBuffer> writer;

    public SceneRawUniform(String name, int size, Consumer<ByteBuffer> writer) {
        if (size <= 0) throw new IllegalArgumentException("UBO size must be positive");
        this.name = Objects.requireNonNull(name, "name");
        this.size = size;
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @Override
    public String getUBOName() {
        return name;
    }

    @Override
    public int getUBOSize() {
        return size;
    }

    @Override
    public void write(Std140Writer writer) {
        throw new UnsupportedOperationException("Raw uniform writes through ByteBuffer");
    }

    @Override
    public void write(ByteBuffer byteBuffer) {
        writer.accept(byteBuffer);
    }

    @Override
    public boolean shouldUseBuffer(SceneUniform lastBuffered) {
        return false;
    }
}
