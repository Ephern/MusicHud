package indi.etern.musichud.client.ui.components.volume;

import icyllis.modernui.animation.Animator;
import icyllis.modernui.animation.AnimatorListener;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import indi.etern.musichud.client.utils.ui.Easing;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.client.utils.ui.PanelStyle;
import lombok.Getter;
import lombok.Setter;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;

/**
 * Volume flyout anchored above its icon. The outer frame is fixed at full size and only the
 * reveal fraction changes, so opening costs no page relayout; the frame follows the anchor with a
 * translation whenever the anchor moves, which keeps the flyout from ever covering its icon.
 */
public final class VolumePanel extends FrameLayout {
    private static final int WIDTH_DP = 20;
    private static final int HEIGHT_DP = 96;
    private static final int CONTENT_PADDING_DP = 6;
    private static final int ANCHOR_GAP_DP = 4;
    private static final int EDGE_MARGIN_DP = 8;
    private static final int OPEN_DURATION_MS = 170;
    private static final int CLOSE_DURATION_MS = 130;

    private final VerticalVolumeSlider slider;
    private final int fullHeight;
    private final int panelWidth;

    private ValueAnimator animator;
    private final Rect clipBounds = new Rect();
    private View anchor;
    private float reveal;
    private boolean openDownward;
    private int anchorLeft;
    private int anchorTop;
    private int anchorRight;
    private int anchorBottom;
    private int panelLeft;
    @Getter
    private boolean dragging;
    @Setter
    private Listener listener;

    public interface Listener {
        void onValueChanged(int value);

        void onTrackingStopped();

        void onScroll(float notches);

        void onHoverChanged(boolean hovered);
    }

    public VolumePanel(Context context) {
        super(context);
        // A click on the panel background must not fall through to the page underneath.
        setClickable(true);
        setClipChildren(true);
        setVisibility(GONE);

        panelWidth = dp(WIDTH_DP);
        fullHeight = dp(HEIGHT_DP);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(CONTENT_PADDING_DP);
        content.setPadding(padding, padding, padding, padding);

        slider = new VerticalVolumeSlider(context);
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1);
        sliderParams.setMargins(0, 0, 0, 0);
        content.addView(slider, sliderParams);
        slider.setOnValueChangeListener(new VerticalVolumeSlider.OnValueChangeListener() {
            @Override
            public void onValueChanged(int value) {
                dragging = true;
                if (listener != null) {
                    listener.onValueChanged(value);
                }
            }

            @Override
            public void onTrackingStopped() {
                dragging = false;
                if (listener != null) {
                    listener.onTrackingStopped();
                }
            }
        });

        addView(content, new FrameLayout.LayoutParams(MATCH_PARENT, fullHeight));

        PanelStyle.applyDynamically(this, dp(16),
                new InsetBackgroundFactory.Padding(0, 0, 0, 0),
                InsetBackgroundFactory.BackdropBlur.UI_LAYER);
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    public void setLevel(int level, boolean muted) {
        slider.setValue(level);
        slider.setMuted(muted);
    }

    /**
     * Positions the panel against {@code anchor}, both in {@code layer} coordinates. Returns false
     * when the anchor is not part of the layer's hierarchy yet.
     */
    public boolean showFor(View anchor, ViewGroup layer) {
        int layerWidth = layer.getWidth();
        int layerHeight = layer.getHeight();
        if (layerWidth <= 0 || layerHeight <= 0 || !locate(anchor, layer)) {
            return false;
        }
        this.anchor = anchor;
        openDownward = anchorTop - dp(ANCHOR_GAP_DP) - fullHeight < dp(EDGE_MARGIN_DP);
        panelLeft = desiredLeft(layerWidth);
        if (!isShowing()) {
            reveal = 0f;
        }
        applyFrameLayout();
        setVisibility(VISIBLE);
        applyReveal(reveal);
        syncToAnchor();
        animateTo(1f, OPEN_DURATION_MS, Easing.EASE_OUT_QUAD);
        return true;
    }

    public void hide() {
        if (!isShowing() || dragging) {
            return;
        }
        animateTo(0f, CLOSE_DURATION_MS, Easing.EASE_IN_QUAD);
    }

    /** Re-glues the flyout to its icon after pointer movement without a layout pass. */
    public void refreshAnchor() {
        syncToAnchor();
    }

