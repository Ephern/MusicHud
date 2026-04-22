package indi.etern.musichud.client.ui.hud.metadata;

import indi.etern.musichud.client.ui.hud.renderer.HudRenderContext;
import lombok.Getter;
import lombok.Setter;

public class Layout {
    public volatile float x, y, width, height;
    public volatile float radius;
    public volatile HorizontalAlign hPosition;
    public volatile VerticalAlign verticalAlign;
    @Setter
    @Getter
    private volatile Layout parent;

    public Layout(float x, float y, float width, float height, float radius) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.radius = radius;
        hPosition = HorizontalAlign.LEFT;
        verticalAlign = VerticalAlign.TOP;
    }

    public Layout(float x, float y, float width, float height, float radius, HorizontalAlign hPosition, VerticalAlign verticalAlign) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.radius = radius;
        this.hPosition = hPosition;
        this.verticalAlign = verticalAlign;
    }

    public record AbsolutePosition(float x, float y) {}
    public AbsolutePosition calcAbsolutePosition(HudRenderContext context) {
        if (parent != null) {
            AbsolutePosition absolutePosition = parent.calcAbsolutePosition(context);
            float xOffset = hPosition.calcX(x, context, getRootLayout());
            float yOffset = verticalAlign.calcY(y, context, getRootLayout());
            return new AbsolutePosition(absolutePosition.x + xOffset, absolutePosition.y + yOffset);
        } else {
            float xOffset = hPosition.calcX(x, context, getRootLayout());
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

    public static Layout ofTextLayout(float x, float y, float maxWidth, float fontSize) {
        return new Layout(x, y, maxWidth, fontSize, 0);
    }
}
