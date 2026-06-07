package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.Animator;
import icyllis.modernui.animation.AnimatorListener;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.core.Choreographer;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.BlendMode;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.LinearGradient;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.Shader;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ScrollController;
import icyllis.modernui.mc.ui.ClampingScrollView;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.LyricLine;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.ui.utils.Easing;
import indi.etern.musichud.client.ui.utils.SpringInterpolator;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class StaggeredLyricScrollView extends ClampingScrollView {
    public static final int AUTO_RECENTER_DELAY_MILLIS = 1000;
    public static final float MAX_DELAY_MILLIS = 500;
    public static final float STAGGERED_BASE_DURATION_MILLIS = 600;
    public static final int MANUAL_SCROLL_FADE_DURATION = 250;
    private static final SpringInterpolator STAGGER_INTERPOLATOR = new SpringInterpolator(STAGGERED_BASE_DURATION_MILLIS * 0.001f, 1);
    private static Logger logger;
    private final Set<LyricLineView> animatingLyricViews = new HashSet<>();
    private final Map<LyricLine, LyricLineView> lyricLines = new LinkedHashMap<>();
    @Getter
    private final List<LyricLineView> lyricLineViewList = new ArrayList<>();
    private final LinearLayout container;
    private final ScrollController scrollController;
    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private final Paint fadeEdgePaint = new Paint();
    Runnable staggeringEndListener = null;
    boolean firstStagger = true;
    boolean scrollFinished = false;
    @Getter
    private volatile ScrollStatus scrollStatus = ScrollStatus.FOLLOW_LYRICS;
    @Getter
    private long lastUserStartScrollTime = -AUTO_RECENTER_DELAY_MILLIS - MANUAL_SCROLL_FADE_DURATION;
    @Getter
    private long lastUserScrollTime = -AUTO_RECENTER_DELAY_MILLIS - MANUAL_SCROLL_FADE_DURATION;
    private long lastAutoScrollTime = (long) -STAGGERED_BASE_DURATION_MILLIS;
    @Getter
    private int currentScrollPosition = 0;
    private LyricLine justHighlightedLyricLine;
    private LyricLine lastHighlightedLyricLine;
    private boolean continueUpdate = false;
    private long lyricAnimationStartAtMillis;
    @Setter
    @Getter
    private boolean staggeredActive;
    @Getter
    @Setter
    private boolean fadeEdgesEnabled = true;
    @Getter
    @Setter
    private float fadeEdgeMaxStrength = 1.0f;
    private LinearGradient topFadeGradient;
    private LinearGradient bottomFadeGradient;
    private int cachedTopFadeHeight = -1;
    private int cachedBottomFadeHeight = -1;
    private float cachedFadeStrength = -1f;
    private float[] delayMillis;
    @Getter
    private float lastTargetScrollPosition;
    private float cumulativeBaseOffset;
    private float prevScrollValue;
    private boolean prevScrollInitialized;
    private float baseOffsetAtRedirect;
    private float[] staggerFromOffsets;
    private volatile MusicDetail musicDetail;
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
            post(() -> {
                if (scrollY != oldScrollY && currentScrollPosition != scrollY) {
                    currentScrollPosition = scrollY;
                    checkManualScrolling();
                }
            });
        });

        scrollController = new ScrollController((controller, amount) -> {
            scrollTo(0, (int) amount);
        });
    }

    public void switchLyrics(MusicDetail musicDetail, Collection<LyricLine> lyrics) {
        this.musicDetail = musicDetail;
        try {
            stopUpdateLoop();
            if (container.getChildCount() > 0) {
                ObjectAnimator slideOut = ObjectAnimator.ofFloat(container, View.TRANSLATION_X, 0, -getWidth());
                slideOut.setInterpolator(Easing.EASE_IN_OUT_QUINT);
                slideOut.setDuration(300);
                slideOut.addListener(new AnimatorListener() {
                    @Override
                    public void onAnimationEnd(@NonNull Animator animation) {
                        firstStagger = true;
                        justHighlightedLyricLine = null;
                        lastHighlightedLyricLine = null;
                        animatingLyricViews.clear();
                        container.removeAllViews();
                        buildLyricRows(lyrics);
                        container.setTranslationX(getWidth());
                        ObjectAnimator slideIn = ObjectAnimator.ofFloat(container, View.TRANSLATION_X, 0);
                        slideIn.setInterpolator(Easing.EASE_IN_OUT_QUINT);
                        slideIn.setDuration(300);
                        slideIn.start();
                        if (!continueUpdate) {
                            startUpdateLoop();
                        }
                    }
                });
                slideOut.start();
            } else {
                if (!continueUpdate) {
                    startUpdateLoop();
                }
                buildLyricRows(lyrics);
            }
        } catch (Exception e) {
            if (logger == null) {
                logger = MusicHud.getLogger(HudRendererManager.class);
            }
            logger.error("While switch lyrics", e);
        }
    }

    private void buildLyricRows(Collection<LyricLine> lyrics) {
        lyricLines.clear();
        lyricLineViewList.clear();

        if (lyrics == null) return;
        Context context = getContext();
        container.addView(new FrameLayout(context), new LayoutParams(0, dp(64)));

        for (LyricLine line : lyrics) {
            LyricLineView row = new LyricLineView(context, line);
            container.addView(row);
            lyricLines.put(line, row);
            lyricLineViewList.add(row);
        }

        container.addView(new RatedHeightView(context, 0.7f, () -> this));

        post(() -> {
            requestLayout();
            for (LyricLineView line : lyricLineViewList) {
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
        alpha.setInterpolator(Easing.EASE_OUT_QUAD);
        alpha.start();
    }

    void highlightLine(@Nullable LyricLine lyricLine) {
        if (Objects.equals(musicDetail, nowPlayingInfo.getCurrentlyPlayingMusicDetail())) {
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
                    long now = MuiModApi.getElapsedTime();
                    boolean disableStagger = lyricLine.getType() == LyricLine.Type.META_DATA || now - lastAutoScrollTime < STAGGERED_BASE_DURATION_MILLIS * 2 / 3;
                    if (!disableStagger) {
                        lastAutoScrollTime = MuiModApi.getElapsedTime();
                        scrollToLyric(target);
                    }
                } else if (scrollStatus == ScrollStatus.MANUAL) {
                    lyricAnimationStartAtMillis = Core.timeMillis();
                }
            });
        }
    }

    private void recenter() {
        LyricLine targetLine = justHighlightedLyricLine;
        if (targetLine == null) return;
        lastHighlightedLyricLine = justHighlightedLyricLine;
        if (scrollStatus == ScrollStatus.RECENTER) return;
        scrollStatus = ScrollStatus.RECENTER;

        LyricLineView target = lyricLines.get(targetLine);
        if (target != null) {
            scrollToLyric(target);
        }
    }

    private void jumpToTop() {
        if (scrollController == null) return;
        scrollController.abortAnimation();
        int maxScroll = Math.max(0, container.getHeight() - getHeight());
        scrollController.setMaxScroll(maxScroll);
        scrollController.scrollTo(0, 0);
        scrollController.setStartValue(currentScrollPosition);
        scrollController.abortAnimation();
        currentScrollPosition = 0;
    }

    private void jumpToLyric(LyricLineView target) {
        if (target == null || scrollController == null) return;
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
        currentScrollPosition = targetScrollY;
    }

    private void scrollToLyric(LyricLineView target) {
        if (target == null || scrollController == null) return;
        int targetTop = target.getScrollPosition(this);

        int scrollViewHeight = getHeight();
        if (scrollViewHeight <= 0) {
            post(() -> scrollToLyric(target));
            return;
        }
        int maxScroll = Math.max(0, container.getHeight() - scrollViewHeight);
        lastTargetScrollPosition = Math.clamp(targetTop - dp(80), 0, maxScroll);

        int duration = (int) (STAGGERED_BASE_DURATION_MILLIS / 2);
        if (scrollStatus == ScrollStatus.IDLE) {
            scrollStatus = ScrollStatus.FOLLOW_LYRICS;
        }

        int targetIndex = lyricLineViewList.indexOf(target);
        boolean staggerJustStarted = !staggeredActive;
        if (staggerJustStarted) {
            staggerFromOffsets = null;
        } else {
            baseOffsetAtRedirect = cumulativeBaseOffset;
            staggerFromOffsets = new float[lyricLineViewList.size()];
            for (int i = 0; i < lyricLineViewList.size(); i++) {
                staggerFromOffsets[i] = lyricLineViewList.get(i).getTranslationY();
            }
        }
        calcLoggedDelay(targetIndex);
        lyricAnimationStartAtMillis = Core.timeMillis();
        if (targetIndex >= 0 && (scrollStatus == ScrollStatus.FOLLOW_LYRICS || scrollStatus == ScrollStatus.RECENTER)) {
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
            lastUserStartScrollTime = MuiModApi.getElapsedTime();
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
        updateLoop();
    }

    private void updateLoop() {
        Choreographer.getInstance().postFrameCallback((choreographer, frameTimeNanos) -> {
            if (continueUpdate) {
                if (scrollController.isScrolling()) {
                    scrollController.update(MuiModApi.getElapsedTime());
                    scrollFinished = false;
                }
                if (((!scrollController.isScrolling() && !scrollFinished) || scrollController.getCurrValue() == lastTargetScrollPosition)
                        && (scrollStatus == ScrollStatus.FOLLOW_LYRICS || scrollStatus == ScrollStatus.RECENTER)) {
                    scrollFinished = true;
                    if (!staggeredActive || animatingLyricViews.isEmpty()) {
                        scrollStatus = ScrollStatus.IDLE;
                    } else {
                        staggeringEndListener = () -> {
                            scrollStatus = ScrollStatus.IDLE;
                        };
                    }
                }
                updateTranslations(frameTimeNanos);

                invalidate();
                updateLoop();
            }
        });
    }

    private void stopUpdateLoop() {
        continueUpdate = false;
    }

    private void calcLoggedDelay(int targetIndex) {
        int totalLines = lyricLineViewList.size();
        delayMillis = new float[totalLines];

        double max = Math.max(5, Math.log(1 + totalLines));
        for (int i = 0; i < totalLines; i++) {
            int distance = Math.abs(i - targetIndex + 1);
            float delayFactor = (float) Math.clamp(Math.log(1 + distance) / max, 0, 1);
            delayMillis[i] = delayFactor * MAX_DELAY_MILLIS * (i < targetIndex ? 0.5f : 1);
        }
    }

    private void updateTranslations(long currentTimeNanos) {
        float elapsedMillis = ((float) currentTimeNanos / 1000000 - lyricAnimationStartAtMillis);
        boolean anyActive = false;

        float currentScrollValue = scrollController.getCurrValue();
        if (prevScrollInitialized) {
            cumulativeBaseOffset += (currentScrollValue - prevScrollValue) / 2.0f;
        } else {
            cumulativeBaseOffset = 0;
            prevScrollInitialized = true;
        }
        prevScrollValue = currentScrollValue;
        float baseOffset = staggeredActive ? cumulativeBaseOffset : 0;

        float scrollCompensation = staggerFromOffsets != null && staggeredActive
                ? baseOffset - baseOffsetAtRedirect
                : baseOffset;

        firstStagger = false;
        for (int i = 0; i < lyricLineViewList.size(); i++) {
            LyricLineView line = lyricLineViewList.get(i);
            float delay = delayMillis == null || !staggeredActive ? 0 : delayMillis[i >= delayMillis.length ? delayMillis.length - 1 : i];
            if (scrollStatus == ScrollStatus.RECENTER) {
                delay /= 2;
            }
            float lastTargetOffset = line.getTargetOffset(lastHighlightedLyricLine);
            float targetOffset = line.getTargetOffset(justHighlightedLyricLine);

            float fromOffset = staggerFromOffsets != null && i < staggerFromOffsets.length
                    ? staggerFromOffsets[i]
                    : lastTargetOffset;

            if (elapsedMillis <= delay) {
                line.setTranslationY(fromOffset + scrollCompensation);
                anyActive = true;
            } else {
                float t = elapsedMillis - delay;
                float rawProgress = t / STAGGERED_BASE_DURATION_MILLIS;
                float progress = Math.min(rawProgress, 1.0f);
                if (rawProgress <= 1.05) {
                    animatingLyricViews.add(line);
                    float eased = STAGGER_INTERPOLATOR.getInterpolation(progress);
                    float offset = fromOffset * (1 - eased) + targetOffset * eased + scrollCompensation * (1 - eased);
                    line.setTranslationY(offset);
                    anyActive = true;
                } else {
                    line.setTranslationY(targetOffset);
                    animatingLyricViews.remove(line);
                    if (staggeredActive) {
                        if (animatingLyricViews.isEmpty()) {
                            if (staggeringEndListener != null) {
                                staggeringEndListener.run();
                            }
                        }
                    }
                }
            }
        }
        if (!anyActive) {
            if (staggeredActive) {
                staggeredActive = false;
                delayMillis = null;
                staggerFromOffsets = null;
                cumulativeBaseOffset = 0;
            }
            prevScrollInitialized = false;
        }
    }

    @Override
    public void onDrawForeground(@NotNull Canvas canvas) {
        super.onDrawForeground(canvas);
        if (fadeEdgesEnabled) {
            drawFadeEdges(canvas);
        }
    }

    private void drawFadeEdges(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;
        int containerHeight = container.getHeight();
        if (containerHeight <= height) return;

        float topFadeEdgeFraction = 0.1f;
        int topFadeHeight = (int) (height * Math.clamp(topFadeEdgeFraction, 0, 1));
        float bottomFadeEdgeFraction = 0.85f;
        int bottomFadeHeight = (int) (height * Math.clamp(bottomFadeEdgeFraction, 0, 1));
        int maxScroll = containerHeight - height;
        int scrollY = getScrollY();

        updateFadeGradients(topFadeHeight, bottomFadeHeight);

        float edgeFadeExtraAlpha;
        long elapsedTime = MuiModApi.getElapsedTime();
        if (scrollStatus == ScrollStatus.MANUAL) {
            long timeMillis = elapsedTime - lastUserStartScrollTime;
            if (timeMillis < 0) {
                edgeFadeExtraAlpha = 1;
            } else if (timeMillis <= MANUAL_SCROLL_FADE_DURATION) {
                edgeFadeExtraAlpha = 1 - Math.clamp((float) timeMillis / MANUAL_SCROLL_FADE_DURATION, 0f, 1f);
            } else {
                edgeFadeExtraAlpha = 0;
            }
        } else {
            long timeMillis = elapsedTime - lastUserScrollTime - AUTO_RECENTER_DELAY_MILLIS;
            if (timeMillis < 0) {
                edgeFadeExtraAlpha = 0;
            } else if (timeMillis <= MANUAL_SCROLL_FADE_DURATION) {
                edgeFadeExtraAlpha = Math.clamp((float) timeMillis / MANUAL_SCROLL_FADE_DURATION, 0f, 1f);
            } else {
                edgeFadeExtraAlpha = 1;
            }
        }

        fadeEdgePaint.setBlendMode(BlendMode.DST_OUT);

        if (scrollY > 0 && topFadeHeight > 0) {
            float strength = Math.min(1f, scrollY / (float) topFadeHeight);
            fadeEdgePaint.setShader(topFadeGradient);
            fadeEdgePaint.setAlpha((int) (255 * strength * edgeFadeExtraAlpha));
            canvas.save();
            canvas.translate(0, scrollY);
            canvas.drawRect(0, 0, width, topFadeHeight, fadeEdgePaint);
            canvas.restore();
        }

        if (scrollY < maxScroll && bottomFadeHeight > 0) {
            float strength = Math.min(1f, (maxScroll - scrollY) / (float) bottomFadeHeight);
            fadeEdgePaint.setShader(bottomFadeGradient);
            fadeEdgePaint.setAlpha((int) (255 * strength * edgeFadeExtraAlpha));
            canvas.save();
            canvas.translate(0, scrollY + height - bottomFadeHeight);
            canvas.drawRect(0, 0, width, bottomFadeHeight, fadeEdgePaint);
            canvas.restore();
        }

        fadeEdgePaint.setBlendMode(null);
    }

    private void updateFadeGradients(int topFadeHeight, int bottomFadeHeight) {
        if (topFadeHeight == cachedTopFadeHeight && bottomFadeHeight == cachedBottomFadeHeight
                && fadeEdgeMaxStrength == cachedFadeStrength
                && topFadeGradient != null && bottomFadeGradient != null) {
            return;
        }
        cachedTopFadeHeight = topFadeHeight;
        cachedBottomFadeHeight = bottomFadeHeight;
        cachedFadeStrength = fadeEdgeMaxStrength;
        int maxAlpha = (int) (255 * Math.clamp(fadeEdgeMaxStrength, 0, 1));
        int opaqueColor = (maxAlpha << 24) | 0x00FFFFFF;
        if (topFadeHeight > 0) {
            topFadeGradient = new LinearGradient(0, 0, 0, topFadeHeight,
                    opaqueColor, 0x00000000, Shader.TileMode.CLAMP, null);
        }
        if (bottomFadeHeight > 0) {
            bottomFadeGradient = new LinearGradient(0, 0, 0, bottomFadeHeight,
                    0x00000000, opaqueColor, Shader.TileMode.CLAMP, null);
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