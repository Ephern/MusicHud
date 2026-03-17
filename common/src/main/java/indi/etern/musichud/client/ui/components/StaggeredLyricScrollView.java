package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.*;
import icyllis.modernui.core.Choreographer;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ScrollController;
import icyllis.modernui.mc.ui.ClampingScrollView;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import indi.etern.musichud.beans.music.LyricLine;
import indi.etern.musichud.client.music.NowPlayingInfo;
import indi.etern.musichud.client.ui.utils.Easings;
import lombok.Getter;
import lombok.NonNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class StaggeredLyricScrollView extends ClampingScrollView {
    private static final int AUTO_RECENTER_DELAY_MILLIS = 3000;
    private static final float MAX_DELAY_MILLIS = 200;
    private static final float LOG_DELAY_FACTOR = 19;
    private static final float ANIM_DURATION_MILLIS = 800;

    private final Map<LyricLine, LyricLineView> lyricLines = new LinkedHashMap<>();
    private final List<LyricLineView> lineList = new ArrayList<>();
    private final LinearLayout container;
    private final ScrollController scrollController;

    // 滚动状态
    private ScrollStatus scrollStatus = ScrollStatus.FOLLOW_LYRICS;
    private long lastUserScrollTime = 0;
    @Getter
    private int currentScrollPosition = 0;

    // 高亮相关
    private LyricLineView lastHighlightedLine;
    private LyricLine lastHighlightedLyricLine;
    private final Consumer<LyricLine> lyricLineUpdateListener = this::highlightLine;

    // 自动归位任务
    private final Runnable autoRecenterRunnable = new Runnable() {
        @Override
        public void run() {
            if (scrollStatus == ScrollStatus.MANUAL) {
                if (MuiModApi.getElapsedTime() - lastUserScrollTime >= AUTO_RECENTER_DELAY_MILLIS) {
                    recenter();
                } else {
                    postDelayed(this, 100);
                }
            }
        }
    };

    private boolean continueUpdate = false;

    private long staggeredStartMillis;
    private boolean staggeredActive;
    private float[] delayMillis;
    @Getter
    private float lastTargetScrollPosition;
    private float startScrollPosition;

    public StaggeredLyricScrollView(Context context) {
        super(context);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);

        setAlpha(0);

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        addView(container, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        NowPlayingInfo.getInstance().getLyricLineUpdateListener().add(lyricLineUpdateListener);

        addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (scrollStatus == ScrollStatus.IDLE || scrollStatus == ScrollStatus.MANUAL) {
                recenter();
            }
        });

        setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY != oldScrollY) {
                currentScrollPosition = scrollY;
                checkManualScrolling();
            }
        });

        scrollController = new ScrollController((controller, amount) -> {
            scrollTo(0, (int) amount);
        });
    }

    public void setLyrics(Collection<LyricLine> lyrics) {
        stopStaggeredAnimation();

        if (container.getChildCount() > 0) {
            ObjectAnimator slideOut = ObjectAnimator.ofFloat(container, View.TRANSLATION_X, 0, -getWidth());
            slideOut.setInterpolator(Easings.EASE_IN_OUT_QUINT);
            slideOut.setDuration(300);
            slideOut.addListener(new AnimatorListener() {
                @Override
                public void onAnimationEnd(@NonNull Animator animation) {
                    container.removeAllViews();
                    buildLyricRows(lyrics);
                    container.setTranslationX(getWidth());
                    ObjectAnimator slideIn = ObjectAnimator.ofFloat(container, View.TRANSLATION_X, 0);
                    slideIn.setInterpolator(Easings.EASE_IN_OUT_QUINT);
                    slideIn.setDuration(300);
                    slideIn.start();
                }
            });
            slideOut.start();
        } else {
            buildLyricRows(lyrics);
        }
    }

    private void buildLyricRows(Collection<LyricLine> lyrics) {
        lyricLines.clear();
        lineList.clear();

        if (lyrics == null) return;
        Context context = getContext();
        container.addView(new FrameLayout(context), new LayoutParams(0, dp(64)));

        for (LyricLine line : lyrics) {
            LyricLineView row = new LyricLineView(context, line);
            container.addView(row);
            lyricLines.put(line, row);
            lineList.add(row);
        }

        container.addView(new RatedHeightView(context, 0.7f, () -> this));

        post(() -> {
            requestLayout();
            initializeScrollToCurrentLyric();
        });
    }

    private void initializeScrollToCurrentLyric() {
        LyricLine current = NowPlayingInfo.getInstance().getCurrentLyricLine();
        if (current != null) {
            LyricLineView target = lyricLines.get(current);
            if (target != null) {
                jumpToLyric(target);
/*
                if (lastHighlightedLine != null && lastHighlightedLine != target) {
                    lastHighlightedLine.fade();
                }
*/
                target.emphasize();
                lastHighlightedLine = target;
                lastHighlightedLyricLine = current;
            } else {
                jumpToTop();
            }
        } else if (!lyricLines.isEmpty()) {
            jumpToTop();
        }

        ObjectAnimator alpha = ObjectAnimator.ofFloat(this, View.ALPHA, 0, 1f);
        alpha.setDuration(300);
        alpha.setInterpolator(Easings.EASE_OUT_QUAD);
        alpha.start();
    }

    void highlightLine(LyricLine lyricLine) {
        MuiModApi.postToUiThread(() -> {
            if (lyricLine == null) {
                if (lastHighlightedLine != null) {
//                    lastHighlightedLine.fade();
                    lastHighlightedLine = null;
                }
                lastHighlightedLyricLine = null;
                return;
            }
            LyricLineView target = lyricLines.get(lyricLine);
            if (target == null) return;

/*
            if (lastHighlightedLine != null && lastHighlightedLine != target) {
                lastHighlightedLine.fade();
            }
*/
            target.emphasize();
            lastHighlightedLine = target;
            lastHighlightedLyricLine = lyricLine;

            if (scrollStatus == ScrollStatus.IDLE || scrollStatus == ScrollStatus.FOLLOW_LYRICS) {
                scrollToLyric(target);
            }
        });
    }

    private void recenter() {
        scrollStatus = ScrollStatus.RECENTER;
        LyricLine targetLine = null;
        LyricLine current = NowPlayingInfo.getInstance().getCurrentLyricLine();
        if (current != null) {
            targetLine = current;
        } else if (lastHighlightedLyricLine != null) {
            targetLine = lastHighlightedLyricLine;
        }

        if (targetLine != null) {
            LyricLineView target = lyricLines.get(targetLine);
            if (target != null) {
                scrollToLyric(target);
            }
        }
    }

    private void jumpToTop() {
        if (scrollController == null) return;
        scrollStatus = ScrollStatus.RECENTER;
        scrollController.abortAnimation();
        int maxScroll = Math.max(0, container.getHeight() - getHeight());
        scrollController.setMaxScroll(maxScroll);
        scrollController.scrollTo(0, 0);
        scrollController.setStartValue(currentScrollPosition);
        scrollController.abortAnimation();
        scrollStatus = ScrollStatus.IDLE;
    }

    private void jumpToLyric(LyricLineView target) {
        if (target == null || scrollController == null) return;
        scrollStatus = ScrollStatus.FOLLOW_LYRICS;
        int targetTop = target.getScrollPosition(this);
        int scrollViewHeight = getHeight();
        if (scrollViewHeight <= 0) {
            post(() -> jumpToLyric(target));
            return;
        }
        int targetScrollY = targetTop - dp(80);
        int maxScroll = Math.max(0, container.getHeight() - scrollViewHeight);
        targetScrollY = Math.max(0, Math.min(targetScrollY, maxScroll));

        scrollController.abortAnimation();
        scrollController.setMaxScroll(maxScroll);
        scrollController.scrollTo(targetScrollY, 0);
        scrollController.setStartValue(currentScrollPosition);
        scrollController.abortAnimation();
        scrollStatus = ScrollStatus.FOLLOW_LYRICS;
    }

    private void scrollToLyric(LyricLineView target) {
        if (target == null || scrollController == null) return;
        int targetTop = target.getScrollPosition(this);;
        int scrollViewHeight = getHeight();
        if (scrollViewHeight <= 0) {
            post(() -> scrollToLyric(target));
            return;
        }
        int maxScroll = Math.max(0, container.getHeight() - scrollViewHeight);
        lastTargetScrollPosition = Math.max(0, Math.min(targetTop - dp(80), maxScroll));
        int currentY = currentScrollPosition;
        startScrollPosition = currentY;
        if (Math.abs(lastTargetScrollPosition - currentY) < 5) {
            if (scrollStatus == ScrollStatus.RECENTER) scrollStatus = ScrollStatus.IDLE;
            return;
        }

        int duration = scrollStatus == ScrollStatus.RECENTER ? (int) Math.min(200 + Math.abs(lastTargetScrollPosition - currentY) / 5, 400) : (int) (ANIM_DURATION_MILLIS / 2);
        if (scrollStatus == ScrollStatus.IDLE) {
            scrollStatus = ScrollStatus.FOLLOW_LYRICS;
        }

        int targetIndex = lineList.indexOf(target);
        if (targetIndex >= 0 && scrollStatus == ScrollStatus.FOLLOW_LYRICS) {
            startStaggeredAnimation(targetIndex);
        }

        scrollController.scrollTo(lastTargetScrollPosition, duration);
        scrollController.setStartValue(currentY);
        scrollController.setMaxScroll(maxScroll);
    }

    private void checkManualScrolling() {
        if (scrollStatus == ScrollStatus.IDLE) {
            scrollStatus = ScrollStatus.MANUAL;
            lastUserScrollTime = MuiModApi.getElapsedTime();
            removeCallbacks(autoRecenterRunnable);
            postDelayed(autoRecenterRunnable, AUTO_RECENTER_DELAY_MILLIS);
            if (scrollController.isScrolling()) {
                scrollController.abortAnimation();
            }
            // 用户手动滚动时立即停止并清除偏移动画
            stopStaggeredAnimation();
        }
    }

    public int getRelativeTop(LyricLineView lyricLineView) {
        int top = 0;
        View current = lyricLineView;
        while (current != container && current != null) {
            top += current.getTop();
            current = (View) current.getParent();
        }
        return top;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startUpdateLoop();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopUpdateLoop();
        removeCallbacks(autoRecenterRunnable);
        NowPlayingInfo.getInstance().getLyricLineUpdateListener().remove(lyricLineUpdateListener);
        if (scrollController != null) {
            scrollController.abortAnimation();
        }
    }

    private void startUpdateLoop() {
        continueUpdate = true;
        Choreographer.getInstance().postFrameCallback((choreographer, frameTimeNanos) -> {
            if (continueUpdate) {
                scrollController.update(MuiModApi.getElapsedTime());
                if (scrollController.getCurrValue() == lastTargetScrollPosition
                        && (scrollStatus == ScrollStatus.FOLLOW_LYRICS || scrollStatus == ScrollStatus.RECENTER)) {
                    scrollStatus = ScrollStatus.IDLE;
                }

                if (staggeredActive) {
                    updateStaggeredTranslations(frameTimeNanos);
                } else {
                    for (LyricLineView line : lineList) {
                        line.setTranslationY(0f);
                    }
                }

                invalidate();
                startUpdateLoop();
            }
        });
    }

    private void stopUpdateLoop() {
        continueUpdate = false;
    }

    private void startStaggeredAnimation(int targetIndex) {
        stopStaggeredAnimation();

        int totalLines = lineList.size();
        delayMillis = new float[totalLines];

        for (int i = 0; i < totalLines; i++) {
            int distance = Math.abs(i - targetIndex);
            float normalized = (float) distance / (float) totalLines;
            float delayFactor = (float) Math.min(1.0, Math.log(1 + normalized * LOG_DELAY_FACTOR) / Math.log(1 + LOG_DELAY_FACTOR));
            delayMillis[i] = delayFactor * MAX_DELAY_MILLIS;
        }

        staggeredStartMillis = Core.timeMillis();
        staggeredActive = true;
    }

    private void stopStaggeredAnimation() {
        staggeredActive = false;
        if (lineList != null) {
            for (LyricLineView line : lineList) {
                line.setTranslationY(0f);
            }
        }
    }

    private void updateStaggeredTranslations(long currentTimeNanos) {
        float elapsedMillis = ((float) currentTimeNanos / 1000000 - staggeredStartMillis);
        boolean anyActive = false;

        float baseOffset = (scrollController.getCurrValue() - startScrollPosition) / 2;
        for (int i = 0; i < lineList.size(); i++) {
            LyricLineView line = lineList.get(i);
            float delay = delayMillis[i];

            if (elapsedMillis <= delay) {
                line.setTranslationY(baseOffset);
                anyActive = true;
            } else {
                float t = elapsedMillis - delay; // 该行动画已进行时间
                if (t < ANIM_DURATION_MILLIS) {
                    // 动画进行中
                    float progress = t / ANIM_DURATION_MILLIS; // [0,1)
                    float eased = Easings.EASE_IN_OUT_QUINT.getInterpolation(progress);
                    float offset = baseOffset * (1 - eased);
                    line.setTranslationY(offset);
                    anyActive = true;
                } else {
                    line.setTranslationY(0f);
                }
            }
        }

        if (!anyActive) {
            staggeredActive = false;
            // 再次确保所有行归零
            for (LyricLineView line : lineList) {
                line.setTranslationY(0f);
            }
        }
    }

    enum ScrollStatus {
        IDLE, MANUAL, RECENTER, FOLLOW_LYRICS
    }

    public static class RatedHeightView extends View {
        private final Supplier<View> targetSupplier;
        private final float heightPercent;

        public RatedHeightView(Context context, float heightRate, Supplier<View> targetSupplier) {
            super(context);
            this.heightPercent = heightRate;
            this.targetSupplier = targetSupplier;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            View parent = targetSupplier.get();
            if (parent != null) {
                int parentHeight = parent.getHeight();
                int targetHeight = (int) (parentHeight * heightPercent);
                int width = MeasureSpec.getSize(widthMeasureSpec);
                setMeasuredDimension(width, targetHeight);
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }
    }
}