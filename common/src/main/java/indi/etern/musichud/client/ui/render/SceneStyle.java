package indi.etern.musichud.client.ui.render;

import indi.etern.musichud.client.ui.layout.LayoutTarget;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Sizes;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

@EqualsAndHashCode
@Getter
public class SceneStyle implements SceneUniform, LayoutTarget {
    public static final int UBO_SIZE = Std140Sizes.calc().putMat4f().putVec4().align(16).get(); // pad to 16-byte alignment (std140)
    private volatile float x, y, width, height;
    private volatile float radius;
    private volatile float alpha = 1;
    private volatile HorizontalAlign horizontalAlign;
    private volatile VerticalAlign verticalAlign;
    private volatile SceneStyle parent;

    private boolean dirty;
    private AbsolutePosition effectiveAbsolutePosition;
    private float effectiveAbsoluteAlpha;

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
        checkAbsoluteValuesDirty();
        Matrix3x2f localMatrix = new Matrix3x2f();
        localMatrix.translate(effectiveAbsolutePosition.x(), effectiveAbsolutePosition.y());
        builder.putMat4f(new Matrix4f().mul(localMatrix)).putVec4(width / 2, height / 2, radius, effectiveAbsoluteAlpha);
        if (dirty) {
            dirty = false;
        }
    }

    @Override
    public boolean shouldUseBuffer(SceneUniform lastBuffered) {
        checkAbsoluteValuesDirty();
        return equals(lastBuffered) && !dirty;
    }

    private void checkAbsoluteValuesDirty() {
        AbsolutePosition absolutePosition = calcAbsoluteCenterPosition(SceneRenderContext.getCurrent());
        if (!absolutePosition.equals(effectiveAbsolutePosition)) {
            dirty = true;
            effectiveAbsolutePosition = absolutePosition;
        }
        float absoluteAlpha = calcAbsoluteAlpha();
        if (absoluteAlpha != effectiveAbsoluteAlpha) {
            dirty = true;
            effectiveAbsoluteAlpha = absoluteAlpha;
        }
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
        if (this.x != x || this.y != y || this.width != width || this.height != height) {
            this.dirty = true;
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setX(float x) {
        this.dirty |= this.x != x;
        this.x = x;
    }

    public void setY(float y) {
        this.dirty |= this.y != y;
        this.y = y;
    }

    public void setWidth(float width) {
        this.dirty |= this.width != width;
        this.width = width;
    }

    public void setHeight(float height) {
        this.dirty |= this.height != height;
        this.height = height;
    }

    public void setRadius(float radius) {
        this.dirty = true;
        this.radius = radius;
    }

    public void setAlpha(float alpha) {
        float clamped = Math.clamp(alpha, 0, 1);
        this.dirty = this.alpha != clamped;
        this.alpha = clamped;
    }

    public void setHorizontalAlign(HorizontalAlign horizontalAlign) {
        this.dirty = true;
        this.horizontalAlign = horizontalAlign;
    }

    public void setVerticalAlign(VerticalAlign verticalAlign) {
        this.dirty = true;
        this.verticalAlign = verticalAlign;
    }

    public void setParent(SceneStyle parent) {
        this.dirty = true;
        this.parent = parent;
    }

    public record AbsolutePosition(float x, float y) {
    }
}
