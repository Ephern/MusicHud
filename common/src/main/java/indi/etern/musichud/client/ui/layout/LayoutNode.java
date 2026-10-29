package indi.etern.musichud.client.ui.layout;

import lombok.Getter;
import lombok.Setter;

public abstract class LayoutNode {
    protected Insets padding = Insets.NONE;
    protected Insets margin = Insets.NONE;
    protected float gap;
    protected SizeSpec.Mode widthMode = SizeSpec.Mode.AUTO;
    protected float widthValue;
    protected SizeSpec.Mode heightMode = SizeSpec.Mode.AUTO;
    protected float heightValue;
    protected float flexGrow;
    protected float flexShrink;
    protected int overflowOrder;
    protected boolean hideOnOverflow;
    protected boolean collapseWhenEmptyAndTrailing;
    protected float aspectRatio;
    protected AlignItems alignItems = AlignItems.START;
    protected JustifyContent justifyContent = JustifyContent.START;
    protected AlignSelf alignSelf = AlignSelf.AUTO;
    @Getter
    @Setter
    protected boolean visible = true;
    protected float minWidth;
    protected float maxWidth = Float.MAX_VALUE;
    protected float minHeight;
    protected float maxHeight = Float.MAX_VALUE;
    @Getter
    protected float x;
    @Getter
    protected float y;
    protected float measuredWidth;
    protected float measuredHeight;
    protected float resolvedMain;
    protected float resolvedCross;
    protected boolean collapsed;
    @Setter
    protected LayoutTarget target;

    public void apply(BoxStyleSpec style) {
        padding = style.padding();
        margin = style.margin();
        gap = style.gap();
        widthMode = style.width().mode();
        widthValue = style.width().value();
        heightMode = style.height().mode();
        heightValue = style.height().value();
        flexGrow = style.flexGrow();
        flexShrink = style.flexShrink();
        overflowOrder = style.overflowOrder();
        hideOnOverflow = style.hideOnOverflow();
        collapseWhenEmptyAndTrailing = style.collapseWhenEmptyAndTrailing();
        aspectRatio = style.aspectRatio();
        alignItems = style.alignItems();
        justifyContent = style.justifyContent();
        alignSelf = style.alignSelf();
        visible = style.visible();
        minWidth = style.minWidth();
        maxWidth = style.maxWidth();
        minHeight = style.minHeight();
        maxHeight = style.maxHeight();
    }

    public void setWidth(float width) {
        widthMode = SizeSpec.Mode.FIXED;
        widthValue = width;
    }

    public void setHeight(float height) {
        heightMode = SizeSpec.Mode.FIXED;
        heightValue = height;
    }

    public float getWidth() {
        return measuredWidth;
    }

    public float getHeight() {
        return measuredHeight;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public boolean isLayoutVisible() {
        return visible && !collapsed;
    }

    public boolean isCollapseWhenEmptyAndTrailing() {
        return collapseWhenEmptyAndTrailing;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        if (collapsed && target != null) {
            target.setBounds(0, 0, 0, 0);
        }
    }

    public void resetCollapse() {
        collapsed = false;
    }

    public abstract void measure(float width, float height);

    public abstract void arrange(float x, float y);

    protected final void setMeasuredSize(float width, float height) {
        measuredWidth = Math.clamp(width, minWidth, maxWidth);
        measuredHeight = Math.clamp(height, minHeight, maxHeight);
    }

    protected final float outerMain(boolean row) {
        return (row ? measuredWidth : measuredHeight) + (row
                ? margin.left() + margin.right()
                : margin.top() + margin.bottom());
    }

    protected final float outerCross(boolean row) {
        return (row ? measuredHeight : measuredWidth) + (row
                ? margin.top() + margin.bottom()
                : margin.left() + margin.right());
    }

    protected final void writeBounds() {
        if (target != null) {
            target.setBounds(x, y, measuredWidth, measuredHeight);
        }
    }

    protected final float resolveSize(SizeSpec.Mode mode, float value, float parentSize,
                                       float contentSize) {
        return switch (mode) {
            case FIXED -> value;
            case PERCENT -> parentSize * value;
            case FILL -> parentSize;
            case CONTENT, AUTO -> contentSize;
        };
    }

    protected float intrinsicWidth(float availableWidth, float availableHeight) {
        return 0;
    }

    protected float intrinsicHeight(float availableWidth, float availableHeight) {
        return 0;
    }
}
