package indi.etern.musichud.client.ui.components;

import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.core.SamplingOptions;
import icyllis.arc3d.sketch.Image;
import icyllis.modernui.core.Choreographer;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.BlendMode;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.LinearGradient;
import icyllis.modernui.graphics.Matrix;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.Shader;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.method.MovementMethod;
import icyllis.modernui.text.style.ClickableSpan;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.gpu.SceneOffscreen;
import indi.etern.musichud.client.ui.render.text.TextEdgeFade;
import indi.etern.musichud.client.utils.ui.Easing;
import indi.etern.musichud.interfaces.ClientConfig;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Single-line marquee built from two real text copies: the leading one scrolls out while the
 * trailing one follows after a gap, so the wrap is seamless and every segment keeps its own
 * click, hover and press-highlight behaviour. The text is one {@link TextView} per copy, so spans
 * such as {@code ClickableSpan} keep working.
 * <p>
 * Scrolling is an integer {@link #scrollTo(int, int)} on this container (what the framework's hit
 * testing reads) plus a fractional shift during the offscreen render, which gives sub-pixel
 * motion. The left/right fade is a {@code DST_OUT} gradient applied to that offscreen render, so
 * it never erases the parent layer. A view background would be captured there too and get eroded
 * at the edges; marquee text is expected to be background-less.
 */
@SuppressWarnings("UnstableApiUsage")
public class MarqueeTextView extends ViewGroup {
    private static final Logger LOGGER = MusicHud.getLogger(MarqueeTextView.class);
    /** Pause at the start of every loop, where the text is shown from its beginning. */
    private static final int PAUSE_MILLIS = 1500;
    private static final int MIN_SCROLL_MILLIS = 3000;
    private static final int MAX_SCROLL_MILLIS = 15000;
    /** Travel speed in pixels per second; longer text gets a longer scroll. */
    private static final float SCROLL_PIXELS_PER_SECOND = 45f;
    /** Empty gap after the text, in units of the viewport width. */
    private static final float GAP_VIEWPORT_RATIO = 0.4f;
    private static final float FADE_HEIGHT_RATIO = 0.9f;
    private static final float FADE_WIDTH_RATIO = 0.4f;
    // Near-identity projective matrix (persp0 = 1e-8) so arc3d takes the transformed-mask glyph
    // path: it keeps fractional glyph positions and bilinear atlas filtering, unlike the
    // direct-mask path, which floors them to device pixels. The epsilon must be in m14, not m44:
    // normalizePerspective() divides m44 back to 1 whenever m14/m24 are zero, and with m14 != 0 it
    // is skipped entirely.
    private static final Matrix SUBPIXEL_MATRIX = new Matrix(
            1f, 0f, 1e-8f,
            0f, 1f, 0f,
            0f, 0f, 1f);

    private final TextView leading;
    private final TextView trailing;
    private final SceneOffscreen fadeTarget = new SceneOffscreen("musichud-marquee-fade");
    private final Paint fadePaint = new Paint();
    private final Choreographer.FrameCallback frameCallback = this::onFrame;

    private Image fadeSnapshot;
    private LinearGradient leftGradient;
    private LinearGradient rightGradient;
    private int gradientBand = -1;
    private int gradientWidth = -1;
    private boolean isolatedPass;
    private boolean isolationFailed;

    /** Natural text width, measured in {@link #onMeasure}. */
    private int contentWidth;
    /** Distance from one copy's origin to the next, set in {@link #onLayout}. */
    private int cycleLength = 1;
    private int lastCycleLength = -1;
    private float offset;
    private boolean marqueeActive;
    private boolean running;
    private long phaseStart;
    private long pausedAt = -1;

