package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.GridLayout;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

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
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = getWidth();
        if (width != lastMeasuredWidth && width > 0) {
            lastMeasuredWidth = width;
            int availableWidth = width - getPaddingLeft() - getPaddingRight();
            int newColumnCount = Math.max(1, availableWidth / calculateActualMinWidth());

            if (newColumnCount != getColumnCount()) {
                performRelayout(newColumnCount);
            }
        }

        super.onLayout(changed, left, top, right, bottom);
    }

    private void performRelayout(int newColumnCount) {
        if (getChildCount() == 0) {
            setColumnCount(newColumnCount);
            return;
        }

        List<View> children = new ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            children.add(getChildAt(i));
        }

        removeAllViews();
        setColumnCount(newColumnCount);

        for (View child : children) {
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = WRAP_CONTENT;
            params.height = WRAP_CONTENT;
            super.addView(child, params);
        }
    }

    private int calculateActualMinWidth() {
        int minWidth = Integer.MAX_VALUE;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
            minWidth = Math.min(minWidth, child.getMeasuredWidth());
        }
        int i = minWidth == Integer.MAX_VALUE ? rowMinWidth : minWidth;
        return i;
    }

    @Override
    public void addView(@NotNull View view) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = WRAP_CONTENT;
        params.height = WRAP_CONTENT;
        super.addView(view, params);
        requestLayout();
    }

    public void addView(@NotNull View view, ViewGroup.LayoutParams params) {
        GridLayout.LayoutParams gridParams = new GridLayout.LayoutParams();
        gridParams.width = params.width;
        gridParams.height = params.height;
        super.addView(view, gridParams);
        requestLayout();
    }

    @Override
    public void removeView(@NotNull View view) {
        super.removeView(view);
        requestLayout();
    }
}