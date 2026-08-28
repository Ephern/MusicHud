package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.*;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.utils.ui.Easing;
import lombok.Setter;

import java.util.*;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class VirtualizedListLayout extends FrameLayout {
    private static final long ANIMATION_DURATION = 400;

    private List<MusicDetail> items = List.of();
    private Map<Long, Integer> indexById = Map.of();
    private final Map<Long, MusicListItem> activeViews = new HashMap<>();
    private final Deque<MusicListItem> viewPool = new ArrayDeque<>();
    private final Map<Long, Integer> heightByItemId = new HashMap<>();
    private final List<PendingRemoval> pendingRemovals = new ArrayList<>();
    private final Map<Long, AnimatorSet> runningAnimations = new HashMap<>();
    private final Deque<MusicListItem> pendingRecycle = new ArrayDeque<>();
    private boolean recyclePosted;
    private int scrollY;
    private int viewportHeight;
    @Setter
    private int defaultItemHeight;

    private record PendingRemoval(long id, MusicListItem view, int index) {
    }

    public VirtualizedListLayout(Context context) {
        super(context);
        defaultItemHeight = dp(64);
    }

    public void resetItems(List<MusicDetail> newItems) {
        cancelAllAnimations();
        pendingRemovals.clear();
        for (MusicListItem view : activeViews.values()) {
            removeView(view);
        }
        while (!pendingRecycle.isEmpty()) {
            MusicListItem view = pendingRecycle.poll();
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

    public void syncItems(List<MusicDetail> newItems) {
        Map<Long, Integer> oldIndex = indexById;
        List<MusicDetail> cleanItems = filterNull(newItems);
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

    public void updateWindow(int scrollY, int viewportHeight) {
        this.scrollY = scrollY;
        this.viewportHeight = viewportHeight;
        rebuildWindow();
    }

    private static Map<Long, Integer> rebuildIndex(List<MusicDetail> list) {
        Map<Long, Integer> map = new HashMap<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            MusicDetail item = list.get(i);
            if (item != null) {
                map.put(item.getId(), i);
            }
        }
        return map;
    }

    private static List<MusicDetail> filterNull(List<MusicDetail> list) {
        return list.stream().filter(Objects::nonNull).toList();
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
        for (PendingRemoval pr : pendingRemovals) {
            if (pr.id() == id) {
                return true;
            }
        }
        return false;
    }

    private MusicListItem obtainView(long id) {
        Integer idx = indexById.get(id);
        MusicDetail data = idx != null ? items.get(idx) : null;
        MusicListItem view = pendingRecycle.pollFirst();
        if (view == null) {
            view = viewPool.poll();
            if (view == null) {
                view = MusicListFactory.createItem(this);
            } else {
                view.clearData();
            }
            addView(view, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        } else {
            view.clearData();
        }
        if (data != null) {
            view.bindData(data);
        }
        view.setAlpha(1f);
        return view;
    }

    /**
     * 延迟回收: 视图加入待回收队列, 在消息循环独立时机 (post) 执行 removeView,
     * 避免在滚动/布局/绘制遍历中触发 detach -> tooltip 隐藏的嵌套 removeView。
     */
    private void recycleView(MusicListItem view) {
        pendingRecycle.add(view);
        if (!recyclePosted) {
            recyclePosted = true;
            post(this::flushPendingRecycle);
        }
    }

    private void flushPendingRecycle() {
        recyclePosted = false;
        MusicListItem view;
        while ((view = pendingRecycle.poll()) != null) {
            if (view.getParent() != null) {
                removeView(view);
            }
            viewPool.add(view);
        }
    }

    private void removeItem(long id, int index) {
        MusicListItem view = activeViews.remove(id);
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
        pendingRemovals.add(new PendingRemoval(id, view, index));
        pendingRemovals.sort(Comparator.comparingInt(PendingRemoval::index));
        animateRemoval(id, view);
    }

    private void addItem(long id, int index) {
        if (!isIndexInWindow(index)) {
            return;
        }
        MusicDetail data = items.get(index);
        MusicListItem view = viewPool.poll();
        if (view == null) {
            view = MusicListFactory.createItem(this);
        } else {
            view.clearData();
        }
        view.bindData(data);
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
            int h = layoutHeight(items.get(i).getId());
            if (i == index) {
                return acc + h > top && acc < bottom;
            }
            acc += h;
        }
        return false;
    }

    private void animateInsertion(long id, MusicListItem view, int targetHeight) {
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

    private void animateRemoval(long id, MusicListItem view) {
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

    private void recycleViewIfAttached(MusicListItem view) {
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
            ids.add(items.get(i).getId());
        }
        while (pendingIdx < pendingRemovals.size()) {
            ids.add(pendingRemovals.get(pendingIdx++).id());
        }
        return ids;
    }

    private MusicListItem viewForId(long id) {
        MusicListItem view = activeViews.get(id);
        if (view != null) {
            return view;
        }
        for (PendingRemoval pr : pendingRemovals) {
            if (pr.id() == id) {
                return pr.view();
            }
        }
        return null;
    }

    private int layoutHeight(long id) {
        MusicListItem view = viewForId(id);
        if (view != null && view.getLayoutParams() != null && runningAnimations.containsKey(id)) {
            return view.getLayoutParams().height;
        }
        return heightByItemId.getOrDefault(id, defaultItemHeight);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        for (MusicListItem view : activeViews.values()) {
            MusicDetail detail = view.getMusicDetail();
            if (detail != null && !runningAnimations.containsKey(detail.getId())
                    && view.getMeasuredHeight() > 0) {
                heightByItemId.put(detail.getId(), view.getMeasuredHeight());
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
        int y = 0;
        for (long id : layoutIds()) {
            int h = layoutHeight(id);
            MusicListItem view = viewForId(id);
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
    }

    private void cancelAllAnimations() {
        for (AnimatorSet animator : new ArrayList<>(runningAnimations.values())) {
            animator.cancel();
        }
        runningAnimations.clear();
    }
}
