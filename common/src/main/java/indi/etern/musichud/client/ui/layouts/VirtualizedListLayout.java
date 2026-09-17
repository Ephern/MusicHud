package indi.etern.musichud.client.ui.layouts;

import icyllis.modernui.animation.*;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import indi.etern.musichud.client.utils.ui.Easing;
import lombok.Setter;

import java.util.*;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Generic windowing list: only views inside the visible window (plus a small buffer) exist.
 * Items are keyed by the adapter-provided {@code long} id, which must be unique across the
 * whole list; duplicates collapse the index/view maps and corrupt layout.
 */
public class VirtualizedListLayout<T, V extends View> extends FrameLayout {
    private static final long ANIMATION_DURATION = 400;

    public interface Adapter<T, V extends View> {
        long idOf(T item);

        V createItem(ViewGroup parent);

        void clearItem(V view);

        void bindItem(V view, T item);

        /** Id currently bound to {@code view}, or {@code -1} when unbound. Used for height caching. */
        long boundIdOf(V view);
    }

    private final Adapter<T, V> adapter;
    private List<T> items = List.of();
    private Map<Long, Integer> indexById = Map.of();
    private final Map<Long, V> activeViews = new HashMap<>();
    private final Deque<V> viewPool = new ArrayDeque<>();
    private final Map<Long, Integer> heightByItemId = new HashMap<>();
    private final List<PendingRemoval<V>> pendingRemovals = new ArrayList<>();
    private final Map<Long, AnimatorSet> runningAnimations = new HashMap<>();
    private final Deque<V> pendingRecycle = new ArrayDeque<>();
    private boolean recyclePosted;
    private int scrollY;
    private int viewportHeight;
    @Setter
    private int defaultItemHeight;

    private record PendingRemoval<V extends View>(long id, V view, int index) {
    }

    public VirtualizedListLayout(Context context, Adapter<T, V> adapter) {
        super(context);
        this.adapter = adapter;
        defaultItemHeight = dp(64);
    }

    public void resetItems(List<T> newItems) {
        cancelAllAnimations();
        pendingRemovals.clear();
        for (V view : activeViews.values()) {
            removeView(view);
        }
        while (!pendingRecycle.isEmpty()) {
            V view = pendingRecycle.poll();
            if (view.getParent() != null) {
                removeView(view);
            }
        }
        activeViews.clear();
        viewPool.clear();
        heightByItemId.clear();
        items = filterNull(newItems);
        indexById = rebuildIndex(items);
        rebuildWindow();
    }

    public void syncItems(List<T> newItems) {
        Map<Long, Integer> oldIndex = indexById;
        List<T> cleanItems = filterNull(newItems);
        Map<Long, Integer> newIndex = rebuildIndex(cleanItems);
        for (long id : oldIndex.keySet()) {
            if (!newIndex.containsKey(id)) {
                removeItem(id, oldIndex.get(id));
            }
        }
        items = cleanItems;
        indexById = newIndex;
        for (long id : newIndex.keySet()) {
            if (!oldIndex.containsKey(id)) {
                addItem(id, newIndex.get(id));
            }
        }
        rebuildWindow();
    }

    /**
     * Replaces item data and rebinds already-visible views in place, without touching the
     * window/recycle machinery. Use this for state/progress updates that keep the same ids;
     * it avoids the recycling churn of {@link #syncItems}.
     */
    public void updateItems(List<T> newItems) {
        List<T> cleanItems = filterNull(newItems);
        items = cleanItems;
        indexById = rebuildIndex(cleanItems);
        for (Map.Entry<Long, V> entry : activeViews.entrySet()) {
            Integer index = indexById.get(entry.getKey());
            if (index != null) {
                adapter.bindItem(entry.getValue(), cleanItems.get(index));
            }
        }
        requestLayout();
    }

    public void updateWindow(int scrollY, int viewportHeight) {
        this.scrollY = scrollY;
        this.viewportHeight = viewportHeight;
        rebuildWindow();
    }

    /**
     * Rebinds the active view of {@code id} in place (state/progress updates), if visible.
     * Does not clear first, so state updates don't trigger appear/disappear animations.
     */
    public void notifyItemChanged(long id) {
        V view = activeViews.get(id);
        Integer index = indexById.get(id);
        if (view == null || index == null) {
            return;
        }
        adapter.bindItem(view, items.get(index));
        heightByItemId.remove(id);
        requestLayout();
    }

    private List<T> filterNull(List<T> list) {
        return list.stream().filter(Objects::nonNull).toList();
    }

