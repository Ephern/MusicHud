package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import indi.etern.musichud.client.ui.render.gpu.SceneMesh;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Vertex/index buffers of a scene mesh. Vertices alternate between two buffers, so an upload never
 * touches the buffer referenced by commands still in flight.
 */
final class SceneGpuMeshImpl implements SceneMesh, AutoCloseable {
    private static final int VERTEX_SLOTS = 2;

    private final GpuBuffer[] vertices = new GpuBuffer[VERTEX_SLOTS];
    private final GpuBuffer indices;
    private final int indexCount;
    private final int vertexBytes;
    private int cursor;
    private boolean closed;

    SceneGpuMeshImpl(String label, int vertexBytes, short[] indices) {
        RenderSystem.assertOnRenderThread();
        if (vertexBytes <= 0 || indices.length == 0) {
            throw new IllegalArgumentException("Mesh must have vertices and indices");
        }
        this.vertexBytes = vertexBytes;
        this.indexCount = indices.length;
        for (int i = 0; i < vertices.length; i++) {
            final int slot = i;
            vertices[i] = RenderSystem.getDevice().createBuffer(
                    () -> label + " vertices " + slot,
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, vertexBytes);
        }
        ByteBuffer indexData = MemoryUtil.memAlloc(indices.length * 2);
        try {
            for (short index : indices) indexData.putShort(index);
            indexData.flip();
            this.indices = RenderSystem.getDevice().createBuffer(() -> label + " indices",
                    GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, indexData);
        } finally {
            MemoryUtil.memFree(indexData);
        }
    }

    void update(ByteBuffer data) {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("Mesh is closed");
        if (data.remaining() != vertexBytes) {
            throw new IllegalArgumentException("Mesh vertex size changed");
        }
        cursor = (cursor + 1) % VERTEX_SLOTS;
        RenderSystem.getDevice().createCommandEncoder()
                .writeToBuffer(vertices[cursor].slice(), data);
    }

    GpuBuffer vertices() {
        return vertices[cursor];
    }

    GpuBuffer indices() {
        return indices;
    }

    @Override
    public int indexCount() {
        return indexCount;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        for (int i = 0; i < vertices.length; i++) {
            if (vertices[i] != null) {
                vertices[i].close();
                vertices[i] = null;
            }
        }
        if (indices != null) {
            indices.close();
        }
    }
}
