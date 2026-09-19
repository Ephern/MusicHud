package indi.etern.musichud.client.ui.layouts;

import icyllis.modernui.animation.Animator;
import icyllis.modernui.animation.AnimatorListener;
import icyllis.modernui.animation.AnimatorSet;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import indi.etern.musichud.client.utils.ui.SpringInterpolator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Single-pass flow layout. Children are measured naturally, allocated into lines with a
 * first-fit pass, then width/weight resolved per line like a horizontal LinearLayout.
 * No nested rows or reparenting, so the first layout pass is already correct.
 * <p>
 * Reflow animates the children's actual layout frames (no translation), so the drawn
 * position always has a single source of truth.
 */
public class FlexWrapLayout extends ViewGroup {
    private static final SpringInterpolator SPRING = new SpringInterpolator(0.27f, 1f);
    private static final long ANIM_DURATION_MS = (long) (SPRING.getDuration() * 1000);
    private static final int APPEAR_OFFSET_DP = 4;
    private static final int DISAPPEAR_OFFSET_DP = 4;
    // Layouts arriving at frame rate are dynamic (e.g. an animated width): those reflow
    // instantly instead of restarting an animation every frame.
    private static final long CONTINUOUS_LAYOUT_WINDOW_MS = 48;

    private final List<Line> lines = new ArrayList<>();
    private final Set<View> laidOutChildren = new HashSet<>();
    private final Set<View> disappearingChildren = new HashSet<>();
    private final Map<View, Animator> runningAnimations = new HashMap<>();
    private final Map<View, Integer> lastLineIndex = new HashMap<>();
    private int lineGravity = Gravity.TOP;
    private boolean animationsEnabled = true;
    private boolean firstLayout = true;
    private long lastLayoutTime;

    private static final class Item {
        final View child;
        final ViewGroup.MarginLayoutParams lp;
        final int naturalWidth;
        int width;
        int height;

        Item(View child, ViewGroup.MarginLayoutParams lp, int width, int height) {
            this.child = child;
            this.lp = lp;
            this.naturalWidth = width;
            this.width = width;
            this.height = height;
        }
    }

    private static final class Line {
        final List<Item> items = new ArrayList<>();
        int fitWidth;
        int top;
        int width;
        int height;
    }

    public FlexWrapLayout(Context context) {
        super(context);
        // Ghost children may briefly draw outside the shrunken bounds while fading out.
        setClipChildren(false);
    }

    public void setLineGravity(int gravity) {
        lineGravity = gravity;
        requestLayout();
    }

