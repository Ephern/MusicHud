package indi.etern.musichud.client.ui.render.pipeline;

import lombok.Getter;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL31.glBindBufferRange;

public class HudShaderProgram implements ScenePipeline {
    @Getter
    private final int programId;
    private final Map<String, Integer> uniformLocations = new HashMap<>();
    private final String name;

    public HudShaderProgram(int programId, String name) {
        this.programId = programId;
        this.name = name;
    }

    public int getUniformOrSamplerLocation(String name) {
        if (programId <= 0) {
            // glGetUniformLocation on a non-program handle raises GL_INVALID_VALUE
            return -1;
        }
        return uniformLocations.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
    }

    @Override
    public String name() {
        return name;
    }

    public static final class UniformBufferHandle {
        @Getter
        private final int uboId;
        @Getter
        private final int bindingPoint;
        private final int size;

        public UniformBufferHandle(int uboId, int bindingPoint, int size) {
            this.uboId = uboId;
            this.bindingPoint = bindingPoint;
            this.size = size;
        }

        public static UniformBufferHandle createAndUpload(int bindingPoint, ByteBuffer data) {
            int dataSize = data.remaining();
            int[] ubo = new int[1];
            glGenBuffers(ubo);
            int uboId = ubo[0];
            glBindBuffer(GL_UNIFORM_BUFFER, uboId);
            glBufferData(GL_UNIFORM_BUFFER, data, GL_DYNAMIC_DRAW);
            glBindBufferRange(GL_UNIFORM_BUFFER, bindingPoint, uboId, 0, dataSize);
            return new UniformBufferHandle(uboId, bindingPoint, dataSize);
        }

        public void upload(ByteBuffer data) {
            data.rewind();
            glBindBuffer(GL_UNIFORM_BUFFER, uboId);
            glBufferSubData(GL_UNIFORM_BUFFER, 0, data);
            glBindBufferRange(GL_UNIFORM_BUFFER, bindingPoint, uboId, 0, size);
        }

        public void bind() {
            glBindBufferRange(GL_UNIFORM_BUFFER, bindingPoint, uboId, 0, size);
        }

        public void delete() {
            glDeleteBuffers(uboId);
        }
    }
}
