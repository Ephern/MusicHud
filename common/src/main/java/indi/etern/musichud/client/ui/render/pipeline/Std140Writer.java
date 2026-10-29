package indi.etern.musichud.client.ui.render.pipeline;

import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

public interface Std140Writer {
    Std140Writer putFloat(float value);

    Std140Writer putInt(int value);

    Std140Writer putVec2(float x, float y);

    Std140Writer putVec3(float x, float y, float z);

    Std140Writer putVec3(Vector3fc vec);

    Std140Writer putVec4(float x, float y, float z, float w);

    Std140Writer putVec4(Vector4fc vec);

    Std140Writer putMat4f(Matrix4fc mat);
}