    /**
     * Disable for recycled list items: animations conflict with list-driven positioning.
     */
    public void setAnimationsEnabled(boolean enabled) {
        animationsEnabled = enabled;
        if (!enabled) {
            cancelAllAnimations();
            finishAllDisappearing();
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                child.setTranslationX(0f);
                child.setTranslationY(0f);
                child.setAlpha(1f);
            }
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int available = widthMode == MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE
                : Math.max(0, widthSize - paddingLeft - paddingRight);

        lines.clear();
        int keepTolerance = dp(1);
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE || disappearingChildren.contains(child)) {
                continue;
            }
            measureNatural(child);
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
            Item item = new Item(child, lp, child.getMeasuredWidth(), child.getMeasuredHeight());
            int outerWidth = item.width + lp.leftMargin + lp.rightMargin;
            // First-fit, but a child keeps its previous line while it overflows only within
            // keepTolerance, so wrapping does not flap around the fit boundary.
            Line target = null;
            for (Line line : lines) {
                if (line.fitWidth == 0 || available - line.fitWidth >= outerWidth) {
                    target = line;
                    break;
                }
            }
            if (target == null) {
                Integer previous = lastLineIndex.get(child);
                if (previous != null && previous >= 0 && previous < lines.size()) {
                    Line line = lines.get(previous);
                    if (line.fitWidth == 0 || available - line.fitWidth >= outerWidth - keepTolerance) {
                        target = line;
                    }
                }
            }
            if (target == null) {
                target = new Line();
                lines.add(target);
            }
            target.items.add(item);
            target.fitWidth += outerWidth;
        }

        int maxLineWidth = 0;
        int resolveAvailable = available;
        if (widthMode == MeasureSpec.UNSPECIFIED) {
            // Without a width constraint, weighted/MATCH_PARENT children must not expand.
            resolveAvailable = 0;
            for (Line line : lines) {
                resolveAvailable = Math.max(resolveAvailable, line.fitWidth);
            }
        }
        int y = getPaddingTop();
        for (Line line : lines) {
            resolveLine(line, resolveAvailable);
            line.top = y;
            y += line.height;
            maxLineWidth = Math.max(maxLineWidth, line.width);
        }
        int measuredWidth = Math.max(maxLineWidth + paddingLeft + paddingRight, getSuggestedMinimumWidth());
        int measuredHeight = Math.max(y + getPaddingBottom(), getSuggestedMinimumHeight());
        setMeasuredDimension(
                resolveSize(measuredWidth, widthMeasureSpec),
                resolveSize(measuredHeight, heightMeasureSpec)
        );
    }

    private static void measureNatural(View child) {
        child.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        );
    }

    /**
     * Resolves final widths for one line: non-weighted children keep fixed/natural width
     * (MATCH_PARENT fills the remainder), then the leftover is split among weighted children.
     * Children whose final width differs from their natural width are re-measured so their
     * height reflects the constraint.
     */
    private static void resolveLine(Line line, int available) {
        float totalWeight = 0f;
        int used = 0;
        for (Item item : line.items) {
            if (isWeighted(item.lp)) {
                totalWeight += weightOf(item.lp);
                continue;
            }
            int room = Math.max(0, available - used - item.lp.leftMargin - item.lp.rightMargin);
            int resolved;
            if (item.lp.width >= 0) {
                resolved = item.lp.width;
            } else if (item.lp.width == MATCH_PARENT) {
                resolved = room;
            } else {
                resolved = Math.min(item.width, room);
            }
            item.width = resolved;
            used += resolved + item.lp.leftMargin + item.lp.rightMargin;
        }
        if (totalWeight > 0f) {
            int remaining = Math.max(0, available - used);
            for (Item item : line.items) {
                if (!isWeighted(item.lp)) {
                    continue;
                }
                int share = (int) (remaining * (weightOf(item.lp) / totalWeight));
                item.width = Math.max(item.lp.width, 0) + share;
                used += item.width + item.lp.leftMargin + item.lp.rightMargin;
            }
        }

        for (Item item : line.items) {
            if (item.width != item.naturalWidth) {
                int heightSpec = item.lp.height >= 0
                        ? MeasureSpec.makeMeasureSpec(item.lp.height, MeasureSpec.EXACTLY)
                        : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                item.child.measure(
                        MeasureSpec.makeMeasureSpec(item.width, MeasureSpec.EXACTLY),
                        heightSpec);
                item.height = item.child.getMeasuredHeight();
            }
        }

        int lineHeight = 0;
        for (Item item : line.items) {
            if (item.lp.height != MATCH_PARENT) {
                lineHeight = Math.max(lineHeight, item.height + item.lp.topMargin + item.lp.bottomMargin);
            }
        }
        for (Item item : line.items) {
            if (item.lp.height == MATCH_PARENT) {
                item.height = Math.max(0, lineHeight - item.lp.topMargin - item.lp.bottomMargin);
            }
        }
        line.height = lineHeight;
        line.width = used;
    }

    private static boolean isWeighted(ViewGroup.MarginLayoutParams lp) {
        return weightOf(lp) > 0f;
    }

    private static float weightOf(ViewGroup.MarginLayoutParams lp) {
        return lp instanceof LinearLayout.LayoutParams linear ? linear.weight : 0f;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int contentWidth = right - left - paddingLeft - getPaddingRight();
        boolean animate = animationsEnabled && !firstLayout;
        long now = Core.timeMillis();
        boolean continuousLayout = animate && lastLayoutTime != 0
                && now - lastLayoutTime <= CONTINUOUS_LAYOUT_WINDOW_MS;
        lastLayoutTime = now;

        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            Line line = lines.get(lineIndex);
            int lineTop = paddingTop + line.top;
            int hGravity = lineGravity & Gravity.HORIZONTAL_GRAVITY_MASK;
            int x = paddingLeft;
            if (hGravity == Gravity.CENTER_HORIZONTAL) {
                x += (contentWidth - line.width) / 2;
            } else if (hGravity == Gravity.RIGHT) {
                x += contentWidth - line.width;
            }
            for (Item item : line.items) {
                View child = item.child;
                if (child.getVisibility() == GONE || disappearingChildren.contains(child)) {
                    continue;
                }
                int childLeft = x + item.lp.leftMargin;
                int childTop = lineTop + verticalOffset(item.height, line.height, item.lp);
                boolean sizeChanged = item.width != child.getWidth() || item.height != child.getHeight();
                Integer previousLine = lastLineIndex.put(child, lineIndex);
                boolean lineChanged = previousLine != null && previousLine != lineIndex;
                animateAfterLayout(child, childLeft, childTop, item.width, item.height,
                        animate, continuousLayout, sizeChanged, lineChanged);
                x += item.width + item.lp.leftMargin + item.lp.rightMargin;
            }
        }
        firstLayout = false;
    }

    private int verticalOffset(int childHeight, int lineHeight, ViewGroup.MarginLayoutParams lp) {
        int vGravity = lineGravity & Gravity.VERTICAL_GRAVITY_MASK;
        if (vGravity == Gravity.BOTTOM) {
            return lineHeight - childHeight - lp.bottomMargin;
        }
        if (vGravity == Gravity.CENTER_VERTICAL) {
            return lp.topMargin + (lineHeight - childHeight - lp.topMargin - lp.bottomMargin) / 2;
        }
        return lp.topMargin;
    }

    private void animateAfterLayout(View child, int toLeft, int toTop, int width, int height,
                                    boolean animate, boolean continuousLayout,
                                    boolean sizeChanged, boolean lineChanged) {
        int toRight = toLeft + width;
        int toBottom = toTop + height;
        if (!laidOutChildren.contains(child)) {
            laidOutChildren.add(child);
            child.layout(toLeft, toTop, toRight, toBottom);
            if (animate) {
                child.setAlpha(0f);
                child.setTranslationY(dp(APPEAR_OFFSET_DP));
                runAppear(child);
            }
            return;
        }
        boolean positionChanged = child.getLeft() != toLeft || child.getTop() != toTop;
        // First layout, animations disabled, a size animation already driven by layout, or a
        // dynamic frame-rate layout (e.g. an animated width): snap to the final frame.
        if (!animate || (sizeChanged && !lineChanged) || (continuousLayout && positionChanged)) {
            cancelAnimation(child);
            child.setTranslationX(0f);
            child.setTranslationY(0f);
            child.setAlpha(1f);
            child.layout(toLeft, toTop, toRight, toBottom);
            return;
        }
        if (!positionChanged) {
            child.layout(toLeft, toTop, toRight, toBottom);
            return;
        }
        startReflow(child, child.getLeft(), child.getTop(), toLeft, toTop, width, height);
    }

    /**
     * Animates the child's actual frame from its current position to the target. Using the
     * layout frame (not translation) keeps the drawn position single-sourced and continuous;
     * a new layout change simply restarts from the current frame.
     */
    private void startReflow(View child, int fromLeft, int fromTop, int toLeft, int toTop,
                             int width, int height) {
        cancelAnimation(child);
        child.setTranslationX(0f);
        child.setTranslationY(0f);
        child.setAlpha(1f);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(ANIM_DURATION_MS);
        animator.setInterpolator(SPRING);
        animator.addUpdateListener(a -> {
            float fraction = a.getAnimatedFraction();
            int l = Math.round(fromLeft + (toLeft - fromLeft) * fraction);
            int t = Math.round(fromTop + (toTop - fromTop) * fraction);
            child.layout(l, t, l + width, t + height);
        });
        runningAnimations.put(child, animator);
        animator.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                if (runningAnimations.get(child) == animator) {
                    runningAnimations.remove(child);
                    child.layout(toLeft, toTop, toLeft + width, toTop + height);
                }
            }
        });
        animator.start();
    }

    private void runAppear(View child) {
        cancelAnimation(child);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(child, View.ALPHA, 1f);
        ObjectAnimator translationY = ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, 0f);
        alpha.setDuration(ANIM_DURATION_MS);
        alpha.setInterpolator(SPRING);
        translationY.setDuration(ANIM_DURATION_MS);
        translationY.setInterpolator(SPRING);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(alpha, translationY);
        runningAnimations.put(child, set);
        set.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                if (runningAnimations.get(child) == set) {
                    runningAnimations.remove(child);
                }
            }
        });
        set.start();
    }

    private void cancelAnimation(View child) {
        Animator existing = runningAnimations.remove(child);
        if (existing != null) {
            existing.cancel();
        }
    }

    @Override
    public void removeView(@NotNull View view) {
        if (animationsEnabled && !firstLayout && laidOutChildren.contains(view)
                && view.getVisibility() == VISIBLE && view.getParent() == this
                && !disappearingChildren.contains(view)) {
            startDisappear(view);
            return;
        }
        cancelAnimation(view);
        laidOutChildren.remove(view);
        disappearingChildren.remove(view);
        lastLineIndex.remove(view);
        super.removeView(view);
    }

    private void startDisappear(View view) {
        disappearingChildren.add(view);
        cancelAnimation(view);
        view.setAlpha(1f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0f);
        ObjectAnimator translationY = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -dp(DISAPPEAR_OFFSET_DP));
        alpha.setDuration(ANIM_DURATION_MS);
        alpha.setInterpolator(SPRING);
        translationY.setDuration(ANIM_DURATION_MS);
        translationY.setInterpolator(SPRING);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(alpha, translationY);
        runningAnimations.put(view, set);
        set.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                if (runningAnimations.get(view) != set) {
                    return;
                }
                runningAnimations.remove(view);
                detachGhost(view);
            }
        });
        set.start();
        requestLayout();
        invalidate();
    }

    private void detachGhost(View view) {
        disappearingChildren.remove(view);
        laidOutChildren.remove(view);
        lastLineIndex.remove(view);
        view.setAlpha(1f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        if (view.getParent() == this) {
            super.removeView(view);
        }
    }

    private void finishAllDisappearing() {
        for (View child : new ArrayList<>(disappearingChildren)) {
            detachGhost(child);
        }
        disappearingChildren.clear();
    }

    private void cancelAllAnimations() {
        for (Animator animator : new ArrayList<>(runningAnimations.values())) {
            animator.cancel();
        }
        runningAnimations.clear();
    }

    @Override
    public void removeAllViews() {
        cancelAllAnimations();
        finishAllDisappearing();
        laidOutChildren.clear();
        lastLineIndex.clear();
        super.removeAllViews();
    }

    @Override
    public void addView(@NotNull View child, int index, @NotNull ViewGroup.LayoutParams params) {
        if (disappearingChildren.contains(child)) {
            cancelAnimation(child);
            disappearingChildren.remove(child);
            laidOutChildren.remove(child);
            lastLineIndex.remove(child);
            child.setAlpha(1f);
            child.setTranslationX(0f);
            child.setTranslationY(0f);
            if (child.getParent() == this) {
                super.removeView(child);
            }
        }
        super.addView(child, index, params);
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAllAnimations();
        finishAllDisappearing();
        // Cancel leaves the current value; clear residual so a reattach lays out cleanly.
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.setTranslationX(0f);
            child.setTranslationY(0f);
            child.setAlpha(1f);
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected @NonNull ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new ViewGroup.MarginLayoutParams(WRAP_CONTENT, WRAP_CONTENT);
    }

    @Override
    protected @NonNull ViewGroup.LayoutParams generateLayoutParams(@NonNull ViewGroup.LayoutParams params) {
        return new ViewGroup.MarginLayoutParams(params);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams params) {
        return params instanceof ViewGroup.MarginLayoutParams;
    }
}
