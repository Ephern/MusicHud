package indi.etern.musichud.client.ui.hud.metadata;

import indi.etern.musichud.client.ui.hud.pipelines.HudUniform;
import indi.etern.musichud.client.ui.hud.pipelines.Std140BufferWriter;
import indi.etern.musichud.client.ui.hud.renderer.HudRenderContext;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

@EqualsAndHashCode
@Getter
public class Layout implements HudUniform {
    public static final int UBO_SIZE = new Std140BufferWriter.Calculator().putMat4f().putVec3().align(16).get();
    private volatile float x, y, width, height;
    private volatile float radius;
    private volatile HorizontalAlign horizontalAlign;
    private volatile VerticalAlign verticalAlign;
    private String targetElementName = "";
    private volatile Layout parent;
    private boolean dirty;
    private AbsolutePosition lastAbsolutePosition;

    public Layout(String targetElementName, float x, float y, float width, float height, float radius) {
        this.targetElementName = targetElementName;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.radius = radius;
        horizontalAlign = HorizontalAlign.LEFT;
        verticalAlign = VerticalAlign.TOP;
    }

    public Layout(String targetElementName, float x, float y, float width, float height, float radius, HorizontalAlign horizontalAlign, VerticalAlign verticalAlign) {
        this.targetElementName = targetElementName;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.radius = radius;
        this.horizontalAlign = horizontalAlign;
        this.verticalAlign = verticalAlign;
    }

    public static Layout ofTextLayout(String targetElementName, float x, float y, float maxWidth, float fontSize) {
        return new Layout(targetElementName, x, y, maxWidth, fontSize, 0);
    }

    @Override
    public String getUBOName() {
        return "MH" + targetElementName + "Position";
    }

    @Override
    public int getUBOSize() {
        return UBO_SIZE;
    }

    @Override
    public void write(Std140BufferWriter builder) {
        Matrix3x2f localMatrix = new Matrix3x2f();
        Layout.AbsolutePosition absolutePosition = calcAbsoluteCenterPosition(HudRenderContext.getCurrent());
        localMatrix.translate(absolutePosition.x(), absolutePosition.y());
        builder.putMat4f(new Matrix4f().mul(localMatrix)).putVec3(width / 2, height / 2, radius);
        if (dirty) {
            dirty = false;
        }
    }

    @Override
    public boolean shouldUseBuffer(HudUniform lastBuffered) {
        checkAbsolutePositionDirty();
        return equals(lastBuffered) && !dirty;
    }

    private void checkAbsolutePositionDirty() {
        AbsolutePosition absolutePosition = calcAbsoluteCenterPosition(HudRenderContext.getCurrent());
        if (!absolutePosition.equals(lastAbsolutePosition)) {
            dirty = true;
            lastAbsolutePosition = absolutePosition;
        }
    }

    public AbsolutePosition calcAbsolutePosition(HudRenderContext context) {
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

    public AbsolutePosition calcAbsoluteCenterPosition(HudRenderContext context) {
        float halfWidth = width / 2;
        float halfHeight = height / 2;
        AbsolutePosition absolutePosition = calcAbsolutePosition(context);
        float centerX = absolutePosition.x() + halfWidth;
        float centerY = absolutePosition.y() + halfHeight;
        return new AbsolutePosition(centerX, centerY);
    }

    public Layout getRootLayout() {
        if (parent != null) {
            return parent.getRootLayout();
        } else {
            return this;
        }
    }

    public void setX(float x) {
        this.dirty = true;
        this.x = x;
    }

    public void setY(float y) {
        this.dirty = true;
        this.y = y;
    }

    public void setWidth(float width) {
        this.dirty = true;
        this.width = width;
    }

    public void setHeight(float height) {
        this.dirty = true;
        this.height = height;
    }

    public void setRadius(float radius) {
        this.dirty = true;
        this.radius = radius;
    }

    public void setHorizontalAlign(HorizontalAlign horizontalAlign) {
        this.dirty = true;
        this.horizontalAlign = horizontalAlign;
    }

    public void setVerticalAlign(VerticalAlign verticalAlign) {
        this.dirty = true;
        this.verticalAlign = verticalAlign;
    }

    public void setTargetElementName(String targetElementName) {
        this.dirty = true;
        this.targetElementName = targetElementName;
    }

    public void setParent(Layout parent) {
        this.dirty = true;
        this.parent = parent;
    }

    public record AbsolutePosition(float x, float y) {
    }
}
