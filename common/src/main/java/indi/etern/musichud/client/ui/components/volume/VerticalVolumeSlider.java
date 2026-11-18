package indi.etern.musichud.client.ui.components.volume;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Choreographer;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.LinearGradient;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.Shader;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.drawable.LinearProgressStyle;
import indi.etern.musichud.client.utils.ui.SpringValue;
import lombok.Getter;

/**
 * Vertical level slider drawn with the same track/fill language as the media progress bar: a pill
 * track and a pill fill anchored at the head. There is no handle; the fill's top edge is the level.
 * The drawn level springs towards the logical value so wheel steps and jumps glide instead of
 * snapping, while drag moves keep retargeting the same spring.
 */
public class VerticalVolumeSlider extends View {
    public static final int MAX_VALUE = 100;
    private static final int TOUCH_WIDTH_DP = 24;
    private static final int DEFAULT_HEIGHT_DP = 80;
    private static final int EDGE_INSET_DP = 2;
    private static final float MUTED_ALPHA = 0.35f;
    private static final float RESPONSE_TIME = 0.1f;
    private static final float DAMPING = 1f;
    private static final float SETTLE_VALUE_EPSILON = 0.05f;
    private static final float SETTLE_VELOCITY_EPSILON = 0.5f;

    /** Slightly brighter than the media bar track: the flyout sits on a dark blurred panel. */
    private static final int TRACK_COLOR = 0x30FFFFFF;

    private static final LinearProgressStyle STYLE = LinearProgressStyle.builder()
            .trackColor(TRACK_COLOR)
            .fillColors(Theme.PROGRESS_TAIL_COLOR, Theme.PROGRESS_HEAD_COLOR)
            .trackThicknessDp(4)
            .cornerRadiusDp(-1)
            .gradientLengthDp(24)
            .alphaFadeIn(0.01f)
            .build();

    private final Paint trackPaint = new Paint();
    private final Paint fillPaint = new Paint();

    private final float thicknessPx;
    private final float radiusPx;
    private final float edgeInsetPx;
    private final float alphaFadeIn;

    @Getter
    private int value = MAX_VALUE;
    private boolean muted;
    private boolean dragging;
    private OnValueChangeListener listener;

    private final SpringValue displaySpring = new SpringValue(RESPONSE_TIME, DAMPING);
    private boolean animating;
    private final Choreographer.FrameCallback frameCallback = this::advanceDisplay;

    public interface OnValueChangeListener {
        void onValueChanged(int value);

        default void onTrackingStopped() {
        }
    }

    public VerticalVolumeSlider(Context context) {
        super(context);
        setClickable(true);
        thicknessPx = dp(STYLE.getTrackThicknessDp());
        radiusPx = STYLE.getCornerRadiusDp() < 0
                ? thicknessPx / 2f
                : Math.min(dp(STYLE.getCornerRadiusDp()), thicknessPx / 2f);
        float gradientLengthPx = dp(STYLE.getGradientLengthDp());
        edgeInsetPx = dp(EDGE_INSET_DP);
        alphaFadeIn = STYLE.getAlphaFadeIn();

        trackPaint.setAntiAlias(true);
        trackPaint.setColor(STYLE.getTrackColor());
        fillPaint.setAntiAlias(true);
        int[] fillColors = STYLE.getFillColors();
        if (fillColors.length > 1 && gradientLengthPx > 0) {
            // Head-first order: the fill grows downwards from the level, so the bright end of the
            // band sits on the level edge and fades towards the base, like the media bar.
            int[] headFirst = new int[fillColors.length];
            for (int i = 0; i < fillColors.length; i++) {
                headFirst[i] = fillColors[fillColors.length - 1 - i];
            }
            fillPaint.setShader(new LinearGradient(0, 0, 0, gradientLengthPx,
                    headFirst, null, Shader.TileMode.CLAMP, null));
        } else {
            fillPaint.setColor(fillColors[fillColors.length - 1]);
        }
        displaySpring.jumpTo(value);
    }

    public void setOnValueChangeListener(OnValueChangeListener listener) {
        this.listener = listener;
    }

