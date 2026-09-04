package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import indi.etern.musichud.interfaces.Unregister;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class FlexWrapLayout extends LinearLayout {
    final List<View> allChildren = new ArrayList<>();
    private final List<LinearLayout> rows = new ArrayList<>();
    private boolean rowsDirty = true;
    private final LayoutTransition defaultTransition = createDefaultTransition();
    private boolean animationsEnabled = true;
    private final Map<View, Integer> childWidthSnapshot = new HashMap<>();
    private int lastRebuildWidth = -1;
    private LinkedHashSet<Consumer<LinearLayout>> lineStylers;
    private boolean rebuildPending = false;

    private static LayoutTransition createDefaultTransition() {
        LayoutTransition layoutTransition = new LayoutTransition();
        layoutTransition.setDuration(300);
        layoutTransition.enableTransitionType(LayoutTransition.APPEARING);
        layoutTransition.enableTransitionType(LayoutTransition.DISAPPEARING);
        layoutTransition.setAnimateParentHierarchy(false);
        return layoutTransition;
    }

    public FlexWrapLayout(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setLayoutTransition(defaultTransition);
        addOnLayoutChangeListener((v1, left, top, right, bottom,
                                   oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft && right - left > 0) {
                scheduleRebuild();
            }
        });
    }

    /**
     * Structural rebuilds must never run inside measure/layout: the framework
     * closes windows (e.g. tooltips) synchronously from detach callbacks, and
     * WindowGroup.onMeasure iterates windows with a stale count, so any child
     * removal that races that iteration crashes with an NPE. Rebuilds are
     * therefore deferred to the next UI message.
     */
    private void scheduleRebuild() {
        if (rebuildPending) {
            return;
        }
        rebuildPending = true;
        MuiModApi.postToUiThread(() -> {
            rebuildPending = false;
            int width = getWidth() > 0 ? getWidth() : lastRebuildWidth;
            if (width > 0) {
                rebuildRows(width);
                lastRebuildWidth = width;
                updateChildWidthSnapshot();
                rowsDirty = false;
                requestLayout();
            }
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        boolean childSizesChanged = detectChildSizeChanges();
        if (rowsDirty || childSizesChanged
                || (getWidth() > 0 && getWidth() != lastRebuildWidth)) {
            scheduleRebuild();
        }
        // Measured with the current (possibly one frame stale) row structure;
        // the deferred rebuild triggers another pass once it applies.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * Detect whether any child's natural width has changed since the last
     * rebuild. Only children that requested a layout are re-measured, which
     * keeps this cheap for large fixed-size children (e.g. cards in a list).
     */
    private boolean detectChildSizeChanges() {
        if (childWidthSnapshot.size() != allChildren.size()) {
            return true;
        }
        for (View child : allChildren) {
            if (!child.isLayoutRequested()) {
                continue;
            }
            int naturalWidth = measureChildWidth(child);
            Integer last = childWidthSnapshot.get(child);
            if (last == null || last != naturalWidth) {
                return true;
            }
        }
        return false;
    }

    /**
     * Measure a child with no width constraint and return its natural width
     * including horizontal margins, matching what a horizontal row will consume.
     */
    private int measureChildWidth(View child) {
        child.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        );
        int childWidth = child.getMeasuredWidth();
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams margins) {
            childWidth += margins.leftMargin + margins.rightMargin;
        }
        return childWidth;
    }

    private void updateChildWidthSnapshot() {
        childWidthSnapshot.clear();
        for (View child : allChildren) {
            childWidthSnapshot.put(child, measureChildWidth(child));
        }
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
        scheduleRebuild();
    }

    @Override
    public void addView(@NonNull View view, @NonNull ViewGroup.LayoutParams params) {
        view.setLayoutParams(params);
        allChildren.add(view);
        rowsDirty = true;
        scheduleRebuild();
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
        // Runs as an independent UI message (see scheduleRebuild), never inside
        // measure/layout, so layout transitions here cannot re-enter window
        // iteration in WindowGroup.onMeasure
        List<View> children = List.copyOf(allChildren);
        WrapLineAllocator allocator = new WrapLineAllocator(width);
        Map<View, LinearLayout> targetRows = new HashMap<>();
        Map<View, Integer> childIndexes = new HashMap<>();
        for (int i = 0; i < children.size(); i++) {
            View child = children.get(i);
            childIndexes.put(child, i);
            int childWidth = measureChildWidth(child);
            LinearLayout targetRow = findOrCreateRowForChild(width, childWidth, allocator);
            targetRows.put(child, targetRow);
        }

        Set<View> movedViews = new HashSet<>();
        for (LinearLayout row : rows) {
            for (int i = row.getChildCount() - 1; i >= 0; i--) {
                View child = row.getChildAt(i);
                LinearLayout targetRow = targetRows.get(child);
                if (targetRow == null) {
                    row.removeView(child);
                } else if (targetRow != row) {
                    movedViews.add(child);
                    // 移动期间禁动画, 避免重排闪烁
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

        for (View child : children) {
            LinearLayout targetRow = targetRows.get(child);
            if (child.getParent() != targetRow) {
                int insertIndex = computeInsertIndex(targetRow, child, childIndexes);
                if (movedViews.contains(child)) {
                    LayoutTransition transition = targetRow.getLayoutTransition();
                    if (transition != null) {
                        targetRow.setLayoutTransition(null);
                        targetRow.addView(child, insertIndex);
                        targetRow.setLayoutTransition(transition);
                    } else {
                        targetRow.addView(child, insertIndex);
                    }
                    child.setAlpha(1f);
                    child.setTransitionAlpha(1f);
                } else {
                    targetRow.addView(child, insertIndex);
                }
            }
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
                                                 WrapLineAllocator allocator) {
        int rowIndex = allocator.allocate(childWidth);
        while (rows.size() <= rowIndex) {
            createNewRow();
        }
        return rows.get(rowIndex);
    }

    private void createNewRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        if (animationsEnabled) {
            row.setLayoutTransition(createDefaultTransition());
        }

        var params = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        row.setLayoutParams(params);
        if (lineStylers != null && !lineStylers.isEmpty()) {
            lineStylers.forEach(styler -> styler.accept(row));
        }
        super.addView(row);
        rows.add(row);
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
        childWidthSnapshot.remove(view);
        // Physical removal from rows happens in the deferred rebuildRows
        rowsDirty = true;
        scheduleRebuild();
    }

    public Unregister applyLineStyle(Consumer<LinearLayout> styler) {
        if (lineStylers == null) {
            lineStylers = new LinkedHashSet<>();
        }
        lineStylers.add(styler);
        return () -> lineStylers.remove(styler);
    }
}
