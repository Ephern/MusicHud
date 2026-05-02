package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.Animator;
import icyllis.modernui.animation.AnimatorListener;
import icyllis.modernui.animation.ObjectAnimator;
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
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.ui.utils.Easings;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class StaggeredLyricScrollView extends ClampingScrollView {
    public static final int AUTO_RECENTER_DELAY_MILLIS = 1000;
    public static final float MAX_DELAY_MILLIS = 150;
    public static final float MAX_STRETCH_MILLIS = 300;
    public static final float LOG_DELAY_FACTOR = 9;
    public static final float STAGGERED_BASE_DURATION_MILLIS = 800;
    private final Map<LyricLine, LyricLineView> lyricLines = new LinkedHashMap<>();
    private final List<LyricLineView> lineList = new ArrayList<>();
    private final LinearLayout container;
    private final ScrollController scrollController;
    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    Set<LyricLineView> staggeringLyricViews = new HashSet<>();
    Runnable staggeringEndListener = null;
    boolean firstStagger = true;
    boolean scrollFinished = false;
    @Getter
    private volatile ScrollStatus scrollStatus = ScrollStatus.FOLLOW_LYRICS;
    @Getter
    private long lastUserScrollTime = 0;
    @Getter
    private int currentScrollPosition = 0;
    private LyricLine justHighlightedLyricLine;
    private LyricLine lastHighlightedLyricLine;
    private boolean continueUpdate = false;
    private long startScrollMillis;
    @Setter
    @Getter
    private boolean staggeredActive;
    private float[] delayMillis;
    private float[] stretchMillis;
    @Getter
    private float lastTargetScrollPosition;
    private float cumulativeBaseOffset;
    private float prevScrollValue;
    private boolean prevScrollInitialized;
    private float baseOffsetAtRedirect;
    private float[] staggerFromOffsets;
    private final Consumer<LyricLine> lyricLineUpdateListener = this::highlightLine;
    private final Runnable autoRecenterRunnable = new Runnable() {
        @Override
        public void run() {
            if (scrollStatus == ScrollStatus.MANUAL) {
                if (MuiModApi.getElapsedTime() - lastUserScrollTime >= AUTO_RECENTER_DELAY_MILLIS) {
                    scrollStatus = ScrollStatus.IDLE;
                    recenter();
                } else {
                    postDelayed(this, 50);
                }
            }
        }
    };

    public StaggeredLyricScrollView(Context context) {
        super(context);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);

        setAlpha(0);

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        addView(container, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        nowPlayingInfo.getLyricLineUpdateListener().add(lyricLineUpdateListener);

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
        if (!continueUpdate) {
            startUpdateLoop();
        }
        firstStagger = true;
        justHighlightedLyricLine = null;
        lastHighlightedLyricLine = null;
        staggeringLyricViews.clear();
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
            for (LyricLineView line : lineList) {
                line.setTranslationY(line.getTargetOffset(nowPlayingInfo.getCurrentLyricLine()));
            }
            initializeScrollToCurrentLyric();
        });
    }

    private void initializeScrollToCurrentLyric() {
        LyricLine current = nowPlayingInfo.getCurrentLyricLine();
        if (current != null) {
            LyricLineView target = lyricLines.get(current);
            if (target != null) {
                jumpToLyric(target);
                target.emphasize();
                lastHighlightedLyricLine = justHighlightedLyricLine;
                justHighlightedLyricLine = current;
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
                justHighlightedLyricLine = null;
                lastHighlightedLyricLine = null;
                return;
            }
            LyricLineView target = lyricLines.get(lyricLine);
            if (target == null) return;
            target.emphasize();
            lastHighlightedLyricLine = justHighlightedLyricLine;
            justHighlightedLyricLine = lyricLine;

            if (scrollStatus == ScrollStatus.IDLE || scrollStatus == ScrollStatus.FOLLOW_LYRICS) {
                Duration duration = lyricLine.getDuration();
                long lineDuration = duration != null ? duration.toMillis() : nowPlayingInfo.getMusicDuration().minus(lyricLine.getStartTime()).toMillis();
                scrollToLyric(target, lyricLine.getType() == LyricLine.Type.META_DATA || lineDuration < STAGGERED_BASE_DURATION_MILLIS / 2);
            }
        });
    }

    private void recenter() {
        lastHighlightedLyricLine = justHighlightedLyricLine;
        if (scrollStatus == ScrollStatus.RECENTER) return;
        scrollStatus = ScrollStatus.RECENTER;
        LyricLine targetLine = justHighlightedLyricLine;

        if (targetLine != null) {
            LyricLineView target = lyricLines.get(targetLine);
            if (target != null) {
                scrollToLyric(target, false);
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
        targetScrollY = Math.clamp(targetScrollY, 0, maxScroll);

        scrollController.abortAnimation();
        scrollController.setMaxScroll(maxScroll);
        scrollController.scrollTo(targetScrollY, 0);
        scrollController.setStartValue(currentScrollPosition);
        scrollController.abortAnimation();

        scrollStatus = ScrollStatus.IDLE;
    }

    private void scrollToLyric(LyricLineView target, boolean forceDisableStagger) {
        if (target == null || scrollController == null) return;
        int targetTop = target.getScrollPosition(this);

        int scrollViewHeight = getHeight();
        if (scrollViewHeight <= 0) {
            post(() -> scrollToLyric(target, false));
            return;
        }
        int maxScroll = Math.max(0, container.getHeight() - scrollViewHeight);
        lastTargetScrollPosition = Math.clamp(targetTop - dp(80), 0, maxScroll);

        int duration = (int) (STAGGERED_BASE_DURATION_MILLIS / 2);
        if (scrollStatus == ScrollStatus.IDLE) {
            scrollStatus = ScrollStatus.FOLLOW_LYRICS;
        }

        int targetIndex = lineList.indexOf(target);
        boolean staggerJustStarted = !staggeredActive;
        if (staggerJustStarted) {
            staggerFromOffsets = null;
        } else {
            baseOffsetAtRedirect = cumulativeBaseOffset;
            staggerFromOffsets = new float[lineList.size()];
            for (int i = 0; i < lineList.size(); i++) {
                staggerFromOffsets[i] = lineList.get(i).getTranslationY();
            }
        }
        calcLoggedDelay(targetIndex);
        startScrollMillis = Core.timeMillis();
        if (forceDisableStagger) {
            staggeredActive = false;
        } else if (targetIndex >= 0 && (scrollStatus == ScrollStatus.FOLLOW_LYRICS || scrollStatus == ScrollStatus.RECENTER)) {
            staggeredActive = true;
        }

        //Force reset due to manual scroll makes scrollController value different to actual value
        if (scrollController.getCurrValue() != currentScrollPosition) {
            scrollController.scrollTo(currentScrollPosition, 0);
            scrollController.abortAnimation();
            prevScrollInitialized = false;
        }

        scrollController.setMaxScroll(maxScroll);
        scrollController.scrollTo(lastTargetScrollPosition, duration);
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
        } else if (scrollStatus == ScrollStatus.MANUAL) {
            lastUserScrollTime = MuiModApi.getElapsedTime();
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
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopUpdateLoop();
        removeCallbacks(autoRecenterRunnable);
        nowPlayingInfo.getLyricLineUpdateListener().remove(lyricLineUpdateListener);
        if (scrollController != null) {
            scrollController.abortAnimation();
        }
    }

    private void startUpdateLoop() {
        continueUpdate = true;
        scrollFinished = false;
        Choreographer.getInstance().postFrameCallback((choreographer, frameTimeNanos) -> {
            if (continueUpdate) {
                if (scrollController.isScrolling()) {
                    scrollController.update(MuiModApi.getElapsedTime());
                    scrollFinished = false;
                }
                if (((!scrollController.isScrolling() && !scrollFinished) || scrollController.getCurrValue() == lastTargetScrollPosition)
                        && (scrollStatus == ScrollStatus.FOLLOW_LYRICS || scrollStatus == ScrollStatus.RECENTER)) {
                    scrollFinished = true;
                    if (!staggeredActive || staggeringLyricViews.isEmpty()) {
                        scrollStatus = ScrollStatus.IDLE;
                    } else {
                        staggeringEndListener = () -> {
                            scrollStatus = ScrollStatus.IDLE;
                        };
                    }
                }
                updateTranslations(frameTimeNanos);

                invalidate();
                startUpdateLoop();
            }
        });
    }

    private void stopUpdateLoop() {
        continueUpdate = false;
    }

    private void calcLoggedDelay(int targetIndex) {
        int totalLines = lineList.size();
        delayMillis = new float[totalLines];
        stretchMillis = new float[totalLines];

        for (int i = 0; i < totalLines; i++) {
            int distance = (int) (Math.abs(i - targetIndex + 0.5f) - 0.5f);
            float normalized = (float) distance / (float) totalLines;
            float delayFactor = (float) Math.min(1.0, Math.log(1 + normalized * LOG_DELAY_FACTOR) / Math.log(1 + LOG_DELAY_FACTOR));
            delayMillis[i] = delayFactor * MAX_DELAY_MILLIS * (i < targetIndex ? 0.3f : 1);
            stretchMillis[i] = delayFactor * MAX_STRETCH_MILLIS;
        }
    }

    private void updateTranslations(long currentTimeNanos) {
        if (!staggeredActive) {
            return;
        }
        float elapsedMillis = ((float) currentTimeNanos / 1000000 - startScrollMillis);
        boolean anyActive = false;

        float currentScrollValue = scrollController.getCurrValue();
        if (prevScrollInitialized) {
            cumulativeBaseOffset += (currentScrollValue - prevScrollValue) / 2.0f;
        } else {
            cumulativeBaseOffset = 0;
            prevScrollInitialized = true;
        }
        prevScrollValue = currentScrollValue;
        float baseOffset = cumulativeBaseOffset;

        float scrollCompensation = staggerFromOffsets != null
                ? baseOffset - baseOffsetAtRedirect
                : baseOffset;

        firstStagger = false;
        for (int i = 0; i < lineList.size(); i++) {
            LyricLineView line = lineList.get(i);
            float delay = delayMillis == null ? 0 : delayMillis[i >= delayMillis.length ? delayMillis.length - 1 : i];
            float stretch = stretchMillis == null ? 0 : stretchMillis[i >= stretchMillis.length ? stretchMillis.length - 1 : i];
            if (scrollStatus == ScrollStatus.RECENTER) {
                delay /= 2;
            }
            float lastTargetOffset = line.getTargetOffset(lastHighlightedLyricLine);
            float targetOffset = line.getTargetOffset(justHighlightedLyricLine);

            float fromOffset = staggerFromOffsets != null
                    ? staggerFromOffsets[i]
                    : lastTargetOffset;

            if (elapsedMillis <= delay) {
                line.setTranslationY(fromOffset + scrollCompensation);
                anyActive = true;
            } else {
                float t = elapsedMillis - delay;
                float duration = STAGGERED_BASE_DURATION_MILLIS + stretch;
                if (t <= duration) {
                    pushStaggering(line);
                    float progress = t / duration; // [0,1)
                    float eased = Easings.EASE_IN_OUT_QUINT.getInterpolation(progress);
                    float offset = fromOffset * (1 - eased) + targetOffset * eased + scrollCompensation * (1 - eased);
                    line.setTranslationY(offset);
                    anyActive = true;
                } else {
                    removeStaggering(line);
                    line.setTranslationY(targetOffset);
                }
            }
        }
        if (!anyActive && staggeredActive) {
            staggeredActive = false;
            delayMillis = null;
            staggerFromOffsets = null;
            prevScrollInitialized = false;
            cumulativeBaseOffset = 0;
        }
    }

    private void pushStaggering(LyricLineView lyricLineView) {
        staggeringLyricViews.add(lyricLineView);
    }

    private void removeStaggering(LyricLineView lyricLineView) {
        staggeringLyricViews.remove(lyricLineView);
        if (staggeringLyricViews.isEmpty()) {
            if (staggeringEndListener != null) {
                staggeringEndListener.run();
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