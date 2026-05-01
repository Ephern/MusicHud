package indi.etern.musichud.client.ui.hud.metadata;

import com.mojang.blaze3d.buffers.Std140Builder;
import indi.etern.musichud.client.ui.hud.pipelines.HudUniform;
import lombok.Getter;
import lombok.Setter;

public class GradientTextData implements HudUniform {
    @Getter @Setter
    private Layout layout;
    public final int[] colors = new int[4];
    public final float[] positions = new float[4];
    public float spread = 0f;
    public float textWidth;
    public float textStartX;
    public int colorCount = 1;

    public GradientTextData(Layout layout) {
        this.layout = layout;
        colors[0] = 0xFFFFFFFF;
        colors[1] = 0xFFFFFFFF;
        colors[2] = 0xFFFFFFFF;
        colors[3] = 0xFFFFFFFF;
        positions[0] = -1;
        positions[1] = -1;
        positions[2] = -1;
        positions[3] = -1;
    }

    public void configureTwoColor(int color0, int color1) {
        colors[0] = color0;
        colors[1] = color1;
        positions[0] = 0f;
        positions[1] = 100f;
        colorCount = 2;
    }

    public void configureThreeColor(int color0, int color1, int color2, float pos1Pct) {
        colors[0] = color0;
        colors[1] = color1;
        colors[2] = color2;
        positions[0] = 0f;
        positions[1] = pos1Pct;
        positions[2] = 100f;
        colorCount = 3;
    }

    public void configureFourColor(int color0, int color1, int color2, int color3,
                                   float pos0Pct, float pos1Pct, float pos2Pct) {
        colors[0] = color0;
        colors[1] = color1;
        colors[2] = color2;
        colors[3] = color3;
        positions[0] = pos0Pct;
        positions[1] = pos1Pct;
        positions[2] = pos2Pct;
        positions[3] = 100f;
        colorCount = 4;
    }

    @Override
    public String getUBOName() {
        return "MHGradientText";
    }

    @Override
    public int getUBOSize() {
        return 0;
    }

    @Override
    public void write(Std140Builder builder) {

    }

    @Override
    public boolean shouldUseBuffer(HudUniform lastBuffered) {
        return false;
    }

/*
    @Override
    public Matrix4f matrix4f() {
        Matrix4f m = new Matrix4f();
        m.setColumn(0, colorToVector(colors[0]));
        m.setColumn(1, colorToVector(colors[1]));
        m.setColumn(2, colorToVector(colors[2]));
        m.setColumn(3, new Vector4f(textStartX, 0, 0, 0));
        return m;
    }
*/

/*
    @Override
    public Vector4f vector4f() {
        return new Vector4f(positions[0] / 100f, positions[1] / 100f,
                positions[2] / 100f, positions[3] / 100f);
    }
*/

/*
    @Override
    public Vector3f vector3f() {
        return new Vector3f(spread, colorCount, textWidth);
    }
*/
}
