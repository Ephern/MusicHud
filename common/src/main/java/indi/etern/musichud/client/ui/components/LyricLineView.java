package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.Animator;
import icyllis.modernui.animation.AnimatorSet;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.dto.LyricLine;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.ui.lyric.LineBlurRenderer;
import indi.etern.musichud.client.ui.lyric.LineBlurResources;
import indi.etern.musichud.client.utils.ui.RhythmAnimator;
import indi.etern.musichud.client.utils.ui.SpringInterpolator;
import indi.etern.musichud.client.utils.ui.SpringValue;
import indi.etern.musichud.interfaces.ClientConfig;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class LyricLineView extends LinearLayout {
    private static final float LYRIC_EMPHASIZE_SCALE = 1.02f;
    private static final float RHYTHM_EMPHASIZE_ANIMATION_SCALE = 0.85f;
    private static final int SCALE_ANIMATION_DELAY = 200;
    private static final int SCALE_ANIMATION_DURATION = 600;
    private static final SpringInterpolator INTERPOLATOR = new SpringInterpolator(SCALE_ANIMATION_DURATION * 0.001f, 1);
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static Logger logger;
    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private final int height = dp(30);
    TextView subText;
    private LinearLayout mainLine;
    private LinearLayout row;
    private LyricLine lyricLine;
    private View mainText;
    private Animator emphasizeAnim;

    private static final float BLUR_SPRING_RESPONSE_SECONDS = 0.3f;
    private final SpringValue blurSpring = new SpringValue(BLUR_SPRING_RESPONSE_SECONDS, 1f);
    private final LineBlurResources blurResources = new LineBlurResources();
    private float blurRadius = 0f;
    private float blurTarget = 0f;
    private boolean blurDisabled = false;
    private boolean blurInitialized = false;

    public LyricLineView(Context context, LyricLine lyricLine) {
        super(context);
        try {
            this.lyricLine = lyricLine;
            setOrientation(LinearLayout.VERTICAL);

            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            {
                mainLine = new LinearLayout(getContext());
                mainLine.setOrientation(LinearLayout.VERTICAL);

                if (lyricLine.getType() == LyricLine.Type.RHYTHM) {
                    row.setScaleX(RHYTHM_EMPHASIZE_ANIMATION_SCALE);
                    row.setScaleY(RHYTHM_EMPHASIZE_ANIMATION_SCALE);
                    LinearLayout rhythmLine = new LinearLayout(getContext());
                    rhythmLine.setOrientation(LinearLayout.HORIZONTAL);
                    rhythmLine.setAlpha(0);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, height);
                    rhythmLine.setLayoutParams(params);

                    for (int i = 0; i < 3; i++) {
                        TextView dot = new TextView(getContext());
                        dot.setText("●");
                        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                        dotParams.setMargins(dp(2), 0, dp(2), 0);
                        dot.setLayoutParams(dotParams);
                        dot.setAlpha(Theme.FADE_LYRIC_ALPHA);
                        dot.setId(i);
                        rhythmLine.addView(dot);
                    }
                    this.mainText = rhythmLine;
                    mainLine.addView(rhythmLine);
                } else {
                    LyricHighlightTextView mainText = new LyricHighlightTextView(getContext(), lyricLine);
                    this.mainText = mainText;
                    mainText.setTextStyle(TextPaint.BOLD);
                    mainText.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
//                mainText.setAlpha(Theme.FADE_LYRIC_ALPHA);
                    mainLine.addView(mainText);
                    if (lyricLine.getType() == LyricLine.Type.META_DATA) {
                        mainText.setTextSize(Theme.SUB_LYRIC_SIZE);
                    } else {
                        mainText.setTextSize(Theme.MAIN_LYRIC_SIZE);

                        refreshSubLyricLine();
                    }
                }
                LayoutParams params = new LayoutParams(0, WRAP_CONTENT, 0.9f);
                row.addView(mainLine, params);
            }

            View blank = new View(getContext());
            LayoutParams blankParams = new LayoutParams(0, MATCH_PARENT, 0.1f);
            row.addView(blank, blankParams);

            FrameLayout topBlank = new FrameLayout(context);
            topBlank.setLayoutParams(new LayoutParams(0, dp(32)));
            LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            setLayoutParams(params);

            addView(topBlank);
            LayoutParams rowParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            addView(row, rowParams);
            if (lyricLine.getType() == LyricLine.Type.RHYTHM) {
                rowParams.setMargins(dp(2), 0, 0, 0);
                params.setMargins(0, 0, 0, -dp(64));
            }
        } catch (Exception e) {
            if (logger == null) {
                logger = MusicHud.getLogger(HudRendererManager.class);
            }
            logger.error("While configure LyricLineView", e);
        }
    }

    public void refreshSubLyricLine() {
        if (clientConfig.getShowTranslatedCnLyrics()) {
            if (subText == null) {
                String subLyric = lyricLine.getTranslatedText();
                if (subLyric != null && !subLyric.isEmpty()) {
                    subText = new TextView(getContext());
                    subText.setText(subLyric);
                    subText.setTextSize(Theme.SUB_LYRIC_SIZE);
                    subText.setTextStyle(TextPaint.BOLD);
                    subText.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
                    subText.setAlpha(Theme.FADE_LYRIC_ALPHA);
                    LayoutParams subParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                    subParams.setMargins(0, dp(6), dp(32), 0);
                    mainLine.addView(subText, subParams);
                }
            }
            if (subText != null/* && subText.getParent() == null*/) {
                subText.setVisibility(VISIBLE);
            }
        } else {
            if (subText != null) {
                subText.setVisibility(GONE);
//                mainLine.removeView(subText);
            }
        }
    }

    public synchronized void emphasize() {
        if (emphasizeAnim != null) return;
        Duration delta = nowPlayingInfo.getPlayedDuration().minus(lyricLine.getStartTime());
        Duration duration = lyricLine.getDuration();
        Duration stayEmphasizeDuration =
                duration == null ?
                        nowPlayingInfo.getMusicDuration().minus(lyricLine.getStartTime())
                        : duration.minus(delta);
        stayEmphasizeDuration = stayEmphasizeDuration.minus(Duration.of(800, ChronoUnit.MILLIS));
        if (stayEmphasizeDuration.isNegative()) {
            stayEmphasizeDuration = Duration.ofMillis(900);
        }
        switch (lyricLine.getType()) {
            case META_DATA -> {
            }
            case NORMAL -> {
                if (mainText instanceof LyricHighlightTextView highlightTextView) {
                    highlightTextView.emphasize();
                    highlightTextView.setOnFade(this::fadeNormalLine);
                }
                row.setPivotX(0f);
                int height = row.getHeight();
                row.setPivotY(Math.max(height, dp(24)));

                int playTime = Math.clamp(delta.toMillis() - SCALE_ANIMATION_DELAY, 0, SCALE_ANIMATION_DURATION);
                if (playTime != SCALE_ANIMATION_DURATION) {
                    ObjectAnimator scaleX = ObjectAnimator.ofFloat(row, View.SCALE_X, 1f, LYRIC_EMPHASIZE_SCALE);
                    scaleX.setInterpolator(INTERPOLATOR);
                    ObjectAnimator scaleY = ObjectAnimator.ofFloat(row, View.SCALE_Y, 1f, LYRIC_EMPHASIZE_SCALE);
                    scaleY.setInterpolator(INTERPOLATOR);

                    AnimatorSet emphasizeAnimSet = new AnimatorSet();
                    emphasizeAnim = emphasizeAnimSet;
                    emphasizeAnimSet.playTogether(scaleX, scaleY/*, alphaAnim*/);
                    emphasizeAnimSet.setDuration(SCALE_ANIMATION_DURATION);
                    emphasizeAnimSet.setStartDelay(SCALE_ANIMATION_DELAY);
                    emphasizeAnimSet.setCurrentPlayTime(playTime);
                    emphasizeAnimSet.start();
                }
            }
            case RHYTHM -> {
                long stayMillis = stayEmphasizeDuration.toMillis();
                stayMillis -= RhythmAnimator.FADE_OUT_PEAK_MS;
                RhythmAnimator rhythmAnim = new RhythmAnimator(row, mainText, stayMillis);
                if (!rhythmAnim.isValid()) {
                    return;
                }

                row.setPivotX(Math.max((float) mainText.getWidth() / 2, dp(30)));
                row.setPivotY(Math.max(row.getHeight() / 2, dp(12)));
                rhythmAnim.start();
                emphasizeAnim = rhythmAnim;

                long dotDuration = rhythmAnim.getDotFadeDuration(stayMillis);
                for (int i = 0; i < 3; i++) {
                    View viewById = mainText.findViewById(i);
                    if (viewById != null) {
                        ObjectAnimator dotAlpha = ObjectAnimator.ofFloat(viewById, View.ALPHA,
                                viewById.getAlpha(), Theme.EMPHASIZE_LYRIC_ALPHA);
                        dotAlpha.setDuration(dotDuration);
                        dotAlpha.setStartDelay(800 + stayMillis * i / 3);
                        dotAlpha.start();
                    }
                }
                return;
            }
        }
        if (stayEmphasizeDuration.isPositive()) {
            postDelayed(this::fade, stayEmphasizeDuration.toMillis() + SCALE_ANIMATION_DELAY);
        } else {
            post(this::fade);
        }
    }

    private void fadeNormalLine() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(row, View.SCALE_X, row.getScaleX(), 1f);
        scaleX.setInterpolator(INTERPOLATOR);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(row, View.SCALE_Y, row.getScaleY(), 1f);
        scaleY.setInterpolator(INTERPOLATOR);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(SCALE_ANIMATION_DURATION);
        set.start();
    }

    public void fade() {
        if (emphasizeAnim != null) {
            emphasizeAnim.cancel();
            emphasizeAnim = null;
        }
        if (Math.abs(row.getScaleX() - 1f) > 0.001f || Math.abs(row.getScaleY() - 1f) > 0.001f) {
            fadeNormalLine();
        }
    }

    public int getScrollPosition(StaggeredLyricScrollView staggeredLyricScrollView) {
        return staggeredLyricScrollView.getRelativeTop(this);
    }

    public float getTargetOffset(LyricLine activeLyricLine) {
        if (activeLyricLine != null && activeLyricLine.getType() == LyricLine.Type.RHYTHM && lyricLine.isAfter(activeLyricLine)) {
            return height - dp(2);
        } else {
            return 0;
        }
    }

    /** Only normal lyric rows (the ones with a blurred main + translation text) are blurred. */
    public boolean isBlurEligible() {
        return lyricLine != null && lyricLine.getType() == LyricLine.Type.NORMAL;
    }

    /** Retargets the blur radius spring; a no-op when the target does not change. */
    public void setBlurTarget(float target, long nowNanos) {
        target = Math.max(0f, target);
        if (Math.abs(target - blurTarget) < 0.01f) {
            return;
        }
        blurTarget = target;
        blurSpring.setTarget(target, nowNanos);
    }

    /** Advances the blur radius animation. Must be called once per frame by the parent. */
    public void updateBlur(long nowNanos) {
        blurRadius = Math.max(0f, blurSpring.update(nowNanos));
    }

    /** Snaps the blur radius to its target without animating (used for off-screen rows). */
    public void snapBlur() {
        blurRadius = blurTarget;
        blurSpring.jumpTo(blurTarget);
    }

    public boolean isBlurInitialized() {
        return blurInitialized;
    }

    public void markBlurInitialized() {
        blurInitialized = true;
    }

    /** Releases the offscreen GPU resources; the row falls back to a sharp draw afterward. */
    public void releaseBlurResources() {
        blurDisabled = true;
        blurResources.close();
    }

    /** Releases the offscreen render targets but keeps the row blur-capable (e.g. temporary hide). */
    public void releaseBlurSurfaces() {
        blurResources.release();
    }

    @Override
    protected void dispatchDraw(@NotNull Canvas canvas) {
        //noinspection UnstableApiUsage
        if (blurDisabled
                || blurRadius < 0.5f
                || !isBlurEligible()
                || !(canvas instanceof ArcCanvas arcCanvas)
                || getWidth() <= 0
                || getHeight() <= 0) {
            super.dispatchDraw(canvas);
            return;
        }
        try {
            if (!LineBlurRenderer.drawBlurred(arcCanvas, getWidth(), getHeight(),
                    blurRadius, computeBlurContentStamp(), blurResources, this::drawBlurredContent)) {
                super.dispatchDraw(canvas);
            }
        } catch (Throwable t) {
            blurDisabled = true;
            blurResources.release();
            if (logger == null) {
                logger = MusicHud.getLogger(HudRendererManager.class);
            }
            logger.error("Disabling lyric blur after an error", t);
            super.dispatchDraw(canvas);
        }
    }

    private void drawBlurredContent(Canvas canvas) {
        super.dispatchDraw(canvas);
    }

    /**
     * Cheap signature of everything that affects the rendered line, so the offscreen source is
     * only re-rendered when it actually changes. It is forced to change every frame while the
     * child is still animating (PERFORMING, or the DONE fade/lowering) and also tracks the row's
     * scale, which can outlast the color fade during the highlight exit.
     */
    private long computeBlurContentStamp() {
        long stamp = 17L;
        if (mainText instanceof LyricHighlightTextView highlight) {
            stamp = stamp * 31 + highlight.getStatus().ordinal();
            stamp = stamp * 31 + highlight.getCurrentTextColor();
            if (highlight.isVisuallyAnimating()) {
                stamp = stamp * 31 + System.nanoTime();
            }
        } else if (mainText != null) {
            stamp = stamp * 31 + System.identityHashCode(mainText);
        }
        if (row != null) {
            stamp = stamp * 31 + Float.floatToIntBits(row.getScaleX());
            stamp = stamp * 31 + Float.floatToIntBits(row.getScaleY());
        }
        if (subText != null) {
            stamp = stamp * 31 + subText.getVisibility();
            stamp = stamp * 31 + subText.getCurrentTextColor();
        }
        return stamp;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        releaseBlurResources();
    }
}