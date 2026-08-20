package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        layoutTransition.enableTransitionType(LayoutTransition.APPEARING);
        layoutTransition.enableTransitionType(LayoutTransition.DISAPPEARING);
        // 禁止 CHANGE_* 动画向上设置到父链(直到窗口): 否则过渡期间布局会被抑制,
        // 且窗口可能在遍历期间被过渡逻辑移除, 导致 WindowGroup.onMeasure 遍历到 null
        layoutTransition.setAnimateParentHierarchy(false);
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

    /**
     * 增量重建行分配: 已在正确行的子元素保留不动, 只有新增/删除/换行时才操作,
     * 避免全量 remove+add 与 LayoutTransition 冲突(被过渡接管的子元素 re-add 会抛
     * "child already has a parent", 也会导致遍历期间容器数组被修改)
     */
    private void rebuildRows(int width) {
        List<View> children = List.copyOf(allChildren);
        // 阶段 A: 纯计算新分配 (现有行视为空行, 只累计本次分配的宽度)
        Map<LinearLayout, Integer> assignedWidths = new HashMap<>();
        Map<View, LinearLayout> targetRows = new HashMap<>();
        Map<View, Integer> childIndexes = new HashMap<>();
        for (int i = 0; i < children.size(); i++) {
            View child = children.get(i);
            childIndexes.put(child, i);
            child.measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
            int childWidth = child.getMeasuredWidth();
            LinearLayout targetRow = findOrCreateRowForChild(width, childWidth, assignedWidths);
            targetRows.put(child, targetRow);
        }

        // 阶段 B: 移除不再存在的子元素(保留淡出动画), 移动的子元素立即完成移除
        Set<View> movedViews = new HashSet<>();
        for (LinearLayout row : rows) {
            for (int i = row.getChildCount() - 1; i >= 0; i--) {
                View child = row.getChildAt(i);
                LinearLayout targetRow = targetRows.get(child);
                if (targetRow == null) {
                    // 真正删除: 保留 DISAPPEARING 淡出动画, 且不会在本轮 re-add
                    row.removeView(child);
                } else if (targetRow != row) {
                    // 换行/移动: 禁用行过渡后移除, 避免启动 DISAPPEARING 动画。
                    // ModernUI 的 runDisappearingTransition 把 View.ALPHA 动画到 0,
                    // 但结束/取消时只恢复 transitionAlpha, 不恢复 alpha, 会导致
                    // 重新加入的子元素永久透明
                    movedViews.add(child);
                    LayoutTransition transition = row.getLayoutTransition();
                    if (transition != null) {
                        row.setLayoutTransition(null);
                    }
                    row.removeView(child);
                    if (transition != null) {
                        row.setLayoutTransition(transition);
                    }
                }
            }
        }

        // 阶段 C: 添加/归位 (按 allChildren 顺序插入, 保证重排后行内顺序不乱)
        for (View child : children) {
            LinearLayout targetRow = targetRows.get(child);
            if (child.getParent() != targetRow) {
                if (child.getParent() != null) {
                    // 防御: 仍被过渡挂住的子元素, 完成其移除
                    ((ViewGroup) child.getParent()).endViewTransition(child);
                }
                int insertIndex = computeInsertIndex(targetRow, child, childIndexes);
                if (movedViews.contains(child)) {
                    // 移动: 无动画加入, 避免重排闪烁
                    LayoutTransition transition = targetRow.getLayoutTransition();
                    if (transition != null) {
                        targetRow.setLayoutTransition(null);
                        targetRow.addView(child, insertIndex);
                        targetRow.setLayoutTransition(transition);
                    } else {
                        targetRow.addView(child, insertIndex);
                    }
                    // 防御: 若过渡动画残留了透明度(ModernUI 取消 DISAPPEARING 后
                    // alpha 可能停在 0), 显式恢复, 否则子元素会不可见
                    child.setAlpha(1f);
                    child.setTransitionAlpha(1f);
                } else {
                    // 新增: APPEARING 淡入动画
                    targetRow.addView(child, insertIndex);
                }
            }
        }

        // 阶段 D: 空行清理
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

    /**
     * 计算子元素在目标行中的插入位置: 按 allChildren 顺序, 插入到第一个
     * 排在其后的已有子元素之前, 保证添加顺序与行内顺序一致
     */
    private static int computeInsertIndex(LinearLayout row, View child,
                                          Map<View, Integer> childIndexes) {
        int childIndex = childIndexes.get(child);
        int count = row.getChildCount();
        for (int i = 0; i < count; i++) {
            Integer existingIndex = childIndexes.get(row.getChildAt(i));
            if (existingIndex != null && existingIndex > childIndex) {
                return i;
            }
        }
        return count;
    }

    private LinearLayout findOrCreateRowForChild(int width, int childWidth,
                                                 Map<LinearLayout, Integer> assignedWidths) {
        for (LinearLayout row : rows) {
            int currentRowWidth = assignedWidths.getOrDefault(row, 0);
            int availableWidth = width - currentRowWidth;
            if (availableWidth >= childWidth) {
                assignedWidths.put(row, currentRowWidth + childWidth);
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
        rows.forEach(row -> row.removeView(view));
        rowsDirty = true;
        requestLayout();
    }
}
