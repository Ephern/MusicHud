package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import indi.etern.musichud.client.ui.render.gpu.SceneMesh;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

final class SceneGpuMeshImpl implements SceneMesh, AutoCloseable {
    private GpuBuffer vertices;
    private final GpuBuffer indices;
    private final int indexCount;
    private final int vertexBytes;
    private boolean closed;

    SceneGpuMeshImpl(String label, int vertexBytes, short[] indices) {
        RenderSystem.assertOnRenderThread();
        if (vertexBytes <= 0 || indices.length == 0) {
            throw new IllegalArgumentException("Mesh must have vertices and indices");
        }
        this.vertexBytes = vertexBytes;
        this.indexCount = indices.length;
        this.vertices = RenderSystem.getDevice().createBuffer(() -> label + " vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, vertexBytes);
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
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(vertices.slice(), data);
    }

    GpuBuffer vertices() {
        return vertices;
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
        if (vertices != null) { vertices.close(); vertices = null; }
        if (indices != null) { indices.close(); }
    }
}
