package indi.etern.musichud.client.ui.hud.metadata;

import indi.etern.musichud.client.ui.hud.piplines.UniformData;
import indi.etern.musichud.client.ui.utils.Transitionable;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static indi.etern.musichud.client.ui.utils.UniformDataUtils.colorToVector;

@Setter
public class HudRenderData implements UniformData {
    private static final int TRANSITION_DURATION_MS = 500;
    volatile Layout layout;
    volatile Transitionable<BackgroundData> transitionableBackground;
    volatile HudRenderData fallback;
    private long initTimestamp;

    public HudRenderData(Layout layout, BackgroundImages backgroundImages) {
        this.layout = layout;
        BackgroundData backgroundData = backgroundImages == null ? BackgroundData.NONE : new BackgroundData(backgroundImages);
        transitionableBackground = new Transitionable<>(backgroundData, TRANSITION_DURATION_MS);
        initTimestamp = System.currentTimeMillis();
    }

    public HudRenderData(Layout layout) {
        this.layout = layout;
        initTimestamp = System.currentTimeMillis();
    }

    public Transitionable<BackgroundData> getTransitionableBackground() {
        if (fallback != null && transitionableBackground == null) {
            return fallback.transitionableBackground;
        } else {
            return transitionableBackground;
        }
    }

    @Override
    public Layout getLayout() {
        if (fallback != null && layout == null) {
            return fallback.layout;
        } else {
            return layout;
        }
    }

    @Override
    public Vector4f vector4f() {
        Layout layout = getLayout();
        return new Vector4f(
                layout.width / 2,
                layout.height / 2,
                Math.min(Math.min(layout.width, layout.height) / 2, layout.radius),
                (System.currentTimeMillis() - initTimestamp) / 1000.0f
        );
    }

    @Override
    public Vector3f vector3f() {
        Transitionable<BackgroundData> transitionableBackground = getTransitionableBackground();
        return new Vector3f(
                transitionableBackground.getProgress(),
                1.0f,
                1.0f
        );
    }

    @Override
    public Matrix4f matrix4f() {
        ThemedColors bgColor = getTransitionableBackground().getMixed().color();
        return bgColor == null ? new Matrix4f() : buildColorMatrix(bgColor);
    }

    private Matrix4f buildColorMatrix(ThemedColors bgColor) {
        Matrix4f matrix = new Matrix4f();
        matrix.setColumn(0, colorToVector(bgColor.primary));
        matrix.setColumn(1, colorToVector(bgColor.secondary));
        matrix.setColumn(2, colorToVector(bgColor.bright));
        matrix.setColumn(3, colorToVector(bgColor.dark));
        return matrix;
    }
}