    private void animateTo(float target, int duration, Easing easing) {
        cancelAnimator();
        if (reveal == target) {
            applyReveal(target);
            syncVisibility(target);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(reveal, target);
        animator.setDuration(duration);
        animator.setInterpolator(easing);
        animator.addUpdateListener(a -> applyReveal((Float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                if (VolumePanel.this.animator != animator) {
                    return;
                }
                VolumePanel.this.animator = null;
                applyReveal(target);
                syncVisibility(target);
            }
        });
        this.animator = animator;
        animator.start();
    }

    private void syncVisibility(float target) {
        if (target <= 0f) {
            setVisibility(GONE);
        }
    }

    /** Anchors the full-size frame once; the reveal is then purely a render-level clip. */
    private void applyFrameLayout() {
        ViewGroup.LayoutParams params = getLayoutParams();
        if (params instanceof FrameLayout.LayoutParams lp) {
            lp.width = panelWidth;
            lp.height = fullHeight;
            lp.leftMargin = panelLeft;
            lp.topMargin = desiredTop();
            setLayoutParams(lp);
        }
        // The frame is authoritative; any offset from a previous anchor is re-derived below.
        setTranslationX(0f);
        setTranslationY(0f);
    }

    private int desiredLeft(int layerWidth) {
        int maxLeft = Math.max(dp(EDGE_MARGIN_DP), layerWidth - panelWidth - dp(EDGE_MARGIN_DP));
        return Math.clamp(anchorLeft + (anchorRight - anchorLeft) / 2 - panelWidth / 2,
                dp(EDGE_MARGIN_DP), maxLeft);
    }

    private int desiredTop() {
        return openDownward
                ? anchorBottom + dp(ANCHOR_GAP_DP)
                : anchorTop - dp(ANCHOR_GAP_DP) - fullHeight;
    }

    /**
     * Keeps the frame glued to the anchor without touching layout: a page relayout can move the
     * icon while the flyout is open, and a stale frame would swallow clicks meant for the icon.
     */
    private void syncToAnchor() {
        View anchor = this.anchor;
        if (anchor == null || !isShowing() || !(getParent() instanceof ViewGroup layer)) {
            return;
        }
        int layerWidth = layer.getWidth();
        if (layerWidth <= 0 || !locate(anchor, layer)) {
            return;
        }
        int dx = desiredLeft(layerWidth) - getLeft();
        int dy = desiredTop() - getTop();
        if (getTranslationX() != dx) {
            setTranslationX(dx);
        }
        if (getTranslationY() != dy) {
            setTranslationY(dy);
        }
    }

    private void applyReveal(float fraction) {
        reveal = Math.clamp(fraction, 0f, 1f);
        int height = Math.round(fullHeight * reveal);
        if (openDownward) {
            clipBounds.set(0, 0, panelWidth, height);
        } else {
            clipBounds.set(0, fullHeight - height, panelWidth, fullHeight);
        }
        setClipBounds(clipBounds);
        setAlpha(reveal);
    }

    private void cancelAnimator() {
        if (animator != null) {
            ValueAnimator running = animator;
            animator = null;
            running.cancel();
        }
    }

    /** Fills the anchor frame relative to {@code ancestor}; false when it is not a descendant. */
    private boolean locate(View anchor, View ancestor) {
        if (anchor.getWidth() <= 0 || anchor.getHeight() <= 0) {
            return false;
        }
        int left = 0;
        int top = 0;
        View view = anchor;
        while (view != ancestor) {
            left += view.getLeft() + Math.round(view.getTranslationX());
            top += view.getTop() + Math.round(view.getTranslationY());
            if (!(view.getParent() instanceof View parent)) {
                return false;
            }
            view = parent;
        }
        anchorLeft = left;
        anchorTop = top;
        anchorRight = left + anchor.getWidth();
        anchorBottom = top + anchor.getHeight();
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAnimator();
        anchor = null;
        super.onDetachedFromWindow();
    }

    @Override
    protected boolean dispatchHoverEvent(@NonNull MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_HOVER_ENTER) {
            notifyHover(true);
        } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
            notifyHover(false);
        }
        return super.dispatchHoverEvent(event);
    }

    private void notifyHover(boolean hovered) {
        if (listener != null) {
            listener.onHoverChanged(hovered);
        }
    }

    @Override
    public boolean onGenericMotionEvent(@NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL) {
            float notches = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (notches != 0f) {
                if (listener != null) {
                    listener.onScroll(notches);
                }
                return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // The animated frame is authoritative; children keep their fixed sizes.
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        int childWidthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        int childHeightSpec = MeasureSpec.makeMeasureSpec(fullHeight, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            child.measure(childWidthSpec, childHeightSpec);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            // Bottom-aligned content; the part above the frame is clipped away while opening.
            int childTop = height - child.getMeasuredHeight();
            child.layout(0, childTop, width, childTop + child.getMeasuredHeight());
        }
        syncToAnchor();
    }
}
