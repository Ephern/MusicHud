package indi.etern.musichud.client.ui.layout;

public final class LeafNode extends LayoutNode {
    private final MeasureFunction measureFunction;

    public LeafNode(MeasureFunction measureFunction) {
        this.measureFunction = measureFunction;
    }

    @Override
    public void measure(float width, float height) {
        setMeasuredSize(width, height);
    }

    @Override
    public void arrange(float x, float y) {
        this.x = x;
        this.y = y;
        writeBounds();
    }

    @Override
    protected float intrinsicWidth(float availableWidth, float availableHeight) {
        if (measureFunction == null) {
            return 0;
        }
        if (widthMode == SizeSpec.Mode.FIXED) {
            return widthValue;
        }
        if (widthMode == SizeSpec.Mode.PERCENT) {
            return availableWidth * widthValue;
        }
        return measureFunction.measure(availableWidth, availableHeight).width();
    }

    @Override
    protected float intrinsicHeight(float availableWidth, float availableHeight) {
        if (measureFunction == null) {
            return 0;
        }
        if (heightMode == SizeSpec.Mode.FIXED) {
            return heightValue;
        }
        if (heightMode == SizeSpec.Mode.PERCENT) {
            return availableHeight * heightValue;
        }
        return measureFunction.measure(availableWidth, availableHeight).height();
    }
}
