package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.Animator;
import icyllis.modernui.animation.AnimatorListener;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.core.Choreographer;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.*;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ui.ClampingScrollView;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.dto.LyricLine;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.ui.lyric.LineBlurRenderer;
import indi.etern.musichud.client.utils.ui.Easing;
import indi.etern.musichud.client.utils.ui.SpringInterpolator;
import indi.etern.musichud.client.utils.ui.SpringValue;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

@SuppressWarnings("UnstableApiUsage")
public class StaggeredLyricScrollView extends ClampingScrollView {
    public static final int AUTO_RECENTER_DELAY_MILLIS = 1000;
    public static final float MAX_DELAY_MILLIS = 800;
    public static final int MANUAL_SCROLL_FADE_DURATION = 250;
    private static final float SPACER_HEIGHT_RATIO = 0.7f;
    private static final int SWITCH_DURATION = 400;
    private static final int SCROLL_RESPONSE_MILLIS = 550;
    private static final float SCROLL_DAMPING = 0.95f;
    // The parent scroll delta is scaled by this before it is fed into each row's live decoupling.
    // It must stay 0.5: icyllis.modernui.graphics.RenderProperties.computeTransform applies the
    // view's x/y translation twice (preTranslate + postTranslate both carry it), so a rendered
    // compensation that matches the scroll needs a set value of `delta / 2`.
    private static final float SCROLL_DECOUPLE_FACTOR = 0.5f;
    // Per-row stagger spring: one fixed curve shared by every row (slight overshoot), so a row's
    // amplitude and duration never depend on its distance from the highlighted row. Only the
    // per-row start delay differs, and that delay is scheduled by the scroll view, never the row.
    private static final int ROW_RESPONSE_MILLIS = SCROLL_RESPONSE_MILLIS;
    private static final float ROW_DAMPING = 1f;
    // A very short highlighted line collapses the whole delay wave (see durationFactor) so a fast
    // sequence of short lines never blocks; the per-row delay ratios are preserved.
    private static final int DELAY_SHORTEN_TAU_MILLIS = 200;
    private static final SpringInterpolator SWITCH_INTERPOLATOR =
            new SpringInterpolator((float) SWITCH_DURATION / 1000, 0.9f);
    private static final float MAX_BLUR_DP = 8f;
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static Logger logger;
    private final Map<LyricLine, LyricLineView> lyricLines = new LinkedHashMap<>();
    @Getter
    @Setter
    private int mainLyricSize = Theme.MAIN_LYRIC_SIZE;
    @Getter
    @Setter
    private int subLyricSize = Theme.SUB_LYRIC_SIZE;
    @Getter
    private final List<LyricLineView> lyricLineViewList = new ArrayList<>();
    private final LinearLayout container;
    private final SpringValue scrollSpring;
    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private final AtomicReference<LyricLine> pendingHighlightLine = new AtomicReference<>();
    private final AtomicBoolean highlightScheduled = new AtomicBoolean();
    private final Paint fadeEdgePaint = new Paint();
    private final Consumer<LyricLine> lyricLineUpdateListener = this::highlightLine;
    private final Runnable autoRecenterRunnable = new Runnable() {
        @Override
        public void run() {
            if (scrollStatus == ScrollStatus.MANUAL) {
                if (MuiModApi.getElapsedTime() - lastUserScrollTime >= AUTO_RECENTER_DELAY_MILLIS) {
                    scrollStatus = ScrollStatus.IDLE;
                    recenter(false);
                } else {
                    postDelayed(this, 50);
                }
            }
        }
    };
    Runnable staggeringEndListener = null;
    boolean scrollFinished = false;
    private View bottomSpacer;
    @Getter
    private volatile ScrollStatus scrollStatus = ScrollStatus.FOLLOW_LYRICS;
    @Getter
    private long lastUserStartScrollTime = -AUTO_RECENTER_DELAY_MILLIS - MANUAL_SCROLL_FADE_DURATION;
    @Getter
    private long lastUserScrollTime = -AUTO_RECENTER_DELAY_MILLIS - MANUAL_SCROLL_FADE_DURATION;
    @Getter
    private int currentScrollPosition = 0;
    private LyricLine justHighlightedLyricLine;
    // Per-row stagger state. Each RowWave holds a spring and a progress spring; every delay - when
    // to retarget the row - and every offset is computed and scheduled here, never inside the row.
    private float[] delayMillis;
    private boolean[] staggerStarted;
    private long[] staggerStartNanos;
    // Per-frame scroll delta used to decouple every row from the container scroll during auto-scroll.
    private float prevScrollValue;
    private boolean prevScrollInitialized;
    // Parent-scroll compensation accumulated over the whole wave, relative to the wave start. A
    // single shared scalar; each row releases its own share with its own progress, so this never
    // pins or freezes a row that is still animating.
    private float cumulativeBaseOffset;
    private float baseOffsetAtRedirect;
    private boolean continueUpdate = false;
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
    // One spring-backed RowWave per lyric row (see buildLyricRows).
    private final List<RowWave> rows = new ArrayList<>();
    @Getter
    private float lastTargetScrollPosition;
    private volatile MusicDetail musicDetail;
    // Lyrics collection currently built into the container; only assigned on successful build.
    private Collection<LyricLine> currentLyrics;
    private boolean pendingResyncToCurrent;
    // True while a finger is on the view; programmatic/layout scroll changes must not count as manual.
    private boolean pointerDown;

