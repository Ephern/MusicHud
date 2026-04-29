package indi.etern.musichud.client.ui.hud.metadata;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import indi.etern.musichud.client.ui.hud.pipelines.HudUniform;
import indi.etern.musichud.client.ui.hud.renderer.HudRenderContext;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

import java.util.Objects;

@EqualsAndHashCode
public class Layout implements HudUniform {
    public static final int UBO_SIZE = new Std140SizeCalculator().putMat4f().putVec3().align(16).get(); // pad to 16-byte alignment (std140)
    public volatile float x, y, width, height;
    public volatile float radius;
    public volatile HorizontalAlign horizontalAlign;
    public volatile VerticalAlign verticalAlign;
    public String targetElementName = "";
    @Setter
    @Getter
    private volatile Layout parent;

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
    public void write(Std140Builder builder) {
        Matrix3x2f localMatrix = new Matrix3x2f();
        Layout.AbsolutePosition absolutePosition = calcAbsoluteCenterPosition(HudRenderContext.getCurrent());
        localMatrix.translate(absolutePosition.x(), absolutePosition.y());
        builder.putMat4f(new Matrix4f().mul(localMatrix)).putVec3(width / 2, height / 2, radius);
    }

    @Override
    public boolean dataEquals(HudUniform other) {
        return other instanceof Layout layout
                && Objects.equals(x, layout.x)
                && Objects.equals(y, layout.y)
                && Objects.equals(width, layout.width)
                && Objects.equals(height, layout.height)
                && Objects.equals(radius, layout.radius)
                && Objects.equals(horizontalAlign, layout.horizontalAlign)
                && Objects.equals(verticalAlign, layout.verticalAlign)
                && ((parent == null && layout.parent == null) || (parent != null && parent.dataEquals(layout.parent)));
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

    public record AbsolutePosition(float x, float y) {
    }
}
