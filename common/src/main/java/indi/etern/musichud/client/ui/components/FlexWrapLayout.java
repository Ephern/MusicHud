package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
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
    private int rowSpacing = 0;
    private int colSpacing = 0;
    private int maxWidth = Integer.MAX_VALUE;

    private final List<LinearLayout> rows = new ArrayList<>();

    public FlexWrapLayout(Context context) {
        super(context);
        setOrientation(VERTICAL);  // 主容器垂直排列(多行)

//        LayoutTransition transition = new LayoutTransition();
//        transition.enableTransitionType(LayoutTransition.CHANGING);
//        setLayoutTransition(transition);
        addOnLayoutChangeListener((v, left, top, right, bottom,
                                              oldLeft, oldTop, oldRight, oldBottom) -> {
            int newWidth = right - left;
            int oldWidth = oldRight - oldLeft;

            if (newWidth != oldWidth && newWidth > 0) {
                post(() -> {
                    setMaxWidth(newWidth);
                    reflowChildren();
                });
            }
        });
    }

    /**
     * 设置子项之间的水平间距
     */
    public void setItemSpacing(int spacing) {
        rowSpacing = spacing;
        requestLayout();
    }

    /**
     * 设置行之间的垂直间距
     */
    public void setLineSpacing(int spacing) {
        colSpacing = spacing;
        requestLayout();
    }

    /**
     * 设置每行的最大宽度
     */
    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
        requestLayout();
    }

    /**
     * 添加子 View 到布局中
     * 这个方法会自动处理换行逻辑
     */
    @Override
    public void addView(@NotNull View view) {
        addView(view, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
    }

    /**
     * 添加子 View 到布局中,带自定义 LayoutParams
     */
    void addView(View view, LayoutParams params) {
        // 测量子 View 的宽度
        view.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        );
        int childWidth = view.getMeasuredWidth();

        // 查找可以容纳这个子 View 的行
        LinearLayout targetRow = findOrCreateRowForChild(childWidth);

        // 设置子 View 的 margin
        if (targetRow.getChildCount() > 0) {
            params.leftMargin = rowSpacing;
        }

        targetRow.addView(view, params);
    }

    /**
     * 查找或创建一个可以容纳指定宽度子 View 的行
     */
    private LinearLayout findOrCreateRowForChild(int childWidth) {
        // 尝试在现有行中找到空间
        for (LinearLayout row : rows) {
            int currentRowWidth = calculateRowWidth(row);
            int availableWidth = maxWidth - currentRowWidth;

            // 如果当前行有子 View,需要考虑 item spacing
            if (row.getChildCount() > 0) {
                availableWidth -= rowSpacing;
            }

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
        if (!rows.isEmpty()) {
            params.topMargin = colSpacing;
        }


//        LayoutTransition transition = new LayoutTransition();
//        transition.enableTransitionType(LayoutTransition.CHANGING);
//        row.setLayoutTransition(transition);

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

            // 添加 item spacing(除了第一个子项)
            if (i > 0) {
                width += rowSpacing;
            }
        }
        return width;
    }

    /**
     * 清除所有子 View
     */
    public void clearFlexChildren() {
        removeAllViews();
        rows.clear();
    }

    /**
     * 重新布局所有子 View
     * 当容器宽度变化时调用
     */
    public void reflowChildren() {
        // 收集所有子 View
        List<View> allChildren = new ArrayList<>();
        for (LinearLayout row : rows) {
            for (int i = 0; i < row.getChildCount(); i++) {
                allChildren.add(row.getChildAt(i));
            }
            row.removeAllViews();
        }

        // 清除现有布局
        clearFlexChildren();

        // 重新添加所有子 View
        for (View child : allChildren) {
            addView(child);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);

        // 如果宽度变化,可能需要重新布局
        if (width != maxWidth && width > 0) {
            maxWidth = width;
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void removeView(@NotNull View view) {
        rows.forEach(row -> {
            row.removeView(view);
        });
        reflowChildren();
    }
}