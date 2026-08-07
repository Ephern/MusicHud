package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class FlexWrapLayout extends LinearLayout {
    final List<View> allChildren = new ArrayList<>();
    private final List<LinearLayout> rows = new ArrayList<>();
    private boolean rowsDirty = true;
    private final LayoutTransition defaultTransition = createDefaultTransition();
    private boolean animationsEnabled = true;

    private static LayoutTransition createDefaultTransition() {
        LayoutTransition layoutTransition = new LayoutTransition();
        layoutTransition.setDuration(300);
//        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
        layoutTransition.enableTransitionType(LayoutTransition.APPEARING);
        layoutTransition.enableTransitionType(LayoutTransition.DISAPPEARING);
        return layoutTransition;
    }

    public FlexWrapLayout(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setLayoutTransition(defaultTransition);
        addOnLayoutChangeListener((v1, left, top, right, bottom,
                                   oldLeft, oldTop, oldRight, oldBottom) -> {
            int newWidth = right - left;
            int oldWidth = oldRight - oldLeft;

            if (newWidth != oldWidth && newWidth > 0) {
                // Layout is finished here, so newWidth is the current real width
                // (no stale previous-frame data). Rebuild synchronously and let
                // the next layout pass render the new rows.
                rebuildRows(newWidth);
                requestLayout();
            }
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (rowsDirty) {
            rowsDirty = false;
            int width;
            if (getWidth() > 0) {
                // Already laid out: only child data changed (addView/removeView),
                // the container width is stable, use the actual laid-out width.
                width = getWidth();
            } else {
                // First pass, no layout yet. Use the current frame's size when
                // constrained; otherwise (WRAP_CONTENT chain / scroll view) the
                // spec size is meaningless (huge), so put every child on a
                // single overflowing row to render immediately. The row is then
                // measured with UNSPECIFIED, so children keep their natural
                // width and are never squeezed (which would inflate their
                // height). The layout listener rewraps once the real width is
                // known.
                int mode = MeasureSpec.getMode(widthMeasureSpec);
                width = (mode == MeasureSpec.EXACTLY || mode == MeasureSpec.AT_MOST)
                        ? MeasureSpec.getSize(widthMeasureSpec)
                        : Integer.MAX_VALUE / 2;
            }
            if (width > 0) {
                rebuildRows(width);
            }
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

    @Override
    public void addView(@NotNull View view) {
        allChildren.add(view);
        rowsDirty = true;
        requestLayout();
    }

    /**
     * ListView 等回收复用场景下动画会与列表布局机制冲突导致项定位错乱，应禁用
     */
    public void setAnimationsEnabled(boolean enabled) {
        animationsEnabled = enabled;
        setLayoutTransition(enabled ? defaultTransition : null);
        for (LinearLayout row : rows) {
            row.setLayoutTransition(enabled ? createDefaultTransition() : null);
        }
    }

    private void rebuildRows(int width) {
        List<View> children = List.copyOf(allChildren);
        Set<View> remainingChildren = new HashSet<>(children);
        for (LinearLayout row : rows) {
            for (int i = row.getChildCount() - 1; i >= 0; i--) {
                View child = row.getChildAt(i);
                row.removeView(child);
                if (remainingChildren.contains(child)) {
                    row.endViewTransition(child);
                }
            }
        }
        for (View child : children) {
            child.measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
            int childWidth = child.getMeasuredWidth();
            LinearLayout targetRow = findOrCreateRowForChild(width, childWidth);
            if (child.getParent() != null) {
                ((ViewGroup) child.getParent()).endViewTransition(child);
            }
            targetRow.addView(child);
        }
        List<LinearLayout> rowsToRemove = new ArrayList<>();
        for (LinearLayout row : rows) {
            if (row.getChildCount() == 0) {
                rowsToRemove.add(row);
                super.removeView(row);
                // 行自身也可能被本容器的 LayoutTransition 接管, 立即完成移除
                endViewTransition(row);
            }
        }
        rows.removeAll(rowsToRemove);
    }

    private LinearLayout findOrCreateRowForChild(int width, int childWidth) {
        for (LinearLayout row : rows) {
            int currentRowWidth = calculateRowWidth(row);
            int availableWidth = width - currentRowWidth;
            if (availableWidth >= childWidth) {
                return row;
            }
        }

        return createNewRow();
    }

    private LinearLayout createNewRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        if (animationsEnabled) {
            row.setLayoutTransition(createDefaultTransition());
        }

        var params = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);

        super.addView(row, params);
        rows.add(row);

        return row;
    }

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
