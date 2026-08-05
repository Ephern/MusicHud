package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class WaterfallLayout extends LinearLayout {
    private final List<View> allChildren = new ArrayList<>();
    private final List<LinearLayout> columns = new ArrayList<>();
    private final List<Float> columnHeights = new ArrayList<>();
    @Setter
    private int rowMinWidth;
    private boolean attached;

    public WaterfallLayout(Context context) {
        super(context);
        setOrientation(HORIZONTAL);

        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                attached = true;
                reflow();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                removeAllViews();
            }
        });

        addOnLayoutChangeListener((v, left, top, right, bottom,
                                    oldLeft, oldTop, oldRight, oldBottom) -> {
            int newWidth = right - left;
            int oldWidth = oldRight - oldLeft;
            if (newWidth != oldWidth && newWidth > 0) {
                post(WaterfallLayout.this::reflow);
            }
        });
    }

    @Override
    public void addView(@NotNull View view) {
        allChildren.add(view);
        if (attached) {
            boolean rebuilt = ensureColumns();
            if (!rebuilt && !columns.isEmpty()) {
                addToShortestColumn(view);
            }
        }
    }

    private boolean ensureColumns() {
        if (rowMinWidth <= 0) return false;
        int availableWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        if (availableWidth <= 0) return false;
        int newColumnCount = Math.max(1, availableWidth / rowMinWidth);
        if (newColumnCount != columns.size()) {
            rebuildColumns(newColumnCount);
            return true;
        }
        return false;
    }

    private void rebuildColumns(int count) {
        for (LinearLayout col : columns) {
            col.removeAllViews();
        }
        super.removeAllViews();
        columns.clear();
        columnHeights.clear();

        for (int i = 0; i < count; i++) {
            LinearLayout col = new LinearLayout(getContext());
            col.setOrientation(VERTICAL);
            super.addView(col, new LayoutParams(0, WRAP_CONTENT, 1f));
            columns.add(col);
            columnHeights.add(0f);
        }

        for (View child : allChildren) {
            if (child.getParent() == null) {
                addToShortestColumn(child);
            }
        }
    }

    private void addToShortestColumn(View child) {
        if (columns.isEmpty()) return;

        int shortestIndex = 0;
        float minHeight = columnHeights.get(0);
        for (int i = 1; i < columns.size(); i++) {
            if (columnHeights.get(i) < minHeight) {
                minHeight = columnHeights.get(i);
                shortestIndex = i;
            }
        }

        child.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        );
        float childHeight = child.getMeasuredHeight();

        columns.get(shortestIndex).addView(child);
        columnHeights.set(shortestIndex, minHeight + childHeight);
    }

    public void reflow() {
        if (allChildren.isEmpty() || rowMinWidth <= 0) return;
        ensureColumns();
    }

    @Override
    public void removeView(@NotNull View view) {
        allChildren.remove(view);
        for (LinearLayout col : columns) {
            col.removeView(view);
        }
        if (attached) {
            reflow();
        }
    }

    @Override
    public void removeAllViews() {
        allChildren.clear();
        columns.clear();
        columnHeights.clear();
        super.removeAllViews();
    }
}