    private Map<Long, Integer> rebuildIndex(List<T> list) {
        Map<Long, Integer> map = new HashMap<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            T item = list.get(i);
            if (item != null) {
                map.put(adapter.idOf(item), i);
            }
        }
        return map;
    }

    private void rebuildWindow() {
        if (items.isEmpty()) {
            return;
        }
        List<Long> ids = layoutIds();
        int buffer = defaultItemHeight * 2;
        int top = scrollY - buffer;
        int bottom = scrollY + viewportHeight + buffer;

        int start = 0;
        int acc = 0;
        for (int i = 0; i < ids.size(); i++) {
            int h = layoutHeight(ids.get(i));
            if (acc + h > top) {
                start = i;
                break;
            }
            acc += h;
        }
        int end = ids.size() - 1;
        acc = 0;
        for (int i = 0; i < ids.size(); i++) {
            acc += layoutHeight(ids.get(i));
            if (acc >= bottom) {
                end = i;
                break;
            }
        }

        Set<Long> needed = new HashSet<>();
        for (int i = start; i <= end; i++) {
            needed.add(ids.get(i));
        }
        for (var it = activeViews.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (!needed.contains(entry.getKey()) && !runningAnimations.containsKey(entry.getKey())) {
                recycleView(entry.getValue());
                it.remove();
            }
        }
        for (long id : needed) {
            if (!activeViews.containsKey(id) && !isPendingRemoval(id)) {
                activeViews.put(id, obtainView(id));
            }
        }
        requestLayout();
    }

    private boolean isPendingRemoval(long id) {
        for (PendingRemoval<V> pr : pendingRemovals) {
            if (pr.id() == id) {
                return true;
            }
        }
        return false;
    }

    private V obtainView(long id) {
        Integer idx = indexById.get(id);
        T data = idx != null ? items.get(idx) : null;
        V view = pendingRecycle.pollFirst();
        if (view == null) {
            view = viewPool.poll();
            if (view == null) {
                view = adapter.createItem(this);
            } else {
                adapter.clearItem(view);
            }
            addView(view, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        } else {
            adapter.clearItem(view);
        }
        if (data != null) {
            adapter.bindItem(view, data);
        }
        view.setAlpha(1f);
        return view;
    }

    /**
     * Defers recycling: removes the view on a separate post to avoid nested removeView while
     * scrolling/layout/drawing (which can break tooltip teardown).
     */
    private void recycleView(V view) {
        pendingRecycle.add(view);
        if (!recyclePosted) {
            recyclePosted = true;
            post(this::flushPendingRecycle);
        }
    }

    private void flushPendingRecycle() {
        recyclePosted = false;
        V view;
        while ((view = pendingRecycle.poll()) != null) {
            if (view.getParent() != null) {
                removeView(view);
            }
            viewPool.add(view);
        }
    }

    private void removeItem(long id, int index) {
        V view = activeViews.remove(id);
        if (view == null) {
            heightByItemId.remove(id);
            return;
        }
        AnimatorSet running = runningAnimations.remove(id);
        if (running != null) {
            running.cancel();
            heightByItemId.remove(id);
            return;
        }
        pendingRemovals.add(new PendingRemoval<>(id, view, index));
        pendingRemovals.sort(Comparator.comparingInt(PendingRemoval::index));
        animateRemoval(id, view);
    }

    private void addItem(long id, int index) {
        if (!isIndexInWindow(index) || isPendingRemoval(id) || activeViews.containsKey(id)) {
            return;
        }
        T data = items.get(index);
        V view = viewPool.poll();
        if (view == null) {
            view = adapter.createItem(this);
        } else {
            adapter.clearItem(view);
        }
        adapter.bindItem(view, data);
        view.setAlpha(0f);
        int width = getWidth();
        if (width > 0) {
            view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        } else {
            view.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        }
        int targetHeight = view.getMeasuredHeight() > 0 ? view.getMeasuredHeight() : defaultItemHeight;
        heightByItemId.put(id, targetHeight);
        addView(view, new FrameLayout.LayoutParams(MATCH_PARENT, 0));
        activeViews.put(id, view);
        animateInsertion(id, view, targetHeight);
    }

    private boolean isIndexInWindow(int index) {
        int buffer = defaultItemHeight * 2;
        int top = scrollY - buffer;
        int bottom = scrollY + viewportHeight + buffer;
        int acc = 0;
        for (int i = 0; i < items.size(); i++) {
            int h = layoutHeight(adapter.idOf(items.get(i)));
            if (i == index) {
                return acc + h > top && acc < bottom;
            }
            acc += h;
        }
        return false;
    }

    private void animateInsertion(long id, V view, int targetHeight) {
        ValueAnimator heightAnim = ValueAnimator.ofFloat(0, targetHeight);
        heightAnim.setDuration(ANIMATION_DURATION);
        heightAnim.setInterpolator(Easing.EASE_IN_OUT_QUINT);
        heightAnim.addUpdateListener(anim -> {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
            lp.height = Math.round((float) anim.getAnimatedValue());
            view.setLayoutParams(lp);
        });
        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, 1f);
        alphaAnim.setDuration(ANIMATION_DURATION);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(heightAnim, alphaAnim);
        set.addListener(new AnimatorListener() {
            @Override
            public void onAnimationCancel(@NonNull Animator animation) {
                recycleViewIfAttached(view);
            }

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                runningAnimations.remove(id);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
                lp.height = WRAP_CONTENT;
                view.setLayoutParams(lp);
                view.setAlpha(1f);
                heightByItemId.put(id, targetHeight);
                requestLayout();
            }
        });
        runningAnimations.put(id, set);
        set.start();
    }

    private void animateRemoval(long id, V view) {
        int start = heightByItemId.getOrDefault(id, defaultItemHeight);
        ValueAnimator heightAnim = ValueAnimator.ofFloat(start, 0);
        heightAnim.setDuration(ANIMATION_DURATION);
        heightAnim.setInterpolator(Easing.EASE_IN_OUT_QUINT);
        heightAnim.addUpdateListener(anim -> {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
            lp.height = Math.round((float) anim.getAnimatedValue());
            view.setLayoutParams(lp);
        });
        ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(view, View.ALPHA, 0f);
        alphaAnim.setDuration(ANIMATION_DURATION);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(heightAnim, alphaAnim);
        set.addListener(new AnimatorListener() {
            @Override
            public void onAnimationCancel(@NonNull Animator animation) {
                recycleViewIfAttached(view);
            }

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                runningAnimations.remove(id);
                pendingRemovals.removeIf(pr -> pr.id() == id);
                heightByItemId.remove(id);
                recycleViewIfAttached(view);
                requestLayout();
            }
        });
        runningAnimations.put(id, set);
        set.start();
    }

    private void recycleViewIfAttached(V view) {
        if (view.getParent() != null) {
            recycleView(view);
        }
    }

    private List<Long> layoutIds() {
        List<Long> ids = new ArrayList<>(items.size() + pendingRemovals.size());
        int pendingIdx = 0;
        for (int i = 0; i < items.size(); i++) {
            while (pendingIdx < pendingRemovals.size() && pendingRemovals.get(pendingIdx).index() <= i) {
                ids.add(pendingRemovals.get(pendingIdx++).id());
            }
            ids.add(adapter.idOf(items.get(i)));
        }
        while (pendingIdx < pendingRemovals.size()) {
            ids.add(pendingRemovals.get(pendingIdx++).id());
        }
        return ids;
    }

    private V viewForId(long id) {
        V view = activeViews.get(id);
        if (view != null) {
            return view;
        }
        for (PendingRemoval<V> pr : pendingRemovals) {
            if (pr.id() == id) {
                return pr.view();
            }
        }
        return null;
    }

    private int layoutHeight(long id) {
        V view = viewForId(id);
        if (view != null && view.getLayoutParams() != null && runningAnimations.containsKey(id)) {
            return view.getLayoutParams().height;
        }
        return heightByItemId.getOrDefault(id, defaultItemHeight);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        for (V view : activeViews.values()) {
            long id = adapter.boundIdOf(view);
            if (id >= 0 && !runningAnimations.containsKey(id) && view.getMeasuredHeight() > 0) {
                heightByItemId.put(id, view.getMeasuredHeight());
            }
        }
        int total = 0;
        for (long id : layoutIds()) {
            total += layoutHeight(id);
        }
        setMeasuredDimension(getMeasuredWidth(), total);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        // Collapse any attached child that is not part of the current layout set. Views queued
        // for deferred recycling would otherwise keep their previous bounds and linger as a
        // stray, un-laid-out box.
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).layout(0, 0, 0, 0);
        }
        int y = 0;
        for (long id : layoutIds()) {
            int h = layoutHeight(id);
            V view = viewForId(id);
            if (view != null) {
                view.layout(0, y, width, y + h);
            }
            y += h;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelAllAnimations();
        pendingRemovals.clear();
        pendingRecycle.clear();
        activeViews.clear();
        viewPool.clear();
        heightByItemId.clear();
        // Detach children too: clearing the maps alone leaves them attached at stale bounds.
        removeAllViews();
    }

    private void cancelAllAnimations() {
        for (AnimatorSet animator : new ArrayList<>(runningAnimations.values())) {
            animator.cancel();
        }
        runningAnimations.clear();
    }
}
