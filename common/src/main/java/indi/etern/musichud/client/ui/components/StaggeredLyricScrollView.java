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
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.utils.ui.Easing;
import indi.etern.musichud.client.utils.ui.SpringInterpolator;
import indi.etern.musichud.client.utils.ui.SpringValue;
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
    private static final float SCROLL_RESPONSE_SECONDS = 0.6f;
    private static final float SCROLL_DAMPING = 1f;
    // Per-row stagger spring: one fixed curve shared by every row (slight overshoot), so a row's
    // amplitude and duration never depend on its distance from the highlighted row. Only the
    // per-row start delay differs, and that delay is scheduled by the scroll view, never the row.
    private static final float ROW_RESPONSE_SECONDS = SCROLL_RESPONSE_SECONDS;
    private static final float ROW_DAMPING = 0.75f;
    // A very short highlighted line collapses the whole delay wave (see durationFactor) so a fast
    // sequence of short lines never blocks; the per-row delay ratios are preserved.
    private static final float DELAY_SHORTEN_TAU_SECONDS = 0.2f;
    private static final SpringInterpolator SWITCH_INTERPOLATOR =
            new SpringInterpolator((float) SWITCH_DURATION / 1000, 0.9f);
    private static Logger logger;
    private final Map<LyricLine, LyricLineView> lyricLines = new LinkedHashMap<>();
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
                    recenter();
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
    // Per-row stagger state. Each RowWave holds only a spring; every delay - when to retarget the
    // row - and every offset is computed and scheduled here, never inside the row.
    private float[] delayMillis;
    private boolean[] staggerStarted;
    private long[] staggerStartNanos;
    private long lastFrameTimeNanos;
    // Per-frame scroll delta used to decouple every row from the container scroll during auto-scroll.
    private float prevScrollValue;
    private boolean prevScrollInitialized;
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
        params.setMargins(dp(1), 0, dp(1), 0);
        addView(container, params);

        nowPlayingInfo.getLyricLineUpdateListener().add(lyricLineUpdateListener);

        addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            int height = bottom - top;
            int lastWidth = oldRight - oldLeft;
            int lastHeight = oldBottom - oldTop;
            if ((width != lastWidth || height != lastHeight)
                    && (scrollStatus == ScrollStatus.IDLE || scrollStatus == ScrollStatus.MANUAL)) {
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

        scrollSpring = new SpringValue(SCROLL_RESPONSE_SECONDS, SCROLL_DAMPING);
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
            LyricLineView row = new LyricLineView(context, line);
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

    /**
     * One lyric row's animation state: a {@link SpringValue} driving the row's translationY, plus
     * the view it drives. It knows nothing about delays or scroll - the scroll view decides when to
     * {@link #retarget} it and how much to {@link #translate} it.
     */
    private static final class RowWave {
        final LyricLineView view;
        final SpringValue spring;

        RowWave(LyricLineView view) {
            this.view = view;
            this.spring = new SpringValue(ROW_RESPONSE_SECONDS, ROW_DAMPING);
        }

        void retarget(float target, long nowNanos) {
            spring.setTarget(target, nowNanos);
        }

        void translate(float delta) {
            spring.translate(delta);
        }

        void update(long nowNanos) {
            spring.update(nowNanos);
        }

        boolean isSettled() {
            return spring.isSettled();
        }

        void jumpTo(float value) {
            spring.jumpTo(value);
        }

        float getValue() {
            return spring.getValue();
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

    private void recenter() {
        LyricLine targetLine = justHighlightedLyricLine;
        if (targetLine == null) return;
        // Resolve the target before switching state: entering RECENTER without a valid
        // target would leave the state machine stuck (no scroll is started to complete it).
        LyricLineView target = lyricLines.get(targetLine);
        if (target == null) return;
        if (scrollStatus == ScrollStatus.RECENTER) return;
        scrollStatus = ScrollStatus.RECENTER;
        scrollFinished = false;
        scrollToLyric(target);
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

        if (scrollStatus == ScrollStatus.IDLE || scrollStatus == ScrollStatus.RECENTER) {
            scrollStatus = ScrollStatus.FOLLOW_LYRICS;
        }

        long now = MuiModApi.getFrameTimeNanos();
        int targetIndex = lyricLineViewList.indexOf(target);
        int n = rows.size();

        calcLoggedDelay(targetIndex, justHighlightedLyricLine);
        // Schedule each row's animation update on its own clock. A follow-up highlight only moves
        // each row's update time; it never resets another row's running spring. The delay grows with
        // the row's distance, so farther rows update (and rejoin the container scroll) later.
        staggerStarted = new boolean[n];
        staggerStartNanos = new long[n];
        for (int i = 0; i < n; i++) {
            float delay = delayMillis[Math.min(i, delayMillis.length - 1)];
            if (scrollStatus == ScrollStatus.RECENTER) {
                delay /= 2;
            }
            staggerStartNanos[i] = now + (long) (delay * 1_000_000L);
        }
        staggeredActive = true;

        scrollSpring.setTarget(targetScrollY, now);
    }

    private void calcLoggedDelay(int targetIndex, LyricLine highlighted) {
        int totalLines = lyricLineViewList.size();
        delayMillis = new float[totalLines];
        double max = Math.max(5, Math.log(1 + totalLines));
        float factor = durationFactor(highlighted);
        for (int i = 0; i < totalLines; i++) {
            int distance = Math.abs(i - targetIndex + 1);
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
        float seconds = duration.toMillis() / 1000f;
        float x = seconds / DELAY_SHORTEN_TAU_SECONDS;
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
            // Idle: ease each row onto its target offset (RHYTHM lines sit dp(30) below the active
            // one) without the stagger wave.
            float deltaSeconds = lastFrameTimeNanos == 0
                    ? 0f : (currentTimeNanos - lastFrameTimeNanos) / 1_000_000_000f;
            lastFrameTimeNanos = currentTimeNanos;
            float smoothFactor = 1.0f - (float) Math.exp(-deltaSeconds * 10.0);
            LyricLine targetLine = justHighlightedLyricLine;
            for (LyricLineView line : lyricLineViewList) {
                float targetOffset = line.getTargetOffset(targetLine);
                float currentOffset = line.getTranslationY();
                float newOffset = currentOffset + (targetOffset - currentOffset) * smoothFactor;
                if (Math.abs(newOffset - targetOffset) < 0.01f) {
                    newOffset = targetOffset;
                }
                line.setTranslationY(newOffset);
            }
            prevScrollInitialized = false;
            return;
        }

        float currentScrollValue = scrollSpring.getValue();
        float decouple = prevScrollInitialized ? (currentScrollValue - prevScrollValue) / 2.0f : 0f;
        prevScrollValue = currentScrollValue;
        prevScrollInitialized = true;

        boolean anyActive = false;
        int n = rows.size();
        for (int i = 0; i < n; i++) {
            RowWave row = rows.get(i);
            LyricLineView view = row.view;
            long startNanos = staggerStartNanos != null && i < staggerStartNanos.length
                    ? staggerStartNanos[i] : 0L;
            if (currentTimeNanos < startNanos) {
                // Not its turn yet: decouple from the container scroll by shifting the whole spring
                // (value and rest target), so the row stays put on screen without interrupting the
                // spring it is currently running.
                row.translate(decouple);
                anyActive = true;
            } else if (!staggerStarted[i]) {
                // Its turn: drop the decoupling from the target so the spring now pulls the row back
                // onto its target offset (interrupting the old interpolation, keeping the velocity).
                row.retarget(view.getTargetOffset(justHighlightedLyricLine), currentTimeNanos);
                staggerStarted[i] = true;
            }
            row.update(currentTimeNanos);
            view.setTranslationY(row.getValue());
            if (!row.isSettled()) {
                anyActive = true;
            }
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
        post(this::recenter);
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