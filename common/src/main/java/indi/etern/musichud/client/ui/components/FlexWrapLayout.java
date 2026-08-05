package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * 一个支持自动换行的弹性布局容器,类似于 CSS flexbox 的 flex-wrap: wrap
 * 使用多个水平 LinearLayout 来实现多行布局
 * Rows are (re)built synchronously in onMeasure where the parent-provided
 * width is already known, so the first layout pass renders correctly and no
 * deferred reflow (post) is needed.
 */
public class FlexWrapLayout extends LinearLayout {
    final List<View> allChildren = new ArrayList<>();
    private final List<LinearLayout> rows = new ArrayList<>();
    private boolean rowsDirty = true;

    public FlexWrapLayout(Context context) {
        super(context);
        setOrientation(VERTICAL);  // 主容器垂直排列(多行)
        addOnLayoutChangeListener((v1, left, top, right, bottom,
                                   oldLeft, oldTop, oldRight, oldBottom) -> {
            int newWidth = right - left;
            int oldWidth = oldRight - oldLeft;

            if (newWidth != oldWidth && newWidth > 0) {
                rowsDirty = true;
                requestLayout();
            }
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (rowsDirty && width > 0) {
            rowsDirty = false;
            rebuildRows(width);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        rows.forEach(ViewGroup::removeAllViews);
        rows.clear();
        super.removeAllViews();
        rowsDirty = true;
    }

    /**
     * 添加子 View 到布局中
     * 实际的行分配延迟到下一次 onMeasure 时按真实宽度执行
     */
    @Override
    public void addView(@NotNull View view) {
        allChildren.add(view);
        rowsDirty = true;
        requestLayout();
    }

    private void rebuildRows(int width) {
        rows.forEach(ViewGroup::removeAllViews);
        List<View> children = List.copyOf(allChildren);
        for (View child : children) {
            child.measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
            int childWidth = child.getMeasuredWidth();
            // 查找可以容纳这个子 View 的行
            LinearLayout targetRow = findOrCreateRowForChild(width, childWidth);
            targetRow.addView(child);
        }
        List<LinearLayout> rowsToRemove = new ArrayList<>();
        for (LinearLayout row : rows) {
            if (row.getChildCount() == 0) {
                rowsToRemove.add(row);
                super.removeView(row);
            }
        }
        rows.removeAll(rowsToRemove);
    }

    /**
     * 查找或创建一个可以容纳指定宽度子 View 的行
     */
    private LinearLayout findOrCreateRowForChild(int width, int childWidth) {
        // 尝试在现有行中找到空间
        for (LinearLayout row : rows) {
            int currentRowWidth = calculateRowWidth(row);
            int availableWidth = width - currentRowWidth;
            if (availableWidth >= childWidth) {
                return row;
            }
        }

        // 没有找到合适的行,创建新行
        return createNewRow();
    }

    /**
     * 创建新的一行
     */
    private LinearLayout createNewRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);

        var params = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);

        super.addView(row, params);
        rows.add(row);

        return row;
    }

    /**
     * 计算一行的当前宽度
     */
    private int calculateRowWidth(LinearLayout row) {
        int width = 0;
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            child.measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
            width += child.getMeasuredWidth();
        }
        return width;
    }

    @Override
    public void removeAllViews() {
        rows.clear();
        allChildren.clear();
        super.removeAllViews();
        rowsDirty = true;
    }

    @Override
    public void removeView(@NotNull View view) {
        allChildren.remove(view);
        rows.forEach(row -> {
            row.removeView(view);
        });
        rowsDirty = true;
        requestLayout();
    }
}