    public StaggeredLyricScrollView(Context context) {
        super(context);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);

        setAlpha(0);

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        LayoutParams params = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        // Reserve room for the blurred lines' horizontal bleed, otherwise the tail is clipped by the
        // scroll view's bounds and a hard edge appears at the left/right of every line.
        int blurMargin = Math.max(dp(1), LineBlurRenderer.maxPaddingFor(dp(MAX_BLUR_DP)));
        params.setMargins(blurMargin, 0, blurMargin, 0);
        addView(container, params);

        nowPlayingInfo.getLyricLineUpdateListener().add(lyricLineUpdateListener);

        addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            int height = bottom - top;
            int lastWidth = oldRight - oldLeft;
            int lastHeight = oldBottom - oldTop;
            if ((width != lastWidth || height != lastHeight)
                    && (scrollStatus == ScrollStatus.IDLE || scrollStatus == ScrollStatus.MANUAL)) {
                recenter(false);
            }
        });

        setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> post(() -> {
            if (scrollY != oldScrollY && currentScrollPosition != scrollY) {
                currentScrollPosition = scrollY;
                checkManualScrolling();
            }
        }));

        scrollSpring = new SpringValue((float) SCROLL_RESPONSE_MILLIS / 1000, SCROLL_DAMPING);
    }

    public void switchLyrics(MusicDetail musicDetail, Collection<LyricLine> lyrics) {
        this.musicDetail = musicDetail;
        try {
            stopUpdateLoop();
            if (container.getChildCount() > 0) {
                ObjectAnimator slideOut = ObjectAnimator.ofFloat(container, View.TRANSLATION_X, 0, -getWidth());
                slideOut.setInterpolator(SWITCH_INTERPOLATOR);
                slideOut.setDuration(SWITCH_DURATION);
                slideOut.addListener(new AnimatorListener() {
                    @Override
                    public void onAnimationEnd(@NonNull Animator animation) {
                        justHighlightedLyricLine = null;
                        for (LyricLineView view : lyricLineViewList) {
                            view.releaseBlurResources();
                        }
                        container.removeAllViews();
                        buildLyricRows(lyrics);
                        container.setTranslationX(getWidth());
                        ObjectAnimator slideIn = ObjectAnimator.ofFloat(container, View.TRANSLATION_X, 0);
                        slideIn.setInterpolator(SWITCH_INTERPOLATOR);
                        slideIn.setDuration(SWITCH_DURATION);
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

    /**
     * Whether the currently built content exactly corresponds to the given instances.
     * Uses reference comparison (not equals) so a re-created MusicDetail or a different
     * lyrics collection still triggers a rebuild.
     */
    public boolean isShowing(MusicDetail detail, Collection<LyricLine> lyrics) {
        return musicDetail == detail && currentLyrics == lyrics;
    }

    private void buildLyricRows(Collection<LyricLine> lyrics) {
        lyricLines.clear();
        lyricLineViewList.clear();
        currentLyrics = lyrics;

        if (lyrics == null) return;
        Context context = getContext();
        container.addView(new FrameLayout(context), new LayoutParams(0, dp(64)));

        rows.clear();
        for (LyricLine line : lyrics) {
            LyricLineView row = new LyricLineView(context, line, mainLyricSize, subLyricSize);
            container.addView(row);
            lyricLines.put(line, row);
            lyricLineViewList.add(row);
            rows.add(new RowWave(row));
        }

        bottomSpacer = new View(context);
        container.addView(bottomSpacer, new LayoutParams(MATCH_PARENT, 0));

        post(() -> {
            requestLayout();
            for (int i = 0; i < lyricLineViewList.size(); i++) {
                LyricLineView line = lyricLineViewList.get(i);
                float offset = line.getTargetOffset(nowPlayingInfo.getCurrentLyricLine());
                line.setTranslationY(offset);
                rows.get(i).jumpTo(offset);
            }
            post(this::resyncToCurrentLyric);
        });
    }

    public void clearLyrics() {
        switchLyrics(MusicDetail.NONE, null);
    }

    /**
     * One lyric row's animation state: an absolute {@link SpringValue} driving the rendered
     * translationY (minus the live decoupling), plus a normalized progress {@link SpringValue} that
     * releases that decoupling. Both are retargeted with {@link SpringValue#setTarget}, so a rapid
     * switch keeps the running value and velocity instead of resetting the animation. The row knows
     * nothing about delays or scroll - the scroll view schedules every retarget.
     */
    private static final class RowWave {
        final LyricLineView view;
        final SpringValue spring;
        final SpringValue progress;

        RowWave(LyricLineView view) {
            this.view = view;
            this.spring = new SpringValue((float) ROW_RESPONSE_MILLIS / 1000, ROW_DAMPING);
            this.progress = new SpringValue((float) ROW_RESPONSE_MILLIS / 1000, ROW_DAMPING);
        }

        void retarget(float target, long nowNanos) {
            spring.setTarget(target, nowNanos);
        }

        void retargetProgress(float target, long nowNanos) {
            progress.setTarget(target, nowNanos);
        }

        /**
         * Snap the release factor. Safe at a wave start because the compensation has just been
         * rebased to zero, so the factor does not affect the rendered value or its velocity.
         */
        void snapProgress(float value) {
            progress.jumpTo(value);
        }

        /** Shift the running base spring by {@code delta} without interrupting it (keeps velocity). */
        void translate(float delta) {
            spring.translate(delta);
        }

        void update(long nowNanos) {
            spring.update(nowNanos);
            progress.update(nowNanos);
        }

        boolean isSettled() {
            return spring.isSettled() && progress.isSettled();
        }

        void jumpTo(float value) {
            spring.jumpTo(value);
            progress.jumpTo(1f);
        }

        float getValue() {
            return spring.getValue();
        }

        float getTarget() {
            return spring.getTarget();
        }

        float getProgress() {
            return progress.getValue();
        }
    }

    public void resyncToCurrentLyric() {
        if (!isContentLaidOut()) {
            // Rows were rebuilt while the view was GONE / not yet measured; child tops are
            // still 0, so defer until the next real layout pass instead of computing a wrong jump.
            pendingResyncToCurrent = true;
            requestLayout();
            return;
        }
        pendingResyncToCurrent = false;
        LyricLine current = nowPlayingInfo.getCurrentLyricLine();
        if (current != null) {
            LyricLineView target = lyricLines.get(current);
            if (target != null) {
                justHighlightedLyricLine = current;
                jumpToLyric(target);
                target.emphasize();
            } else {
                jumpToTop();
            }
        } else if (!lyricLines.isEmpty()) {
            jumpToTop();
        }
        startUpdateLoop();

        ObjectAnimator alpha = ObjectAnimator.ofFloat(this, View.ALPHA, 0, 1f);
        alpha.setDuration(300);
        alpha.setInterpolator(Easing.EASE_OUT_QUAD);
        alpha.start();
    }

    private boolean isContentLaidOut() {
        // The first lyric row has real height only after a layout pass; a stale scroll-view
        // height or previously laid out container cannot be trusted after a rebuild.
        return lyricLineViewList.isEmpty() || lyricLineViewList.getFirst().getHeight() > 0;
    }

    void highlightLine(@Nullable LyricLine lyricLine) {
        if (!Objects.equals(musicDetail, nowPlayingInfo.getCurrentlyPlayingMusicDetail())) {
            return;
        }
        // Conflate rapid highlight updates to the latest one so the UI thread never backs up.
        pendingHighlightLine.set(lyricLine);
        if (highlightScheduled.compareAndSet(false, true)) {
            MuiModApi.postToUiThread(this::drainHighlight);
        }
    }

    private void drainHighlight() {
        highlightScheduled.set(false);
        applyHighlight(pendingHighlightLine.getAndSet(null));
    }

    private void applyHighlight(@Nullable LyricLine lyricLine) {
        if (lyricLine == null) {
            justHighlightedLyricLine = null;
            return;
        }
        LyricLineView target = lyricLines.get(lyricLine);
        if (target == null) return;
        target.emphasize();
        justHighlightedLyricLine = lyricLine;

        if (scrollStatus == ScrollStatus.MANUAL) {
            return;
        }
        scrollToLyric(target);
    }

    private void recenter(boolean useJump) {
        LyricLine targetLine = justHighlightedLyricLine;
        if (targetLine == null) return;
        // Resolve the target before switching state: entering RECENTER without a valid
        // target would leave the state machine stuck (no scroll is started to complete it).
        LyricLineView target = lyricLines.get(targetLine);
        if (target == null) return;
        if (scrollStatus == ScrollStatus.RECENTER) return;
        scrollStatus = ScrollStatus.RECENTER;
        scrollFinished = false;
        if (useJump) {
            jumpToLyric(target);
        } else {
            scrollToLyric(target);
        }
    }

    private void jumpToTop() {
        if (scrollSpring == null) return;
        resetStaggerState();
        scrollSpring.jumpTo(0f);
        scrollTo(0, 0);
        currentScrollPosition = 0;
        lastTargetScrollPosition = 0f;
        snapRows();
    }

    private void jumpToLyric(LyricLineView target) {
        if (target == null || scrollSpring == null) return;
        int targetTop = target.getScrollPosition(this);
        int scrollViewHeight = getHeight();
        if (scrollViewHeight <= 0) {
            post(() -> jumpToLyric(target));
            return;
        }
        int maxScroll = Math.max(0, container.getHeight() - scrollViewHeight);
        int targetScrollY = Math.clamp(targetTop - dp(80), 0, maxScroll);

        resetStaggerState();
        scrollSpring.jumpTo(targetScrollY);
        scrollTo(0, targetScrollY);
        currentScrollPosition = targetScrollY;
        // Keep the loop's settle check consistent with the value we just forced.
        lastTargetScrollPosition = targetScrollY;
        snapRows();
    }

    private void scrollToLyric(LyricLineView target) {
        if (target == null || scrollSpring == null) return;
        int targetTop = target.getScrollPosition(this);

        int scrollViewHeight = getHeight();
        if (scrollViewHeight <= 0) {
            post(() -> scrollToLyric(target));
            return;
        }
        int maxScroll = Math.max(0, container.getHeight() - scrollViewHeight);
        int targetScrollY = Math.clamp(targetTop - dp(80), 0, maxScroll);
        lastTargetScrollPosition = targetScrollY;

        long now = MuiModApi.getFrameTimeNanos();
        int targetIndex = lyricLineViewList.indexOf(target);
        int n = rows.size();

        calcLoggedDelay(targetIndex, justHighlightedLyricLine);

        if (scrollStatus == ScrollStatus.IDLE || scrollStatus == ScrollStatus.RECENTER) {
            scrollStatus = ScrollStatus.FOLLOW_LYRICS;
        }
        // Rebase the live compensation for this wave while keeping every running spring continuous:
        // the old compensation is shifted into the base spring via translate() (value, velocity and
        // the running segment are preserved), so a rapid highlight never freezes a row.
        long[] previousStartNanos = staggerStartNanos;
        float cumulativeBeforeRebase = cumulativeBaseOffset;
        float previousBase = baseOffsetAtRedirect;

        staggerStarted = new boolean[n];
        staggerStartNanos = new long[n];
        for (int i = 0; i < n; i++) {
            float delay = delayMillis[Math.min(i, delayMillis.length - 1)];
            if (scrollStatus == ScrollStatus.RECENTER) {
                delay /= 2;
            }
            long newStart = now + (long) (delay * 1_000_000L);
            // Only carry over the previous deadline while that row is still waiting (its deadline is
            // still in the future): it may move earlier but never later, so it cannot be starved. A
            // row whose previous deadline already passed has started, and gets a fresh delay so
            // rapid highlights keep respecting the per-row stagger instead of moving in lockstep.
            if (previousStartNanos != null && i < previousStartNanos.length
                    && previousStartNanos[i] > now
                    && previousStartNanos[i] < newStart) {
                newStart = previousStartNanos[i];
            }
            staggerStartNanos[i] = newStart;

            RowWave row = rows.get(i);
            float progress = Math.clamp(row.getProgress(), 0f, 1f);
            row.translate((cumulativeBeforeRebase - previousBase) * (1f - progress));
            // Re-decouple immediately from the very first frame instead of fading the factor in:
            // the compensation was just rebased to zero, so this snap keeps position/velocity
            // continuous and leaves the running base spring untouched.
            row.snapProgress(0f);
        }
        baseOffsetAtRedirect = cumulativeBaseOffset;
        staggeredActive = true;

        scrollSpring.setTarget(targetScrollY, now);
    }

    private void calcLoggedDelay(int targetIndex, LyricLine highlighted) {
        int totalLines = lyricLineViewList.size();
        delayMillis = new float[totalLines];
        double max = Math.max(5, Math.log(1 + totalLines));
        boolean inRecenter = scrollStatus == ScrollStatus.RECENTER;
        float factor = durationFactor(highlighted) * (inRecenter ? 0.5f : 1);
        for (int i = 0; i < totalLines; i++) {
            int distance = Math.abs(i - targetIndex + (inRecenter ? 0 : 1));
            float delayFactor = (float) Math.clamp(Math.log(1 + distance) / max, 0, 1);
            delayMillis[i] = delayFactor * MAX_DELAY_MILLIS * (i < targetIndex ? 0.5f : 1) * factor;
        }
    }

    /**
     * Only a highlighted line that lasts tens-to-hundreds of milliseconds visibly shortens the
     * wave; normal lines keep the full delay.
     */
    private static float durationFactor(@Nullable LyricLine highlighted) {
        if (highlighted == null) return 1f;
        Duration duration = highlighted.getDuration();
        if (duration == null) return 1f;
        float x = (float) duration.toMillis() / DELAY_SHORTEN_TAU_MILLIS;
        return 1f - (float) Math.exp(-x * x);
    }

    /** Snap every row to rest at its target offset (used when the scroll position is forced). */
    private void snapRows() {
        clearStaggerState();
        for (RowWave row : rows) {
            float offset = row.view.getTargetOffset(justHighlightedLyricLine);
            row.jumpTo(offset);
            row.view.setTranslationY(offset);
        }
    }

    /** Drop every in-flight stagger scheduling array; the rows keep their current value/velocity. */
    private void clearStaggerState() {
        staggeredActive = false;
        delayMillis = null;
        staggerStarted = null;
        staggerStartNanos = null;
        cumulativeBaseOffset = 0f;
        baseOffsetAtRedirect = 0f;
        prevScrollValue = 0f;
        prevScrollInitialized = false;
    }

    private void checkManualScrolling() {
        if (scrollStatus == ScrollStatus.IDLE) {
            // Only an active finger drag counts; programmatic jumps, aborts and layout-driven
            // scroll corrections must not switch to MANUAL and cancel the edge fade.
            if (pointerDown) {
                markManual();
            }
        } else if (scrollStatus == ScrollStatus.MANUAL) {
            lastUserScrollTime = MuiModApi.getElapsedTime();
        }
    }

    private void markManual() {
        scrollStatus = ScrollStatus.MANUAL;
        clearStaggerState();
        lastUserStartScrollTime = MuiModApi.getElapsedTime();
        lastUserScrollTime = MuiModApi.getElapsedTime();
        removeCallbacks(autoRecenterRunnable);
        postDelayed(autoRecenterRunnable, AUTO_RECENTER_DELAY_MILLIS);
        if (scrollSpring != null) {
            scrollSpring.jumpTo(getScrollY());
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pointerDown = true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            pointerDown = false;
        }
        if (action == MotionEvent.ACTION_DOWN
                && (scrollStatus == ScrollStatus.FOLLOW_LYRICS || scrollStatus == ScrollStatus.RECENTER)) {
            markManual();
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_SCROLL
                && (scrollStatus == ScrollStatus.IDLE
                || scrollStatus == ScrollStatus.FOLLOW_LYRICS
                || scrollStatus == ScrollStatus.RECENTER)) {
            markManual();
        }
        return super.onGenericMotionEvent(ev);
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
        for (LyricLineView view : lyricLineViewList) {
            view.releaseBlurSurfaces();
        }
    }

    private void startUpdateLoop() {
        if (!continueUpdate) {
            continueUpdate = true;
            scrollFinished = false;
            updateLoop();
        }
    }

    private void updateLoop() {
        try {
            Choreographer.getInstance().postFrameCallback((choreographer, frameTimeNanos) -> {
                if (continueUpdate) {
                    if (scrollStatus != ScrollStatus.MANUAL) {
                        float value = scrollSpring.update(frameTimeNanos);
                        int maxScroll = Math.max(0, container.getHeight() - getHeight());
                        scrollTo(0, Math.clamp((int) value, 0, maxScroll));
                        if (!scrollSpring.isSettled()) {
                            scrollFinished = false;
                        }
                        // Settle when the spring has stopped or already sits on the target (the
                        // latter matters when a new highlight maps to the same scroll position, so
                        // the state machine still gets a chance to fall back to IDLE).
                        boolean atTarget = Math.abs(scrollSpring.getValue() - lastTargetScrollPosition) < 1f;
                        if (((!scrollSpring.isSettled() && !scrollFinished) || atTarget)
                                && (scrollStatus == ScrollStatus.FOLLOW_LYRICS || scrollStatus == ScrollStatus.RECENTER)) {
                            scrollFinished = true;
                            if (!staggeredActive) {
                                scrollStatus = ScrollStatus.IDLE;
                            } else {
                                staggeringEndListener = () -> {
                                    if (scrollStatus != ScrollStatus.MANUAL) {
                                        scrollStatus = ScrollStatus.IDLE;
                                    }
                                };
                            }
                        }
                    } else {
                        // A finger owns the scroll: keep the spring in sync so auto-follow resumes
                        // from where the user left it instead of fighting the gesture.
                        scrollSpring.jumpTo(getScrollY());
                    }
                    updateTranslations(frameTimeNanos);
                    updateBlur(frameTimeNanos);

                    invalidate();
                    updateLoop();
                }
            });
        } catch (IllegalStateException e) {
            if (continueUpdate) {
                throw e;
            }
        }
    }

    private void stopUpdateLoop() {
        continueUpdate = false;
        resetStaggerState();
    }

    private void updateTranslations(long currentTimeNanos) {
        if (rows.isEmpty()) {
            return;
        }
        if (!staggeredActive) {
            // Idle: drive each row's base spring onto its target offset (RHYTHM lines sit dp(30)
            // below the active one) without the stagger wave. Driving the spring - and not the View
            // directly - keeps the base in sync with what is rendered, so a later wave never resumes
            // from a stale offset (e.g. after a manual scroll eased a row to dp(30)).
            LyricLine targetLine = justHighlightedLyricLine;
            int count = Math.min(rows.size(), lyricLineViewList.size());
            for (int i = 0; i < count; i++) {
                RowWave row = rows.get(i);
                float targetOffset = row.view.getTargetOffset(targetLine);
                if (Math.abs(row.getTarget() - targetOffset) > 0.01f) {
                    row.retarget(targetOffset, currentTimeNanos);
                }
                row.update(currentTimeNanos);
                row.view.setTranslationY(row.getValue());
            }
            cumulativeBaseOffset = 0f;
            prevScrollInitialized = false;
            return;
        }

        float currentScrollValue = scrollSpring.getValue();
        float decouple = prevScrollInitialized
                ? (currentScrollValue - prevScrollValue) * SCROLL_DECOUPLE_FACTOR : 0f;
        prevScrollValue = currentScrollValue;
        prevScrollInitialized = true;

        // Accumulate the parent-scroll compensation for the whole wave (until the scroll settles).
        // This is a single shared scalar; rows release their own share with their own progress, so it
        // never freezes a row that is still animating.
        boolean scrollSettled = scrollSpring.isSettled();
        if (!scrollSettled) {
            cumulativeBaseOffset += decouple;
        }
        float scrollCompensation = cumulativeBaseOffset - baseOffsetAtRedirect;

        boolean anyActive = false;
        int n = rows.size();
        for (int i = 0; i < n; i++) {
            RowWave row = rows.get(i);
            LyricLineView view = row.view;
            long startNanos = staggerStartNanos != null && i < staggerStartNanos.length
                    ? staggerStartNanos[i] : 0L;
            if (currentTimeNanos < startNanos) {
                // Not its turn yet: the row keeps following the parent scroll through the live
                // compensation; its running spring is never interrupted.
                anyActive = true;
            } else if (i < staggerStarted.length && !staggerStarted[i]) {
                // Its turn: move toward the target offset and release this row's decoupling. Both
                // retargets preserve the current value/velocity, so a rapid switch stays continuous.
                row.retarget(view.getTargetOffset(justHighlightedLyricLine), currentTimeNanos);
                row.retargetProgress(1f, currentTimeNanos);
                staggerStarted[i] = true;
            }
            row.update(currentTimeNanos);
            float progress = Math.clamp(row.getProgress(), 0f, 1f);
            view.setTranslationY(row.getValue() + scrollCompensation * (1f - progress));
            if (!row.isSettled()) {
                anyActive = true;
            }
        }

        // The wave is only finished once the parent scroll has settled as well, so the hand-off to
        // the idle path never jumps (by then the released render already equals the target offset).
        if (!scrollSettled) {
            anyActive = true;
        }

        boolean wasActive = staggeredActive;
        staggeredActive = anyActive;
        if (wasActive && !anyActive) {
            clearStaggerState();
            Runnable listener = staggeringEndListener;
            staggeringEndListener = null;
            if (listener != null) {
                listener.run();
            }
        }
    }

    /**
     * Drives the per-row defocus blur. The highlighted row stays sharp; the radius grows with the
     * row distance and saturates five rows away. While the user is manually scrolling (or when no
     * line is highlighted) every row is driven sharp.
     */
    private void updateBlur(long nowNanos) {
        boolean blurEnabled = clientConfig.getEnableLyricBlur();
        boolean manual = scrollStatus == ScrollStatus.MANUAL;
        int referenceIndex = resolveBlurReferenceIndex();
        float maxRadius = dp(MAX_BLUR_DP);
        int scrollY = getScrollY();
        int viewportBottom = scrollY + getHeight();
        // Off-screen rows are snapped sharp instead of spring-animated, so the number of blurred
        // (and therefore re-convolved) rows is bounded by the viewport rather than the whole song.
        int margin = dp(160);
        int count = lyricLineViewList.size();
        for (int i = 0; i < count; i++) {
            LyricLineView view = lyricLineViewList.get(i);
            int top = getRelativeTop(view);
            int bottom = top + view.getHeight();
            boolean visible = view.getHeight() > 0
                    && bottom >= scrollY - margin
                    && top <= viewportBottom + margin;
            if (visible && blurEnabled && !manual && referenceIndex >= 0 && view.isBlurEligible()) {
                float target = LineBlurRenderer.radiusForDistance(Math.abs(i - referenceIndex), maxRadius);
                view.setBlurTarget(target, nowNanos);
                if (view.isBlurInitialized()) {
                    view.updateBlur(nowNanos);
                } else {
                    // First time this row is blurred: appear already defocused instead of ramping up
                    // from sharp, which otherwise looks like the blur is missing right after init.
                    view.snapBlur();
                    view.markBlurInitialized();
                }
            } else {
                view.setBlurTarget(0f, nowNanos);
                view.snapBlur();
            }
        }
    }

    /**
     * The row the blur is focused on: the currently highlighted line, else the last highlighted
     * line, else the first row - so the lyrics start defocused downward before the first line.
     */
    private int resolveBlurReferenceIndex() {
        int index = indexOfLyricView(nowPlayingInfo.getCurrentLyricLine());
        if (index >= 0) {
            return index;
        }
        index = indexOfLyricView(justHighlightedLyricLine);
        if (index >= 0) {
            return index;
        }
        return lyricLineViewList.isEmpty() ? -1 : 0;
    }

    private int indexOfLyricView(@Nullable LyricLine line) {
        if (line == null) {
            return -1;
        }
        LyricLineView view = lyricLines.get(line);
        return view == null ? -1 : lyricLineViewList.indexOf(view);
    }

    // Near-identity projective matrix (persp0 = 1e-8): makes the position matrix
    // report hasPerspective() so arc3d skips the direct-mask glyph path (which floors
    // every glyph to whole device pixels) and uses the transformed-mask path, which
    // keeps fractional positions and samples the atlas with bilinear filtering.
    // The epsilon must live in m14 (persp0), NOT m44 (persp2): Device normalizes the
    // CTM via Matrix.normalizePerspective(), which divides m44 back to exactly 1
    // whenever m14/m24 are zero, silently undoing an m44-based flag. With m14 != 0
    // the normalization is skipped entirely. The resulting w-distortion
    // (~1e-8 * scale^2 * x) is far below one physical pixel.
    private static final Matrix SUBPIXEL_MATRIX = new Matrix(
            1f, 0f, 1e-8f,
            0f, 1f, 0f,
            0f, 0f, 1f
    );

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.concat(SUBPIXEL_MATRIX);
        super.dispatchDraw(canvas);
        canvas.restore();
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

    @Override
    public void requestLayout() {
        super.requestLayout();
        if (lyricLines != null) {
            lyricLines.forEach((l, lv) -> lv.requestLayout());
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int viewport = bottom - top;
        if (viewport > 0 && bottomSpacer != null && bottomSpacer.getParent() == container) {
            int target = (int) (viewport * SPACER_HEIGHT_RATIO);
            ViewGroup.LayoutParams lp = bottomSpacer.getLayoutParams();
            if (lp.height != target) {
                lp.height = target;
                bottomSpacer.requestLayout();
            }
        }
        if (pendingResyncToCurrent) {
            pendingResyncToCurrent = false;
            post(this::resyncToCurrentLyric);
        }
    }

    public void refreshLinesStyle() {
        lyricLineViewList.forEach(LyricLineView::refreshSubLyricLine);
        post(() -> recenter(true));
    }

    public void reinitialize() {
        if (container == null || lyricLineViewList.isEmpty()) {
            return;
        }
        resetStaggerState();
        scrollStatus = ScrollStatus.IDLE;
        post(() -> {
            requestLayout();
            post(this::resyncToCurrentLyric);
        });
    }

    public void suspendLyricFollowingAndHide() {
        stopUpdateLoop();
        removeCallbacks(autoRecenterRunnable);
        resetStaggerState();
        scrollStatus = ScrollStatus.IDLE;
    }

    private void resetStaggerState() {
        staggeringEndListener = null;
        clearStaggerState();
        if (scrollSpring != null) {
            // Sync the spring to the actual scroll position; keep our mirror consistent so the
            // scroll-change listener filters the forced jump instead of flagging a manual scroll.
            currentScrollPosition = getScrollY();
            scrollSpring.jumpTo(currentScrollPosition);
        }
    }

    enum ScrollStatus {
        IDLE, MANUAL, RECENTER, FOLLOW_LYRICS
    }
}