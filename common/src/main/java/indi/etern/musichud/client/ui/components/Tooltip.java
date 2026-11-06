package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.Animator;
import icyllis.modernui.animation.AnimatorListener;
import icyllis.modernui.animation.AnimatorSet;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewConfiguration;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.view.WindowManager;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.utils.ui.Easing;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.client.utils.ui.PanelStyle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * Panel-styled replacement for {@code View.setTooltipText}, shown from the hover machinery we
 * drive ourselves. ModernUI's built-in tooltip takes its colours from the app theme and offers no
 * per-view hook, so this class owns the whole popup instead: the card style matches {@code Modal}
 * and the background is blurred through the current UI layer.
 * <p>
 * The tooltip is a {@link WindowManager} window, which ModernUI draws inside the main
 * {@code WindowGroup} layer, so the blur drawable samples the UI underneath it.
 * <p>
 * Instances are cached per anchor, so repeated {@link #attach} calls only update the text.
 */
@SuppressWarnings("UnstableApiUsage")
public final class Tooltip {
    private static final int SHOW_DURATION = 150;
    private static final int HIDE_DURATION = 100;
    private static final float SHOW_SCALE_FROM = 0.95f;
    private static final int GAP_DP = 4;
    private static final int CORNER_RADIUS_DP = 6;
    private static final int PADDING_HORIZONTAL_DP = 8;
    private static final int PADDING_VERTICAL_DP = 4;
    private static final int MAX_WIDTH_DP = 512;
    /** Hover is precise only for views at least this large, in dp. */
    private static final int PRECISE_ANCHOR_DP = 96;

    private static final WeakHashMap<View, WeakReference<Tooltip>> ATTACHED = new WeakHashMap<>();

    private final View anchor;
    private final View.OnAttachStateChangeListener attachListener;
    private final Runnable showRunnable = this::show;
    private final Runnable hideRunnable = () -> hide(true);

    private CharSequence text;
    private TextView content;
    private AnimatorSet animation;
    private boolean pendingShow;
    private boolean detached;
    private int hoverX;
    private int hoverY;

    private Tooltip(@NotNull View anchor) {
        this.anchor = anchor;
        anchor.setOnHoverListener((view, event) -> {
            handleHover(event);
            return false;
        });
        attachListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                cancelShow();
                anchor.removeCallbacks(hideRunnable);
                hide(false);
            }
        };
        anchor.addOnAttachStateChangeListener(attachListener);
    }

    /** Attaches to (or updates) the tooltip of {@code anchor}; a null text hides and disables it. */
    public static Tooltip attach(View anchor, @Nullable CharSequence text) {
        WeakReference<Tooltip> reference = ATTACHED.get(anchor);
        Tooltip tooltip = reference == null ? null : reference.get();
        if (tooltip == null) {
            tooltip = new Tooltip(anchor);
            ATTACHED.put(anchor, new WeakReference<>(tooltip));
        }
        tooltip.setText(text);
        return tooltip;
    }

    public @Nullable CharSequence getText() {
        return text;
    }

    /** Forwards a container's hover event to the tooltip previously attached to {@code anchor}. */
    public static boolean handleHover(View anchor, @NotNull MotionEvent event) {
        WeakReference<Tooltip> reference = ATTACHED.get(anchor);
        Tooltip tooltip = reference == null ? null : reference.get();
        return tooltip != null && tooltip.handleHover(event);
    }

    public void setText(@Nullable CharSequence text) {
        this.text = text;
        if (text == null || text.length() == 0) {
            anchor.removeCallbacks(hideRunnable);
            hide(false);
        } else if (content != null) {
            content.setText(text);
        }
    }

    /**
     * Drives the tooltip from a container's hover dispatch. {@link ViewGroup} hands hover to its
     * children, so an {@link View.OnHoverListener} on the container itself never sees it.
     *
     * @return always false, the event is not consumed
     */
    public boolean handleHover(@NotNull MotionEvent event) {
        if (detached) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                hoverX = (int) event.getX();
                hoverY = (int) event.getY();
                if (content == null && !pendingShow) {
                    pendingShow = true;
                    anchor.postDelayed(showRunnable, ViewConfiguration.getHoverTooltipShowTimeout());
                }
                anchor.removeCallbacks(hideRunnable);
                anchor.postDelayed(hideRunnable, ViewConfiguration.getHoverTooltipHideTimeout());
            }
            case MotionEvent.ACTION_HOVER_EXIT -> {
                cancelShow();
                anchor.removeCallbacks(hideRunnable);
                hide(true);
            }
            default -> {
            }
        }
        return false;
    }

    public void hide() {
        cancelShow();
        anchor.removeCallbacks(hideRunnable);
        hide(true);
    }

    public void detach() {
        detached = true;
        cancelShow();
        anchor.removeCallbacks(hideRunnable);
        hide(false);
        anchor.setOnHoverListener(null);
        anchor.removeOnAttachStateChangeListener(attachListener);
        ATTACHED.remove(anchor);
    }

    private void show() {
        pendingShow = false;
        CharSequence value = text;
        if (detached || content != null || value == null || value.length() == 0) {
            return;
        }
        if (!anchor.isAttachedToWindow() || !anchor.isShown()) {
            return;
        }
        View root = anchor.getRootView();
        WindowManager windowManager = windowManager();
        if (root == null || root.getMeasuredWidth() <= 0 || windowManager == null) {
            return;
        }

        TextView tooltip = new TextView(anchor.getContext());
        tooltip.setTextSize(Theme.TEXT_SIZE_NORMAL);
        tooltip.setTextColor(Theme.NORMAL_TEXT_COLOR);
        tooltip.setGravity(Gravity.CENTER_VERTICAL);
        tooltip.setText(value);
        tooltip.setMaxWidth(Math.min(root.getMeasuredWidth() / 2, tooltip.dp(MAX_WIDTH_DP)));
        PanelStyle.applyDynamically(tooltip, tooltip.dp(CORNER_RADIUS_DP),
                new InsetBackgroundFactory.Padding(tooltip.dp(PADDING_HORIZONTAL_DP),
                        tooltip.dp(PADDING_VERTICAL_DP),
                        tooltip.dp(PADDING_HORIZONTAL_DP),
                        tooltip.dp(PADDING_VERTICAL_DP)),
                InsetBackgroundFactory.BackdropBlur.UI_LAYER);

        int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        tooltip.measure(spec, spec);
        tooltip.setPivotX(tooltip.getMeasuredWidth() / 2f);
        tooltip.setPivotY(tooltip.getMeasuredHeight() / 2f);
        tooltip.setAlpha(0f);
        tooltip.setScaleX(SHOW_SCALE_FROM);
        tooltip.setScaleY(SHOW_SCALE_FROM);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_ABOVE_SUB_PANEL;
        position(params, tooltip, root);
        windowManager.addView(tooltip, params);
        content = tooltip;
        animateIn(tooltip);
    }

    /** Mirrors {@code TooltipPopup}: precise anchoring above the threshold, centred below it. */
    private void position(WindowManager.LayoutParams params, TextView tooltip, View root) {
        int gap = tooltip.dp(GAP_DP);
        int precise = tooltip.dp(PRECISE_ANCHOR_DP);
        int offsetX = anchor.getWidth() >= precise ? hoverX : anchor.getWidth() / 2;
        int offsetBelow;
        int offsetAbove;
        if (anchor.getHeight() >= precise) {
            int extra = tooltip.dp(8);
            offsetBelow = hoverY + extra;
            offsetAbove = hoverY - extra;
        } else {
            offsetBelow = anchor.getHeight();
            offsetAbove = 0;
        }
        params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        params.x = location[0] + offsetX - root.getMeasuredWidth() / 2;
        int height = tooltip.getMeasuredHeight();
        int yBelow = location[1] + offsetBelow + gap;
        int yAbove = location[1] + offsetAbove - gap - height;
        params.y = yBelow + height <= root.getMeasuredHeight() ? yBelow : yAbove;
    }

    private void animateIn(TextView tooltip) {
        cancelAnimation();
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(tooltip, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(tooltip, View.SCALE_X, SHOW_SCALE_FROM, 1f),
                ObjectAnimator.ofFloat(tooltip, View.SCALE_Y, SHOW_SCALE_FROM, 1f));
        set.setDuration(SHOW_DURATION);
        set.setInterpolator(Easing.EASE_OUT_QUAD);
        animation = set;
        set.start();
    }

    private void hide(boolean animate) {
        cancelShow();
        TextView tooltip = content;
        if (tooltip == null) {
            return;
        }
        content = null;
        cancelAnimation();
        if (!animate) {
            remove(tooltip);
            return;
        }
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(tooltip, View.ALPHA, tooltip.getAlpha(), 0f),
                ObjectAnimator.ofFloat(tooltip, View.SCALE_X, tooltip.getScaleX(), SHOW_SCALE_FROM),
                ObjectAnimator.ofFloat(tooltip, View.SCALE_Y, tooltip.getScaleY(), SHOW_SCALE_FROM));
        set.setDuration(HIDE_DURATION);
        set.setInterpolator(Easing.EASE_IN_QUAD);
        set.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NotNull Animator animator) {
                remove(tooltip);
            }
        });
        animation = set;
        set.start();
    }

    private void remove(TextView tooltip) {
        WindowManager windowManager = windowManager();
        if (windowManager != null && tooltip.getParent() != null) {
            try {
                windowManager.removeView(tooltip);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void cancelShow() {
        if (pendingShow) {
            pendingShow = false;
            anchor.removeCallbacks(showRunnable);
        }
    }

    private void cancelAnimation() {
        if (animation != null) {
            AnimatorSet set = animation;
            animation = null;
            set.cancel();
        }
    }

    private @Nullable WindowManager windowManager() {
        return (WindowManager) anchor.getContext().getSystemService(Context.WINDOW_SERVICE);
    }
}
