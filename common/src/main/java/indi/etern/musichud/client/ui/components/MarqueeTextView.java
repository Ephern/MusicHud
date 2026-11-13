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
import icyllis.modernui.text.SpannableStringBuilder;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.TextPaint;
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
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Single-line marquee built from two real text copies: the leading one scrolls out while the
 * trailing one follows after a gap, so the wrap is seamless and every segment keeps its own
 * click and hover behaviour. The text is one {@link TextView} per copy, so spans such as
 * {@code ClickableSpan} keep working; they dim while hovered or pressed.
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
    /** Opacity of a clickable span under the pointer, and while the button is held down. */
    private static final float HOVER_ALPHA = 0.5f;
    private static final float PRESSED_ALPHA = 0.3f;
    /** Dim transition duration; a short cross-fade keeps hover feedback calm. */
    private static final long DIM_MILLIS = 100;
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
    private final Choreographer.FrameCallback dimFrameCallback = this::onDimFrame;

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
    /** The wrapper currently under the pointer, dimmed until the pointer leaves it. */
    private InteractiveClickableSpan hoveredSpan;
    private InteractiveClickableSpan pressedSpan;
    /** Spans whose dim transition is still running. */
    private final Set<InteractiveClickableSpan> dimmingSpans =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean dimFramePosted;

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
            // Pressed links dim instead of drawing the selection highlight behind the text.
            copy.setHighlightColor(0);
            addView(copy, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
        phaseStart = MuiModApi.getElapsedTime();
    }

    public void setText(CharSequence text) {
        clearInteraction();
        CharSequence prepared = wrapClickableSpans(text);
        leading.setText(prepared);
        trailing.setText(prepared);
        resetPhase();
    }

    /**
     * Replaces clickable spans with wrappers that carry the hover and pressed state, since the
     * caller's spans report their draw state but cannot be told to dim.
     */
    private static CharSequence wrapClickableSpans(CharSequence text) {
        if (!(text instanceof Spanned spanned)) {
            return text;
        }
        List<ClickableSpan> spans = spanned.getSpans(0, spanned.length(), ClickableSpan.class);
        if (spans.isEmpty()) {
            return text;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        Set<ClickableSpan> wrapped = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ClickableSpan span : spans) {
            // Ranges are looked up by instance, so a span attached twice can only be wrapped once.
            if (!wrapped.add(span)) {
                continue;
            }
            builder.removeSpan(span);
            builder.setSpan(new InteractiveClickableSpan(span), spanned.getSpanStart(span),
                    spanned.getSpanEnd(span), spanned.getSpanFlags(span));
        }
        return builder;
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
            case MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                ClickableSpan span = findClickableSpan(event);
                setHoverPause(span != null);
                setHoveredSpan(span instanceof InteractiveClickableSpan hover ? hover : null);
            }
            case MotionEvent.ACTION_HOVER_EXIT -> {
                setHoverPause(false);
                setHoveredSpan(null);
            }
            default -> {
            }
        }
        return super.dispatchHoverEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                ClickableSpan span = findClickableSpan(event);
                setPressedSpan(span instanceof InteractiveClickableSpan pressed ? pressed : null);
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setPressedSpan(null);
            default -> {
            }
        }
        return super.dispatchTouchEvent(event);
    }

    /** Text that can be clicked while it moves is hard to hit; stop the loop while pointing at it. */
    @Nullable
    private ClickableSpan findClickableSpan(MotionEvent event) {
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
            if (offset >= 0) {
                List<ClickableSpan> spans = spanned.getSpans(offset, offset, ClickableSpan.class);
                if (!spans.isEmpty()) {
                    return spans.getFirst();
                }
            }
        }
        return null;
    }

    private void setHoveredSpan(@Nullable InteractiveClickableSpan span) {
        if (hoveredSpan == span) {
            return;
        }
        InteractiveClickableSpan previous = hoveredSpan;
        hoveredSpan = span;
        if (previous != null) {
            previous.hovered = false;
            animateDim(previous);
        }
        if (span != null) {
            span.hovered = true;
            animateDim(span);
        }
    }

    private void setPressedSpan(@Nullable InteractiveClickableSpan span) {
        if (pressedSpan == span) {
            return;
        }
        InteractiveClickableSpan previous = pressedSpan;
        pressedSpan = span;
        if (previous != null) {
            previous.pressed = false;
            animateDim(previous);
        }
        if (span != null) {
            span.pressed = true;
            animateDim(span);
        }
    }

    private void animateDim(InteractiveClickableSpan span) {
        if (span.startTransition(MuiModApi.getElapsedTime())) {
            dimmingSpans.add(span);
            if (!dimFramePosted) {
                dimFramePosted = true;
                Choreographer.getInstance().postFrameCallback(dimFrameCallback);
            }
        }
        // Span draw states are re-applied on every draw, so only a repaint is needed.
        invalidate();
    }

    private void onDimFrame(Choreographer choreographer, long frameTimeNanos) {
        dimFramePosted = false;
        if (dimmingSpans.isEmpty()) {
            return;
        }
        long now = MuiModApi.getElapsedTime();
        dimmingSpans.removeIf(span -> !span.tick(now));
        invalidate();
        if (!dimmingSpans.isEmpty()) {
            dimFramePosted = true;
            choreographer.postFrameCallback(dimFrameCallback);
        }
    }

    /** Snaps every dimmed span back to opaque, e.g. when the text or visibility is gone. */
    private void clearInteraction() {
        if (hoveredSpan != null) {
            hoveredSpan.reset();
            hoveredSpan = null;
        }
        if (pressedSpan != null) {
            pressedSpan.reset();
            pressedSpan = null;
        }
        for (InteractiveClickableSpan span : dimmingSpans) {
            span.reset();
        }
        dimmingSpans.clear();
        if (dimFramePosted) {
            dimFramePosted = false;
            Choreographer.getInstance().removeFrameCallback(dimFrameCallback);
        }
        invalidate();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (isVisible) {
            resetPhase();
            updateLoopState();
        } else {
            clearInteraction();
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
        clearInteraction();
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

    /** Delegates to the caller's span and dims it while hovered, or further while pressed. */
    private static final class InteractiveClickableSpan extends ClickableSpan {
        private final ClickableSpan delegate;
        private boolean hovered;
        private boolean pressed;
        /** Current opacity multiplier, eased towards the state target. */
        private float alpha = 1f;
        private float alphaFrom = 1f;
        private float alphaTo = 1f;
        private long alphaStart;
        private boolean animating;

        InteractiveClickableSpan(ClickableSpan delegate) {
            this.delegate = delegate;
        }

        private float targetAlpha() {
            return pressed ? PRESSED_ALPHA : hovered ? HOVER_ALPHA : 1f;
        }

        /** Restarts the eased transition from the current value; returns whether one is running. */
        private boolean startTransition(long now) {
            float target = targetAlpha();
            if (target == alphaTo) {
                return animating;
            }
            alphaFrom = alpha;
            alphaTo = target;
            alphaStart = now;
            animating = alphaFrom != alphaTo;
            if (!animating) {
                alpha = alphaTo;
            }
            return animating;
        }

        /** Advances one frame; returns false once the transition has settled. */
        private boolean tick(long now) {
            if (!animating) {
                return false;
            }
            float progress = (now - alphaStart) / (float) DIM_MILLIS;
            if (progress >= 1f) {
                alpha = alphaTo;
                animating = false;
                return false;
            }
            alpha = alphaFrom
                    + (alphaTo - alphaFrom) * Easing.EASE_IN_OUT_SINE.getInterpolation(progress);
            return true;
        }

        private void reset() {
            hovered = false;
            pressed = false;
            alpha = 1f;
            alphaFrom = 1f;
            alphaTo = 1f;
            animating = false;
        }

        @Override
        public void onClick(@NotNull View widget) {
            delegate.onClick(widget);
        }

        @Override
        public void updateDrawState(@NotNull TextPaint ds) {
            delegate.updateDrawState(ds);
            if (alpha < 1f) {
                ds.setAlphaF(ds.getAlphaF() * alpha);
            }
        }
    }
}
