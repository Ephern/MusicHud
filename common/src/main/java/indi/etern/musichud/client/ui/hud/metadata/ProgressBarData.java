package indi.etern.musichud.client.ui.hud.metadata;

import indi.etern.musichud.client.ui.hud.piplines.UniformData;
import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static indi.etern.musichud.client.ui.utils.UniformDataUtils.colorToVector;

public class ProgressBarData implements UniformData {
    public int fillColorLeft;
    public int fillColorRight;
    public int backgroundColor;
    public float gradientLength;
    public float gradientRightOffset;
    public float transitionBorderRate;
    @Getter
    @Setter
    private Layout layout;
    @Getter
    private float progress;  // 0.0 - 1.0

    public ProgressBarData(Layout layout, int fillColorLeft, int fillColorRight, int backgroundColor, float gradientLength, float gradientRightOffset, float transitionBorderRate) {
        this.layout = layout;
        this.fillColorLeft = fillColorLeft;
        this.fillColorRight = fillColorRight;
        this.backgroundColor = backgroundColor;
        this.gradientLength = gradientLength;
        this.gradientRightOffset = gradientRightOffset;
        this.transitionBorderRate = transitionBorderRate;
    }

    public void setProgress(float progress) {
        this.progress = Math.clamp(progress, 0.0f, 1.0f);
    }

    @Override
    public Vector4f vector4f() {
        return new Vector4f(
                layout.width / 2f,
                layout.height / 2f,
                layout.radius,
                progress
        );
    }

    @Override
    public Vector3f vector3f() {
        //noinspection SuspiciousNameCombination
        return new Vector3f(
                gradientLength,
                gradientRightOffset,
                transitionBorderRate
        );
    }

    @Override
    public Matrix4f matrix4f() {
        Matrix4f gradientColorsMatrix = new Matrix4f();
        gradientColorsMatrix.setColumn(0, colorToVector(fillColorLeft));
        gradientColorsMatrix.setColumn(1, colorToVector(fillColorRight));
        gradientColorsMatrix.setColumn(2, colorToVector(backgroundColor));
        gradientColorsMatrix.setColumn(3, new Vector4f());
        return gradientColorsMatrix;
    }
}