    public void setValue(int value) {
        int clamped = Math.clamp(value, 0, MAX_VALUE);
        if (this.value == clamped) {
            return;
        }
        this.value = clamped;
        animateDisplayTo(clamped);
    }

    public void setMuted(boolean muted) {
        if (this.muted == muted) {
            return;
        }
        this.muted = muted;
        invalidate();
    }

    private void animateDisplayTo(int target) {
        displaySpring.setTarget(target, MuiModApi.getFrameTimeNanos());
        if (!animating) {
            animating = true;
            Choreographer.getInstance().postFrameCallback(frameCallback);
        }
        invalidate();
    }

    private void advanceDisplay(Choreographer choreographer, long frameTimeNanos) {
        if (!animating) {
            return;
        }
        displaySpring.update(frameTimeNanos);
        invalidate();
        if (displaySpring.isSettled(SETTLE_VALUE_EPSILON, SETTLE_VELOCITY_EPSILON)) {
            // Land exactly on the target so the final frame is not a sub-pixel off.
            displaySpring.jumpTo(displaySpring.getTarget());
            animating = false;
            invalidate();
        } else {
            choreographer.postFrameCallback(frameCallback);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(dp(TOUCH_WIDTH_DP), widthMeasureSpec);
        int height = resolveSize(dp(DEFAULT_HEIGHT_DP), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float trackTop = getPaddingTop() + edgeInsetPx;
        float trackBottom = getHeight() - getPaddingBottom() - edgeInsetPx;
        if (trackBottom <= trackTop) {
            return;
        }
        float shown = Math.clamp(displaySpring.getValue(), 0f, MAX_VALUE);
        float centerX = getWidth() / 2f;
        float halfThickness = thicknessPx / 2f;
        float anchorY = yForValue(shown, trackTop, trackBottom);

        canvas.drawRoundRect(centerX - halfThickness, trackTop,
                centerX + halfThickness, trackBottom, radiusPx, trackPaint);

        float fillLength = trackBottom - anchorY;
        float fraction = shown / MAX_VALUE;
        if (fillLength > 0 && fraction > 0) {
            float fillRadius = Math.min(radiusPx, fillLength / 2f);
            float alpha = muted ? MUTED_ALPHA : 1f;
            if (alphaFadeIn > 0) {
                alpha *= Math.min(1f, fraction / alphaFadeIn);
            }
            fillPaint.setAlphaF(alpha);
            int save = canvas.save();
            // The level edge is the head: the fill extends from it downwards to the base.
            canvas.translate(0, anchorY);
            canvas.drawRoundRect(centerX - halfThickness, 0,
                    centerX + halfThickness, fillLength, fillRadius, fillPaint);
            canvas.restoreToCount(save);
        }
    }

    private static float yForValue(float value, float trackTop, float trackBottom) {
        return trackBottom - (trackBottom - trackTop) * value / MAX_VALUE;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                dragging = true;
                setPressed(true);
                updateFromY(event.getY());
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    updateFromY(event.getY());
                    return true;
                }
            }
            case MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false;
                    setPressed(false);
                    updateFromY(event.getY());
                    performClick();
                    if (listener != null) {
                        listener.onTrackingStopped();
                    }
                    return true;
                }
            }
            case MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false;
                    setPressed(false);
                    if (listener != null) {
                        listener.onTrackingStopped();
                    }
                    return true;
                }
            }
            default -> {
            }
        }
        return super.onTouchEvent(event);
    }

    private void updateFromY(float y) {
        float trackTop = getPaddingTop() + edgeInsetPx;
        float trackBottom = getHeight() - getPaddingBottom() - edgeInsetPx;
        float usable = trackBottom - trackTop;
        if (usable <= 0) {
            return;
        }
        int next = Math.round(MAX_VALUE * (trackBottom - y) / usable);
        next = Math.clamp(next, 0, MAX_VALUE);
        if (next == value) {
            return;
        }
        value = next;
        animateDisplayTo(next);
        if (listener != null) {
            listener.onValueChanged(next);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        animating = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        super.onDetachedFromWindow();
    }
}
