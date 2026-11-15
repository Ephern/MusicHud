package indi.etern.musichud.client.ui.render;

import indi.etern.musichud.client.ui.layout.LayoutTarget;
import indi.etern.musichud.client.ui.render.pipeline.DirtyMarker;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Sizes;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

@EqualsAndHashCode
@Getter
public class SceneStyle implements SceneUniform, LayoutTarget {
    public static final int UBO_SIZE = Std140Sizes.calc().putMat4f().putVec4().align(16).get(); // pad to 16-byte alignment (std140)
    @Setter
    private volatile float x;
    @Setter
    private volatile float y;
    @Setter
    private volatile float width;
    @Setter
    private volatile float height;
    @Setter
    private volatile float radius;
    private volatile float alpha = 1;
    @Setter
    private volatile HorizontalAlign horizontalAlign;
    @Setter
    private volatile VerticalAlign verticalAlign;
    @Setter
    private volatile SceneStyle parent;

    @Getter(AccessLevel.NONE)
    private WrittenState written;

    public SceneStyle(float x, float y, float width, float height, float radius) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.radius = radius;
        horizontalAlign = HorizontalAlign.LEFT;
        verticalAlign = VerticalAlign.TOP;
    }

    public SceneStyle(float x, float y, float width, float height, float radius, HorizontalAlign horizontalAlign, VerticalAlign verticalAlign) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.radius = radius;
        this.horizontalAlign = horizontalAlign;
        this.verticalAlign = verticalAlign;
    }

    public static SceneStyle ofTextLayout(float x, float y, float maxWidth, float fontSize) {
        return new SceneStyle(x, y, maxWidth, fontSize, 0);
    }

    @Override
    public String getUBOName() {
        return "MHPosition";
    }

    @Override
    public int getUBOSize() {
        return UBO_SIZE;
    }

    @Override
    public void write(Std140Writer builder) {
        WrittenState state = currentState();
        written = state;
        Matrix3x2f localMatrix = new Matrix3x2f();
        localMatrix.translate(state.position.x(), state.position.y());
        builder.putMat4f(new Matrix4f().mul(localMatrix))
                .putVec4(state.width / 2, state.height / 2, state.radius, state.alpha);
    }

    @Override
    public void checkDirty(DirtyMarker marker) {
        if (!currentState().equals(written)) {
            marker.markDirty();
        }
    }

    private WrittenState currentState() {
        return new WrittenState(calcAbsoluteCenterPosition(SceneRenderContext.getCurrent()),
                width, height, radius, calcAbsoluteAlpha());
    }

    private record WrittenState(AbsolutePosition position, float width, float height, float radius,
                                float alpha) {
    }

    public AbsolutePosition calcAbsolutePosition(SceneRenderContext context) {
        if (parent != null) {
            AbsolutePosition absolutePosition = parent.calcAbsolutePosition(context);
            float xOffset = horizontalAlign.calcX(x, context, getRootLayout());
            float yOffset = verticalAlign.calcY(y, context, getRootLayout());
            return new AbsolutePosition(absolutePosition.x + xOffset, absolutePosition.y + yOffset);
        } else {
            float xOffset = horizontalAlign.calcX(x, context, getRootLayout());
            float yOffset = verticalAlign.calcY(y, context, getRootLayout());
            return new AbsolutePosition(xOffset, yOffset);
        }
    }

    public AbsolutePosition calcAbsoluteCenterPosition(SceneRenderContext context) {
        float halfWidth = width / 2;
        float halfHeight = height / 2;
        AbsolutePosition absolutePosition = calcAbsolutePosition(context);
        float centerX = absolutePosition.x() + halfWidth;
        float centerY = absolutePosition.y() + halfHeight;
        return new AbsolutePosition(centerX, centerY);
    }

    public float calcAbsoluteAlpha() {
        if (parent != null) {
            return parent.calcAbsoluteAlpha() * alpha;
        } else {
            return alpha;
        }
    }

    public SceneStyle getRootLayout() {
        if (parent != null) {
            return parent.getRootLayout();
        } else {
            return this;
        }
    }

    @Override
    public void setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setAlpha(float alpha) {
        this.alpha = Math.clamp(alpha, 0, 1);
    }

    public record AbsolutePosition(float x, float y) {
    }
}
