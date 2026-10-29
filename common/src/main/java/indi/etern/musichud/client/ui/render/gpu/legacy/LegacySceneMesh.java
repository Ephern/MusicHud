package indi.etern.musichud.client.ui.render.gpu.legacy;

import com.mojang.blaze3d.platform.GlStateManager;
import indi.etern.musichud.client.ui.render.gpu.SceneMesh;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

final class LegacySceneMesh implements SceneMesh, AutoCloseable {
    private static final int STRIDE = 5 * Float.BYTES;
    private static final int POSITION_LOCATION = 0;
    private static final int UV_LOCATION = 2;

    private final int vao;
    private final int vertexBuffer;
    private final int indexBuffer;
    private final int indexCount;
    private final int vertexBytes;
    private boolean closed;

    LegacySceneMesh(int vertexBytes, short[] indices) {
        this.vertexBytes = vertexBytes;
        this.indexCount = indices.length;
        this.vao = GlStateManager._glGenVertexArrays();
        GlStateManager._glBindVertexArray(vao);
        this.vertexBuffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexBytes, GL15.GL_DYNAMIC_DRAW);
        // Locations must match HudShaderManager: Position=0, UV0=2 (Color=1 is unused here)
        GL20.glEnableVertexAttribArray(POSITION_LOCATION);
        GL20.glVertexAttribPointer(POSITION_LOCATION, 3, GL11.GL_FLOAT, false, STRIDE, 0L);
        GL20.glEnableVertexAttribArray(UV_LOCATION);
        GL20.glVertexAttribPointer(UV_LOCATION, 2, GL11.GL_FLOAT, false, STRIDE, 3L * Float.BYTES);
        this.indexBuffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        ShortBuffer data = MemoryUtil.memAllocShort(indices.length);
        try {
            data.put(indices).flip();
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(data);
        }
        GL30.glBindVertexArray(0);
    }

    void update(ByteBuffer vertices) {
        if (closed) throw new IllegalStateException("Mesh is closed");
        if (vertices.remaining() != vertexBytes) {
            throw new IllegalArgumentException("Mesh vertex size changed");
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, vertices);
    }

    void draw() {
        GlStateManager._glBindVertexArray(vao);
        GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_SHORT, 0L);
    }

    @Override
    public int indexCount() {
        return indexCount;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        GL15.glDeleteBuffers(vertexBuffer);
        GL15.glDeleteBuffers(indexBuffer);
        GlStateManager._glDeleteVertexArrays(vao);
    }
}
