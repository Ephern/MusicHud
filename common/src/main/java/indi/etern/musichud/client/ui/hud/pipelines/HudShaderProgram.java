package indi.etern.musichud.client.ui.hud.pipelines;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL31.*;

public class HudShaderProgram {
    private final int programId;
    private final Map<String, Integer> uniformBlockBindingPoints = new HashMap<>();
    private final Map<String, Integer> uniformLocations = new HashMap<>();

    public HudShaderProgram(int programId) {
        this.programId = programId;
    }

    public int getProgramId() {
        return programId;
    }

    public void cacheUniformLocation(String name) {
        int loc = glGetUniformLocation(programId, name);
        if (loc != -1) {
            uniformLocations.put(name, loc);
        }
    }

    public int getUniformLocation(String name) {
        Integer loc = uniformLocations.get(name);
        return loc != null ? loc : -1;
    }

    public void setUniformBlockBindingPoint(String blockName, int bindingPoint) {
        uniformBlockBindingPoints.put(blockName, bindingPoint);
    }

    public Integer getUniformBlockBindingPoint(String blockName) {
        return uniformBlockBindingPoints.get(blockName);
    }

    public static final class UniformBufferHandle {
        private final int uboId;
        private final int bindingPoint;

        public UniformBufferHandle(int uboId, int bindingPoint) {
            this.uboId = uboId;
            this.bindingPoint = bindingPoint;
        }

        public int getUboId() {
            return uboId;
        }

        public int getBindingPoint() {
            return bindingPoint;
        }

        public static UniformBufferHandle createAndUpload(int bindingPoint, ByteBuffer data) {
            int[] ubo = new int[1];
            glGenBuffers(ubo);
            int uboId = ubo[0];
            glBindBuffer(GL_UNIFORM_BUFFER, uboId);
            glBufferData(GL_UNIFORM_BUFFER, data, GL_DYNAMIC_DRAW);
            glBindBufferBase(GL_UNIFORM_BUFFER, bindingPoint, uboId);
            return new UniformBufferHandle(uboId, bindingPoint);
        }

        public void upload(ByteBuffer data) {
            data.rewind();
            glBindBuffer(GL_UNIFORM_BUFFER, uboId);
            glBufferData(GL_UNIFORM_BUFFER, data, GL_DYNAMIC_DRAW);
            glBindBufferBase(GL_UNIFORM_BUFFER, bindingPoint, uboId);
        }

        public void bind() {
            glBindBufferBase(GL_UNIFORM_BUFFER, bindingPoint, uboId);
        }

        public void delete() {
            glDeleteBuffers(uboId);
        }
    }
}
