package indi.etern.musichud.client.ui.hud.pipelines;

import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

/**
 * Version-neutral writer for std140 layout uniform data.
 * <p>
 * High-level HUD code (metadata beans, renderers) writes uniform data through this
 * interface only, so that each supported Minecraft version only needs to provide a
 * thin adapter ({@link ByteBufferStd140Writer} here) instead of reimplementing the
 * metadata layer.
 */
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
