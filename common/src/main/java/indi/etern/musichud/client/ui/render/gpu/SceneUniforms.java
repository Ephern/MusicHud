package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;

public final class SceneUniforms {
    private SceneUniforms() {
    }

    public static SceneUniform vec2(String name, float x, float y) {
        return new SceneRawUniform(name, 16,
                buffer -> buffer.putFloat(0, x).putFloat(4, y));
    }

    public static SceneUniform float1(String name, float value) {
        return new SceneRawUniform(name, 16,
                buffer -> buffer.putFloat(0, value));
    }

    public static SceneUniform bytes(String name, byte[] data) {
        return new SceneRawUniform(name, data.length,
                buffer -> {
                    for (int i = 0; i < data.length; i++) {
                        buffer.put(i, data[i]);
                    }
                });
    }
}