    public MarqueeTextView(Context context) {
        super(context);
        // ViewGroup skips draw()/onDrawForeground by default; this control draws its own fade there.
        setWillNotDraw(false);
        setHorizontalScrollBarEnabled(false);
        setVerticalScrollBarEnabled(false);
        leading = new CopyView(context);
        trailing = new CopyView(context);
        for (TextView copy : new TextView[]{leading, trailing}) {
            copy.setSingleLine();
            copy.setHorizontalScrollBarEnabled(false);
            copy.setVerticalScrollBarEnabled(false);
            addView(copy, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
        phaseStart = MuiModApi.getElapsedTime();
    }

    public void setText(CharSequence text) {
        leading.setText(text);
        trailing.setText(text);
        resetPhase();
    }

    public void setTextColor(int color) {
        leading.setTextColor(color);
        trailing.setTextColor(color);
    }

    public void setTextSize(float size) {
        leading.setTextSize(size);
        trailing.setTextSize(size);
    }

    public void setGravity(int gravity) {
        leading.setGravity(gravity);
        trailing.setGravity(gravity);
    }

    public void setMovementMethod(MovementMethod movement) {
        leading.setMovementMethod(movement);
        trailing.setMovementMethod(movement);
    }

    public void setHighlightColor(int color) {
        leading.setHighlightColor(color);
        trailing.setHighlightColor(color);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int width = 0;
        int height = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.measure(unspecified, unspecified);
            width = Math.max(width, child.getMeasuredWidth());
            height = Math.max(height, child.getMeasuredHeight());
        }
        contentWidth = width;
        setMeasuredDimension(
                resolveSize(width, widthMeasureSpec),
                resolveSize(Math.max(height, getSuggestedMinimumHeight()), heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int height = bottom - top;
        int viewport = right - left;
        cycleLength = contentWidth > viewport
                ? contentWidth + Math.max(1, Math.round(viewport * GAP_VIEWPORT_RATIO))
                // Park the second copy well outside the viewport when nothing scrolls.
                : contentWidth + viewport + 1;
        leading.layout(0, 0, contentWidth, height);
        trailing.layout(cycleLength, 0, cycleLength + contentWidth, height);
    }

    @Override
    protected void dispatchDraw(@NotNull Canvas canvas) {
        canvas.save();
        canvas.concat(SUBPIXEL_MATRIX);
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    @Override
    public void draw(@NotNull Canvas canvas) {
        if (!marqueeActive || isolationFailed || !(canvas instanceof ArcCanvas arc)) {
            super.draw(canvas);
            return;
        }
        // The parent already translated by -scrollX; undo it so this canvas is content space.
        canvas.save();
        canvas.translate(getScrollX(), 0);
        boolean drawn = drawIsolated(arc);
        canvas.restore();
        if (!drawn) {
            super.draw(canvas);
        }
    }

    private boolean drawIsolated(@NotNull ArcCanvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (!fadeTarget.ensure(width, height) && !fadeTarget.isReady()) {
            return false;
        }
        try {
            Image snapshot;
            isolatedPass = true;
            try {
                snapshot = fadeTarget.renderSnapshot(-offset, 0f, super::draw);
            } finally {
                isolatedPass = false;
            }
            replaceSnapshot(snapshot);
            if (fadeSnapshot == null) {
                return false;
            }
            var nativeCanvas = canvas.getCanvas();
            nativeCanvas.save();
            nativeCanvas.drawImageRect(fadeSnapshot,
                    new Rect2f(0, 0, width, height),
                    new Rect2f(0, 0, width, height),
                    SamplingOptions.LINEAR, null,
                    icyllis.arc3d.sketch.Canvas.SRC_RECT_CONSTRAINT_FAST);
            nativeCanvas.restore();
            return true;
        } catch (Throwable failure) {
            isolationFailed = true;
            releaseIsolation();
            LOGGER.error("Disabling marquee edge fade after an error", failure);
            return false;
        }
    }

    /**
     * Runs inside the offscreen copy only: the erase would otherwise eat the parent's pixels.
     * The copy is shifted by {@code -offset}, so the viewport starts at content x = offset.
     */
    @Override
    public void onDrawForeground(@NotNull Canvas canvas) {
        super.onDrawForeground(canvas);
        if (!isolatedPass || !marqueeActive) {
            return;
        }
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        int band = Math.min(Math.round(height * FADE_HEIGHT_RATIO), Math.round(width * FADE_WIDTH_RATIO));
        if (band <= 0) {
            return;
        }
        float leftStrength = leftFadeStrength(band);
        float rightStrength = rightFadeStrength(band, width);
        if (leftStrength <= 0f && rightStrength <= 0f) {
            return;
        }
        ensureGradients(band, width);

        canvas.save();
        canvas.translate(offset, 0f);
        fadePaint.setBlendMode(BlendMode.DST_OUT);
        if (leftStrength > 0f) {
            fadePaint.setShader(leftGradient);
            fadePaint.setAlpha(Math.round(255f * Math.clamp(leftStrength, 0f, 1f)));
            canvas.drawRect(0, 0, band, height, fadePaint);
        }
        if (rightStrength > 0f) {
            fadePaint.setShader(rightGradient);
            fadePaint.setAlpha(Math.round(255f * Math.clamp(rightStrength, 0f, 1f)));
            canvas.drawRect(width - band, 0, width, height, fadePaint);
        }
        fadePaint.setShader(null);
        fadePaint.setBlendMode(null);
        fadePaint.setAlpha(255);
        canvas.restore();
    }

    /** Non-zero while text from one of the copies crosses the left edge. */
    private float leftFadeStrength(int band) {
        if (offset <= 0f || offset >= contentWidth) {
            return 0f;
        }
        return TextEdgeFade.smoothstep(offset / band);
    }

    /** Non-zero while a copy is cut by the right edge; zero while the gap crosses it. */
    private float rightFadeStrength(int band, int width) {
        float rightEdge = offset + width;
        if (rightEdge < contentWidth) {
            return TextEdgeFade.smoothstep((contentWidth - rightEdge) / band);
        }
        if (rightEdge >= cycleLength) {
            return TextEdgeFade.smoothstep((cycleLength + contentWidth - rightEdge) / band);
        }
        return 0f;
    }

    private void ensureGradients(int band, int width) {
        if (band == gradientBand && width == gradientWidth
                && leftGradient != null && rightGradient != null) {
            return;
        }
        gradientBand = band;
        gradientWidth = width;
        leftGradient = new LinearGradient(0, 0, band, 0,
                0xFF000000, 0x00000000, Shader.TileMode.CLAMP, null);
        rightGradient = new LinearGradient(width - band, 0, width, 0,
                0x00000000, 0xFF000000, Shader.TileMode.CLAMP, null);
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE ->
                    setHoverPause(isOverClickableSpan(event));
            case MotionEvent.ACTION_HOVER_EXIT -> setHoverPause(false);
            default -> {
            }
        }
        return super.dispatchHoverEvent(event);
    }

    /** Text that can be clicked while it moves is hard to hit; stop the loop while pointing at it. */
    private boolean isOverClickableSpan(MotionEvent event) {
        float x = event.getX() + getScrollX();
        float y = event.getY();
        for (TextView copy : new TextView[]{leading, trailing}) {
            float localX = x - copy.getLeft();
            float localY = y - copy.getTop();
            if (localX < 0 || localX >= copy.getWidth() || localY < 0 || localY >= copy.getHeight()) {
                continue;
            }
            CharSequence text = copy.getText();
            if (!(text instanceof Spanned spanned)) {
                continue;
            }
            int offset = copy.getOffsetForPosition(localX, localY);
            if (offset >= 0 && !spanned.getSpans(offset, offset, ClickableSpan.class).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (isVisible) {
            resetPhase();
            updateLoopState();
        } else {
            stopLoop();
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            updateLoopState();
        } else {
            stopLoop();
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int prevWidth, int prevHeight) {
        super.onSizeChanged(width, height, prevWidth, prevHeight);
        resetPhase();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateLoopState();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopLoop();
        releaseIsolation();
    }

    private void onFrame(Choreographer choreographer, long frameTimeNanos) {
        if (!running) {
            return;
        }
        if (!isAttachedToWindow() || !isShown()) {
            running = false;
            resetMarquee();
            return;
        }
        updateOffset();
        choreographer.postFrameCallback(frameCallback);
    }

    private void updateOffset() {
        int viewport = getWidth();
        if (viewport <= 0 || getHeight() <= 0 || contentWidth <= viewport) {
            marqueeActive = false;
            applyOffset(0f);
            return;
        }
        marqueeActive = true;
        if (cycleLength != lastCycleLength) {
            lastCycleLength = cycleLength;
            phaseStart = clock();
        }
        int duration = Math.clamp(Math.round(cycleLength * 1000f / SCROLL_PIXELS_PER_SECOND),
                MIN_SCROLL_MILLIS, MAX_SCROLL_MILLIS);
        long phase = Math.floorMod(clock() - phaseStart, (long) PAUSE_MILLIS + duration);
        float progress = phase < PAUSE_MILLIS
                ? 0f
                : Easing.EASE_IN_OUT_SINE.getInterpolation((phase - PAUSE_MILLIS) / (float) duration);
        applyOffset(progress * cycleLength);
    }

    private void applyOffset(float value) {
        if (value != offset) {
            offset = value;
            invalidate();
        }
        int scroll = Math.round(value);
        if (getScrollX() != scroll) {
            scrollTo(scroll, 0);
        }
    }

    private void resetPhase() {
        phaseStart = MuiModApi.getElapsedTime();
        lastCycleLength = -1;
        resetMarquee();
    }

    private void resetMarquee() {
        pausedAt = -1;
        marqueeActive = false;
        applyOffset(0f);
    }

    private void updateLoopState() {
        if (!isAttachedToWindow() || !isShown() || !ClientConfig.getInstance().getEnableMarqueeText()) {
            stopLoop();
            return;
        }
        startLoop();
    }

    private void startLoop() {
        if (running) {
            return;
        }
        running = true;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    private void stopLoop() {
        if (!running) {
            return;
        }
        running = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        resetMarquee();
    }

    private void setHoverPause(boolean pause) {
        long now = MuiModApi.getElapsedTime();
        if (pause) {
            if (pausedAt < 0) {
                pausedAt = now;
            }
        } else if (pausedAt >= 0) {
            phaseStart += now - pausedAt;
            pausedAt = -1;
        }
    }

    private long clock() {
        return pausedAt >= 0 ? pausedAt : MuiModApi.getElapsedTime();
    }

    private void replaceSnapshot(Image image) {
        if (fadeSnapshot == image) {
            return;
        }
        if (fadeSnapshot != null) {
            fadeSnapshot.unref();
        }
        fadeSnapshot = image;
    }

    private void releaseIsolation() {
        replaceSnapshot(null);
        fadeTarget.close();
    }

    /** A copy whose links keep the default arrow cursor, like every other clickable in this UI. */
    private static final class CopyView extends TextView {
        CopyView(Context context) {
            super(context);
        }

        @Override
        public PointerIcon onResolvePointerIcon(@NotNull MotionEvent event) {
            return null;
        }
    }
}
