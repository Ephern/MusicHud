package indi.etern.musichud.client.ui.components;

import icyllis.arc3d.sketch.Image;
import icyllis.modernui.animation.Animator;
import icyllis.modernui.animation.AnimatorSet;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.BlendMode;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
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
import indi.etern.musichud.client.ui.lyric.LineBloomResources;
import indi.etern.musichud.client.ui.lyric.LineBlurRenderer;
import indi.etern.musichud.client.ui.lyric.LineBlurResources;
import indi.etern.musichud.client.ui.render.gpu.SceneBlurSlot;
import indi.etern.musichud.client.ui.render.gpu.SceneTextureFormat;
import indi.etern.musichud.client.utils.ui.RhythmAnimator;
import indi.etern.musichud.client.utils.ui.SpringInterpolator;
import indi.etern.musichud.client.utils.ui.SpringValue;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class LyricLineView extends LinearLayout {
    public static final float BLOOM_DP = 8f;
    private static final long BLOOM_RELEASE_NANOS = 220_000_000L;
    private static final float LYRIC_EMPHASIZE_SCALE = 1.02f;
    private static final float RHYTHM_EMPHASIZE_ANIMATION_SCALE = 0.85f;
    private static final int SCALE_ANIMATION_DELAY = 500;
    private static final int SCALE_ANIMATION_DURATION = 800;
    private static final SpringInterpolator INTERPOLATOR = new SpringInterpolator(SCALE_ANIMATION_DURATION * 0.001f, 1);
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static Logger logger;
    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private final int height = dp(30);
    @Getter
    private final int mainLyricSize;
    @Getter
    private final int subLyricSize;
    private final LyricLine lyricLine;
    private TextView subText;
    private LinearLayout mainLine;
    private LinearLayout row;
    private View mainText;
    private Animator emphasizeAnim;

    private static final float BLUR_SPRING_RESPONSE_SECONDS = 0.3f;
    private final SpringValue blurSpring = new SpringValue(BLUR_SPRING_RESPONSE_SECONDS, 1f);
    private final LineBlurResources blurResources = new LineBlurResources();
    private final SceneBlurSlot blurSlot = new SceneBlurSlot();
    private final LineBloomResources bloomResources = new LineBloomResources();
    private final SceneBlurSlot bloomSlot = new SceneBlurSlot(SceneTextureFormat.RGBA16F, true);
    private final Paint bloomPaint = new Paint();
    private Image frameImage;
    private int frameSurfaceWidth;
    private int frameSurfaceHeight;
    private int framePadding;
    private Image bloomFrameImage;
    private int bloomSurfaceWidth;
    private int bloomSurfaceHeight;
    private int bloomPadding;
    private float blurRadius = 0f;
    private float blurTarget = 0f;
    /**
     * -- SETTER --
     *  Marks the row as taking part in this frame's blur pass. While enabled the row keeps its
     *  offscreen source and result current even at radius zero, so a row that just lost its
     *  highlight composites a near-current image instead of a stale one.
     */
    @Setter
    private boolean blurPipelineEnabled;
    private boolean bloomPipelineEnabled;
    private boolean bloomFading;
    private long bloomFadeStartNanos;
    private boolean blurDisabled = false;
    /** Set once the row has been out of view long enough that its blur targets are worth freeing. */
    private boolean blurTargetsReleased;
    private long blurIdleSinceNanos;
    @Getter
    private boolean blurInitialized = false;

    public LyricLineView(Context context, LyricLine lyricLine, int mainLyricSize, int subLyricSize) {
        super(context);
        bloomPaint.setBlendMode(BlendMode.PLUS);
        this.lyricLine = lyricLine;
        this.mainLyricSize = mainLyricSize;
        this.subLyricSize = subLyricSize;
        try {
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
                        dot.setTextSize(subLyricSize);
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
                        mainText.setTextSize(subLyricSize);
                    } else {
                        mainText.setTextSize(mainLyricSize);

                        refreshSubLyricLine();
                    }
                }
                LayoutParams params = new LayoutParams(0, WRAP_CONTENT, 1f);
                row.addView(mainLine, params);
            }

            View blank = new View(getContext());
            LayoutParams blankParams = new LayoutParams(0, MATCH_PARENT, 0.02f);
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
                logger = MusicHud.getLogger(LyricLineView.class);
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
                    subText.setTextSize(subLyricSize);
                    subText.setTextStyle(TextPaint.BOLD);
                    subText.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
                    subText.setAlpha(Theme.FADE_LYRIC_ALPHA);
                    LayoutParams subParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                    subParams.setMargins(0, dp(6), dp(24), 0);
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
        Duration stayEmphasizeDuration;
        if (duration != null) {
            stayEmphasizeDuration = duration.minus(delta);
        } else {
            // The field can be nulled by a concurrent switch-to-idle/stop; fall back to a short
            // fixed emphasis instead of dereferencing null.
            Duration musicDuration = nowPlayingInfo.getMusicDuration();
            stayEmphasizeDuration = musicDuration == null
                    ? Duration.ZERO
                    : musicDuration.minus(lyricLine.getStartTime());
        }
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
                updatePivot();

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

                updatePivot();
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

    private void updatePivot() {
        if (row == null) {
            return;
        }
        switch (lyricLine.getType()) {
            case NORMAL -> {
                row.setPivotX(0f);
                row.setPivotY(Math.max(row.getHeight(), dp(24)));
            }
            case RHYTHM -> {
                row.setPivotX(mainText == null ? 0f : mainText.getWidth() / 2f);
                row.setPivotY(row.getHeight() / 2f);
            }
            default -> {
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updatePivot();
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

    public void markBlurInitialized() {
        blurInitialized = true;
    }

    private static int ceilDiv(int value, int divisor) {
        return Math.max(1, (value + divisor - 1) / divisor);
    }

    /** Releases the offscreen GPU resources; the row falls back to a sharp draw afterward. */
    public void releaseBlurResources() {
        blurDisabled = true;
        blurResources.close();
        blurSlot.unregister();
        bloomResources.close();
        bloomSlot.unregister();
        frameImage = null;
        bloomFrameImage = null;
        bloomFading = false;
    }

    /**
     * Drops the cached blur source and result. Used when the row becomes visible again: the last
     * published texture may predate the current layout, and nothing else would re-request it.
     */
    public void invalidateBlur() {
        frameImage = null;
        blurResources.invalidate();
        bloomFrameImage = null;
        bloomResources.invalidate();
        bloomFading = false;
        blurInitialized = false;
    }

    /** Releases the offscreen render targets but keeps the row blur-capable (e.g. temporary hide). */
    public void releaseBlurSurfaces() {
        blurResources.release();
        frameImage = null;
        bloomResources.release();
        bloomFrameImage = null;
        bloomFading = false;
    }

    private boolean canPrepareBlur() {
        return blurPipelineEnabled
                && !blurDisabled
                && !blurSlot.isDisabled()
                && isBlurEligible()
                && getWidth() > 0
                && getHeight() > 0;
    }

    /**
     * Frees the blur and bloom targets of a row that scrolled far out of view. Unlike
     * {@link #releaseBlurResources()}, this is reversible: the next request rebuilds them, so a
     * whole song never keeps one pipeline set per line.
     */
    public void releaseBlurTargetsWhenIdle(long nowNanos, long idleNanos) {
        if (blurTargetsReleased || blurDisabled || blurResources.width() <= 0) {
            return;
        }
        if (blurIdleSinceNanos == 0L) {
            blurIdleSinceNanos = Math.max(1L, nowNanos);
            return;
        }
        if (nowNanos - blurIdleSinceNanos < idleNanos) {
            return;
        }
        blurTargetsReleased = true;
        blurInitialized = false;
        frameImage = null;
        bloomFrameImage = null;
        bloomFading = false;
        blurResources.release();
        bloomResources.release();
        blurSlot.unregister();
        bloomSlot.unregister();
    }

    /** The row is back within range: cancel a pending idle release. */
    public void clearBlurIdleClock() {
        blurIdleSinceNanos = 0L;
    }

    public void prepareBlurSource(int padding) {
        frameImage = null;
        if (!canPrepareBlur()) {
            return;
        }
        blurSlot.register();
        blurTargetsReleased = false;
        blurIdleSinceNanos = 0L;
        try {
            if (!blurResources.prepare(getWidth(), getHeight(), padding, blurRadius,
                    computeBlurContentStamp(), super::dispatchDraw, blurSlot)) {
                return;
            }
            // Below the blurrable radius the row is drawn sharp; the pipeline was still fed above so
            // the image is ready the moment the radius crosses over.
            if (!LineBlurRenderer.isBlurrable(blurRadius)) {
                return;
            }
            Image image = blurSlot.image();
            if (image == null) {
                return;
            }
            int scale = Math.max(1, blurSlot.publishedScale());
            if (image.getWidth() != ceilDiv(blurResources.width(), scale)
                    || image.getHeight() != ceilDiv(blurResources.height(), scale)) {
                // The published result belongs to an older target size (e.g. the row was just laid
                // out). Without a forced re-request it would stay unusable until the radius changes.
                blurResources.invalidate();
                return;
            }
            frameImage = image;
            frameSurfaceWidth = blurResources.width();
            frameSurfaceHeight = blurResources.height();
            framePadding = blurResources.padding();
        } catch (Throwable failure) {
            blurDisabled = true;
            blurResources.release();
            blurSlot.shutdown();
            frameImage = null;
            if (logger == null) {
                logger = MusicHud.getLogger(LyricLineView.class);
            }
            logger.error("Disabling lyric blur after an error", failure);
        }
    }

    private boolean canPrepareBloom() {
        return bloomPipelineEnabled
                && !bloomSlot.isDisabled()
                && isBloomEligible()
                && getWidth() > 0
                && getHeight() > 0;
    }

    public boolean isBloomEligible() {
        return lyricLine != null
                && lyricLine.getType() == LyricLine.Type.NORMAL
                && mainText instanceof LyricHighlightTextView highlight
                && highlight.getStatus() == LyricHighlightTextView.HighlightStatus.PERFORMING
                && highlight.hasDurablePhrase();
    }

    public void setBloomPipelineEnabled(boolean enabled) {
        if (bloomPipelineEnabled == enabled) {
            return;
        }
        bloomPipelineEnabled = enabled;
        if (enabled) {
            bloomFading = false;
        } else if (bloomFrameImage != null && !bloomFading) {
            bloomFading = true;
            bloomFadeStartNanos = System.nanoTime();
        }
    }

    private float bloomAlpha() {
        if (!bloomFading) {
            return 1f;
        }
        float t = Math.clamp(
                (System.nanoTime() - bloomFadeStartNanos) / (float) BLOOM_RELEASE_NANOS,
                0f, 1f);
        return 1f - t * t * (3f - 2f * t);
    }

    /**
     * Parent phase 1 for the long-note bloom: renders only the durable-phrase mask offscreen and
     * asks the render thread to blur it. The visible text is not part of this mask.
     */
    public void prepareBloomSource(int padding) {
        if (bloomFading) {
            if (bloomAlpha() <= 0f) {
                bloomFrameImage = null;
                bloomFading = false;
            }
            return;
        }
        if (!canPrepareBloom()) {
            if (bloomFrameImage != null) {
                bloomFading = true;
                bloomFadeStartNanos = System.nanoTime();
            }
            return;
        }
        bloomSlot.register();
        blurTargetsReleased = false;
        blurIdleSinceNanos = 0L;
        bloomFrameImage = null;
        try {
            float sigma = LineBlurRenderer.sigmaFor(dp(BLOOM_DP));
            if (!bloomResources.prepare(getWidth(), getHeight(), padding, sigma,
                    computeBloomContentStamp(), this::drawBloomMaskAtMainText, bloomSlot)) {
                return;
            }
            Image image = bloomSlot.image();
            if (image == null) {
                return;
            }
            int scale = Math.max(1, bloomSlot.publishedScale());
            if (image.getWidth() != ceilDiv(bloomResources.width(), scale)
                    || image.getHeight() != ceilDiv(bloomResources.height(), scale)) {
                bloomResources.invalidate();
                return;
            }
            bloomFrameImage = image;
            bloomSurfaceWidth = bloomResources.width();
            bloomSurfaceHeight = bloomResources.height();
            bloomPadding = bloomResources.padding();
        } catch (Throwable failure) {
            bloomResources.release();
            bloomSlot.shutdown();
            bloomFrameImage = null;
            if (logger == null) {
                logger = MusicHud.getLogger(LyricLineView.class);
            }
            logger.error("Disabling lyric bloom after an error", failure);
        }
    }

    /**
     * Draws only the bloom mask's source text, at the position the row's normal child draw would
     * use. The row's scale is baked into the mask, so the bloom image can be drawn unbaked at the
     * row level and still align with the sharp text.
     */
    private void drawBloomMaskAtMainText(Canvas canvas) {
        if (!(mainText instanceof LyricHighlightTextView highlight)) {
            return;
        }
        canvas.save();
        if (row != null) {
            canvas.translate(row.getLeft() + row.getTranslationX(),
                    row.getTop() + row.getTranslationY());
            canvas.scale(row.getScaleX(), row.getScaleY(), row.getPivotX(), row.getPivotY());
        }
        if (mainLine != null) {
            canvas.translate(mainLine.getLeft() + mainLine.getTranslationX(),
                    mainLine.getTop() + mainLine.getTranslationY());
        }
        canvas.translate(mainText.getLeft() + mainText.getTranslationX(),
                mainText.getTop() + mainText.getTranslationY());
        highlight.drawBloomMask(canvas);
        canvas.restore();
    }

    private long computeBloomContentStamp() {
        long stamp = 17L;
        if (mainText instanceof LyricHighlightTextView highlight) {
            stamp = stamp * 31 + highlight.getStatus().ordinal();
            stamp = stamp * 31 + nowPlayingInfo.getPlayedDuration().toMillis();
            if (highlight.isVisuallyAnimating()) {
                stamp = stamp * 31 + System.nanoTime();
            }
        }
        if (row != null) {
            stamp = stamp * 31 + Float.floatToIntBits(row.getScaleX());
            stamp = stamp * 31 + Float.floatToIntBits(row.getScaleY());
        }
        return stamp;
    }

    @Override
    protected void dispatchDraw(@NotNull Canvas canvas) {
        //noinspection UnstableApiUsage
        if (bloomFrameImage != null && canvas instanceof ArcCanvas arcCanvas) {
            float bloomAlpha = bloomAlpha();
            if (bloomAlpha > 0.001f) {
                bloomPaint.setAlphaF(bloomAlpha);
                LineBlurRenderer.drawResult(arcCanvas, bloomFrameImage, bloomSurfaceWidth,
                        bloomSurfaceHeight, bloomPadding, bloomPaint);
            }
        }
        //noinspection UnstableApiUsage
        if (frameImage != null && canvas instanceof ArcCanvas arcCanvas) {
            LineBlurRenderer.drawResult(arcCanvas, frameImage, frameSurfaceWidth, frameSurfaceHeight,
                    framePadding);
            return;
        }
        super.dispatchDraw(canvas);
    }

    /**
     * Signature of everything that affects the rendered line, so the offscreen source is re-rendered
     * only when it changes. It changes every frame while the child animates, and tracks the row scale,
     * which outlasts the colour fade.
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
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        blurSlot.register();
        bloomSlot.register();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        releaseBlurResources();
    }
}