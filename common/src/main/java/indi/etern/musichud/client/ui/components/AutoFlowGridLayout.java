package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.GridLayout;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class AutoFlowGridLayout extends GridLayout {
    private int lastMeasuredWidth = -1;
    @Setter
    @Getter
    private int rowMinWidth;

    public AutoFlowGridLayout(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setColumnCount(1);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        if (width != lastMeasuredWidth && width > 0) {
            lastMeasuredWidth = width;
            int availableWidth = width - getPaddingLeft() - getPaddingRight();
            int newColumnCount = Math.max(1, availableWidth / rowMinWidth);
            if (newColumnCount != getColumnCount()) {
                post(() -> setColumnCount(newColumnCount));
            }
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }


    @Override
    public void addView(@NotNull View view) {
        addViewInternal(WRAP_CONTENT, WRAP_CONTENT, view);
        requestLayout();
    }

    private void addViewInternal(int width, int height, @NotNull View view) {
        LayoutParams params = new LayoutParams();
        params.width = width;
        params.height = height;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED);
        params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.TOP);
        super.addView(view, params);
    }

    public void addView(@NotNull View view, ViewGroup.LayoutParams params) {
        addViewInternal(params.width, params.height, view);
        requestLayout();
    }

    @Override
    public void removeView(@NotNull View view) {
        super.removeView(view);
        requestLayout();
    }
}