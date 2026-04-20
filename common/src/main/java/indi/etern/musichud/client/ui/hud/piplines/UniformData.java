package indi.etern.musichud.client.ui.hud.piplines;

import indi.etern.musichud.client.ui.hud.metadata.Layout;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public interface UniformData {
    Layout getLayout();
    Vector4f vector4f();
    Vector3f vector3f();
    Matrix4f matrix4f();
}
