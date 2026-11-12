package indi.etern.musichud.fabric.render;

import indi.etern.musichud.client.ui.render.gpu.SceneMesh;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.buffer.IndexBuffer;
import net.vulkanmod.vulkan.memory.buffer.VertexBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/** Indexed mesh backed by VulkanMod's per-frame vertex/index buffers. */
final class VulkanModSceneMesh implements SceneMesh, AutoCloseable {
    private final VertexBuffer vertices;
    private final IndexBuffer indices;
    private final int vertexBytes;
    private final int indexCount;
    private boolean closed;

    VulkanModSceneMesh(int vertexBytes, short[] indices) {
        this.vertexBytes = vertexBytes;
        this.indexCount = indices.length;
        this.vertices = new VertexBuffer(vertexBytes, MemoryTypes.GPU_MEM);
        ByteBuffer indexData = MemoryUtil.memAlloc(indices.length * Short.BYTES);
        try {
            for (short index : indices) {
                indexData.putShort(index);
            }
            indexData.flip();
            this.indices = new IndexBuffer(indexData.remaining(), MemoryTypes.GPU_MEM,
                    IndexBuffer.IndexType.UINT16);
            this.indices.copyBuffer(indexData, indexData.remaining(), 0);
        } finally {
            MemoryUtil.memFree(indexData);
        }
    }

    void update(ByteBuffer data) {
        if (closed) throw new IllegalStateException("Mesh is closed");
        if (data.remaining() != vertexBytes) {
            throw new IllegalArgumentException("Mesh vertex size changed");
        }
        // Buffer#copyBuffer(ByteBuffer, int) appends at the write cursor; overwrite in place.
        vertices.copyBuffer(data, data.remaining(), 0);
    }

    VertexBuffer vertices() {
        return vertices;
    }

    IndexBuffer indices() {
        return indices;
    }

    @Override
    public int indexCount() {
        return indexCount;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        vertices.scheduleFree();
        indices.scheduleFree();
    }
}
