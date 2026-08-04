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
 */
public class FlexWrapLayout extends LinearLayout {
    final List<View> allChildren = new ArrayList<>();
    private final List<LinearLayout> rows = new ArrayList<>();
    boolean attached = false;

    public FlexWrapLayout(Context context) {
        super(context);
        setOrientation(VERTICAL);  // 主容器垂直排列(多行)
        addOnLayoutChangeListener((v1, left, top, right, bottom,
                                   oldLeft, oldTop, oldRight, oldBottom) -> {
            int newWidth = right - left;
            int oldWidth = oldRight - oldLeft;

            if (newWidth != oldWidth && newWidth > 0) {
                post(FlexWrapLayout.this::reflowChildren);
            }
        });
        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                post(() -> {
                    attached = true;
                    reflowChildren();
                });
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                attached = false;
                removeAllRowViews();
            }
        });
    }

    private void removeAllRowViews() {
        rows.forEach(ViewGroup::removeAllViews);
        rows.clear();
        super.removeAllViews();
    }

    /**
     * 添加子 View 到布局中
     * 这个方法会自动处理换行逻辑
     */
    @Override
    public void addView(@NotNull View view) {
        // 测量子 View 的宽度
        allChildren.add(view);
        addViewInternal(view);
    }

    private void addViewInternal(View view) {
        view.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        );
        int childWidth = view.getMeasuredWidth();
        if (attached && view.getParent() == null) {
            // 查找可以容纳这个子 View 的行
            LinearLayout targetRow = findOrCreateRowForChild(childWidth);
            targetRow.addView(view);
        }
    }

    /**
     * 查找或创建一个可以容纳指定宽度子 View 的行
     */
    private LinearLayout findOrCreateRowForChild(int childWidth) {
        // 尝试在现有行中找到空间
        for (LinearLayout row : rows) {
            int currentRowWidth = calculateRowWidth(row);
            int availableWidth = getWidth() - currentRowWidth;
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
    }

    public void reflowChildren() {
        rows.forEach(ViewGroup::removeAllViews);
        List<View> allChildren1 = List.copyOf(allChildren);
        for (View child : allChildren1) {
            addViewInternal(child);
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

    @Override
    public void removeView(@NotNull View view) {
        allChildren.remove(view);
        rows.forEach(row -> {
            row.removeView(view);
        });
        reflowChildren();
    }
}