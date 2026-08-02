package indi.etern.musichud.client.ui.components;

import icyllis.modernui.R;
import icyllis.modernui.animation.Animator;
import icyllis.modernui.animation.AnimatorListener;
import icyllis.modernui.animation.AnimatorSet;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.StateSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.utils.ui.ButtonInsetBackgroundFactory;
import indi.etern.musichud.client.ui.utils.ui.Easing;
import lombok.Getter;

import java.util.function.BiConsumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

@SuppressWarnings("UnstableApiUsage")
public class Modal {
    private static final int MAX_WIDTH = 400;
    private static final int CORNER_RADIUS = 12;
    private static final int CONTENT_PADDING = 24;
    private static final int BUTTON_PADDING = 16;
    private static final int BUTTON_GAP = 8;
    private static final int DIVIDER_HEIGHT = 1;
    private static final int SCREEN_DIM_COLOR = 0xA0000000;
    private static final int SHOW_DURATION = 200;
    private static final int DISMISS_DURATION = 150;

    @Getter
    private final PopupWindow popupWindow;

    private final View dimView;
    private final LinearLayout card;

    private AnimatorSet currentAnimation;
    private boolean isDismissing;
    private boolean finishingAnimation;

    public Modal(Context context, View contentView, ActionButton... buttons) {
        this(context, null, contentView, buttons);
    }

    public Modal(Context context, TextView titleView, View contentView, ActionButton... buttons) {
        popupWindow = new PopupWindow(context) {
            @Override
            public void dismiss() {
                if (!isDismissing && popupWindow.isShowing() && !finishingAnimation) {
                    Modal.this.dismiss();
                } else {
                    super.dismiss();
                }
            }
        };

        FrameLayout overlayFrame = new FrameLayout(context);

        dimView = new View(context);
        dimView.setBackground(new ColorDrawable(SCREEN_DIM_COLOR));
        dimView.setClickable(true);
        dimView.setOnClickListener(v -> dismiss());
        overlayFrame.addView(dimView, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        card = new LinearLayout(context) {
            {
                setOrientation(LinearLayout.VERTICAL);
            }

            @Override
            public boolean onTouchEvent(@NonNull MotionEvent event) {
                super.onTouchEvent(event);
                return true;
            }
        };

        applyOutlinedCardBackground(context, card);

        int topPadding = card.dp(CONTENT_PADDING);
        int sidePadding = card.dp(CONTENT_PADDING);
        int bottomPadding = card.dp(CONTENT_PADDING);
        card.setPadding(sidePadding, topPadding, sidePadding, bottomPadding);

        if (titleView != null) {
            TypedValue tv = new TypedValue();
            var theme = context.getTheme();
            if (theme.resolveAttribute(R.ns, R.attr.colorOnSurface, tv, true)) {
                titleView.setTextColor(theme.getResources().loadColorStateList(tv, theme));
            } else {
                titleView.setTextColor(0xFFFFFFFF);
            }
            titleView.setTextSize(Theme.TEXT_SIZE_LARGER);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            titleParams.setMargins(0, 0, 0, card.dp(CONTENT_PADDING));
            card.addView(titleView, titleParams);
        }

        contentView.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        card.addView(contentView);

        View divider = new View(context);
        ShapeDrawable dividerBg = new ShapeDrawable();
        dividerBg.setShape(ShapeDrawable.HLINE);
        dividerBg.setSize(card.dp(DIVIDER_HEIGHT), card.dp(DIVIDER_HEIGHT));
        TypedValue dividerTv = new TypedValue();
        var theme = context.getTheme();
        if (theme.resolveAttribute(R.ns, R.attr.colorOutlineVariant, dividerTv, true)) {
            dividerBg.setColor(theme.getResources().loadColorStateList(dividerTv, theme));
        } else {
            dividerBg.setColor(0xFF3E3E42);
        }
        divider.setBackground(dividerBg);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                MATCH_PARENT, card.dp(DIVIDER_HEIGHT));
        dividerParams.setMargins(0, card.dp(CONTENT_PADDING), 0, card.dp(BUTTON_PADDING));
        card.addView(divider, dividerParams);

        LinearLayout buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        card.addView(buttonRow, new LinearLayout.LayoutParams(
                MATCH_PARENT, card.dp(32)));

        float weight = 1.0f;
        int gapPx = card.dp(BUTTON_GAP);

        ButtonInsetBackgroundFactory backgroundFactory = ButtonInsetBackgroundFactory.builder().cornerRadius(card.dp(8)).build();

        for (int i = 0; i < buttons.length; i++) {
            ActionButton buttonMeta = buttons[i];
            Button button = new Button(context);
            button.setText(buttonMeta.getText());
            TypedValue btnTv = new TypedValue();
            if (context.getTheme().resolveAttribute(R.ns, R.attr.colorOnSurface, btnTv, true)) {
                button.setTextColor(context.getTheme().getResources().loadColorStateList(btnTv, context.getTheme()));
            } else {
                button.setTextColor(0xFFFFFFFF);
            }
            button.setGravity(Gravity.CENTER);
            button.setTextSize(14);
            button.setOnClickListener(v -> buttonMeta.getOnClickListener().accept(buttonMeta, this));

            button.setBackground(backgroundFactory.newBackgroundDrawable());

            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    0, MATCH_PARENT, weight);
            if (i < buttons.length - 1) {
                btnParams.setMargins(0, 0, gapPx, 0);
            }
            buttonRow.addView(button, btnParams);

            buttonMeta.injectView(button, buttonRow, btnParams);
        }

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                card.dp(MAX_WIDTH), WRAP_CONTENT);
        cardParams.gravity = Gravity.CENTER;
        overlayFrame.addView(card, cardParams);

        popupWindow.setContentView(overlayFrame);
        popupWindow.setWidth(MATCH_PARENT);
        popupWindow.setHeight(MATCH_PARENT);
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(false);
        popupWindow.setTouchModal(true);
    }

    private void applyOutlinedCardBackground(Context context, View layout) {
        ShapeDrawable bg = new ShapeDrawable();
        bg.setCornerRadius(layout.dp(CORNER_RADIUS));
        TypedValue value = new TypedValue();
        var theme = context.getTheme();
        if (theme.resolveAttribute(R.ns, R.attr.colorSurface, value, true)) {
            bg.setColor(theme.getResources().loadColorStateList(value, theme));
        } else {
            bg.setColor(0xFF2D2D30);
        }

        int[] strokeColors = new int[2];
        if (theme.resolveAttribute(R.ns, R.attr.colorOutlineVariant, value, true)) {
            strokeColors[0] = value.data;
            theme.resolveAttribute(R.ns, R.attr.colorOutline, value, true);
            strokeColors[1] = ColorStateList.modulateColor(value.data, 0.12f);
        } else {
            strokeColors[0] = 0x20FFFFFF;
            strokeColors[1] = 0x08FFFFFF;
        }
        bg.setStroke(layout.dp(1), new ColorStateList(
                new int[][]{
                        StateSet.get(StateSet.VIEW_STATE_ENABLED),
                        StateSet.WILD_CARD
                },
                strokeColors
        ));
        layout.setBackground(bg);
    }

    public void show() {
        //noinspection UnstableApiUsage
        View decorView = UIManager.getInstance().getDecorView();
        if (decorView == null) {
            return;
        }
        long now = System.currentTimeMillis();
        decorView.dispatchGenericMotionEvent(
                MotionEvent.obtain(now, MotionEvent.ACTION_HOVER_EXIT, 0, 0, 0)
        );

        View focused = decorView.findFocus();
        if (focused != null) {
            focused.clearFocus();
        }

        card.setAlpha(0f);
        card.setScaleX(0.95f);
        card.setScaleY(0.95f);
        dimView.setAlpha(0f);
        dimView.setClickable(true);

        isDismissing = false;
        finishingAnimation = false;

        popupWindow.showAtLocation(decorView, Gravity.CENTER, 0, 0);
        popupWindow.getContentView().setFocusableInTouchMode(true);
        popupWindow.getContentView().requestFocus();

        card.post(() -> {
            card.setPivotX(card.getWidth() / 2f);
            card.setPivotY(card.getHeight() / 2f);

            ObjectAnimator dimFadeIn = ObjectAnimator.ofFloat(dimView, View.ALPHA, 0f, 1f);
            ObjectAnimator cardFadeIn = ObjectAnimator.ofFloat(card, View.ALPHA, 0f, 1f);
            ObjectAnimator cardScaleXIn = ObjectAnimator.ofFloat(card, View.SCALE_X, 0.95f, 1f);
            ObjectAnimator cardScaleYIn = ObjectAnimator.ofFloat(card, View.SCALE_Y, 0.95f, 1f);

            currentAnimation = new AnimatorSet();
            currentAnimation.playTogether(dimFadeIn, cardFadeIn, cardScaleXIn, cardScaleYIn);
            currentAnimation.setDuration(SHOW_DURATION);
            currentAnimation.setInterpolator(Easing.EASE_OUT_QUAD);
            currentAnimation.start();
        });
    }

    public void dismiss() {
        if (isDismissing || !popupWindow.isShowing()) {
            return;
        }
        isDismissing = true;

        dimView.setClickable(false);

        if (currentAnimation != null) {
            currentAnimation.cancel();
        }

        ObjectAnimator dimFadeOut = ObjectAnimator.ofFloat(dimView, View.ALPHA, dimView.getAlpha(), 0f);
        ObjectAnimator cardFadeOut = ObjectAnimator.ofFloat(card, View.ALPHA, card.getAlpha(), 0f);
        ObjectAnimator cardScaleXOut = ObjectAnimator.ofFloat(card, View.SCALE_X, card.getScaleX(), 0.95f);
        ObjectAnimator cardScaleYOut = ObjectAnimator.ofFloat(card, View.SCALE_Y, card.getScaleY(), 0.95f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(dimFadeOut, cardFadeOut, cardScaleXOut, cardScaleYOut);
        set.setDuration(DISMISS_DURATION);
        set.setInterpolator(Easing.EASE_IN_QUAD);
        set.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                finishingAnimation = true;
                popupWindow.dismiss();
                finishingAnimation = false;
                isDismissing = false;
            }
        });
        set.start();
    }

    public void setOnDismissListener(PopupWindow.OnDismissListener listener) {
        popupWindow.setOnDismissListener(listener);
    }

    public boolean isShowing() {
        return popupWindow.isShowing();
    }

    public static final class ActionButton {
        @Getter
        private String text;
        @Getter
        private final BiConsumer<ActionButton, Modal> onClickListener;

        @Getter
        private Button button;
        private ViewGroup parent;
        private ViewGroup.LayoutParams params;
        private float originalWeight;

        public ActionButton(String text, BiConsumer<ActionButton, Modal> onClickListener) {
            this.text = text;
            this.onClickListener = onClickListener;
        }

        void injectView(Button button, ViewGroup parent, ViewGroup.LayoutParams params) {
            this.button = button;
            this.parent = parent;
            this.params = params;
            if (params instanceof LinearLayout.LayoutParams llp) {
                this.originalWeight = llp.weight;
            }
        }

        public void setText(String text) {
            this.text = text;
            if (button != null) {
                button.setText(text);
            }
        }

        public void setEnabled(boolean enabled) {
            if (button != null) {
                button.setEnabled(enabled);
            }
        }

        public void animateVisibility(boolean show, int duration) {
            if (button == null || parent == null) return;

            if (show) {
                if (button.getVisibility() != View.GONE) return;
                button.setVisibility(View.VISIBLE);
                button.setAlpha(0f);
                button.setScaleX(0f);
            }

            float targetAlpha = show ? 1f : 0f;
            float targetScaleX = show ? 1f : 0f;

            ObjectAnimator alpha = ObjectAnimator.ofFloat(button, View.ALPHA, button.getAlpha(), targetAlpha);
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(button, View.SCALE_X, button.getScaleX(), targetScaleX);
            AnimatorSet set = new AnimatorSet();
            set.playTogether(alpha, scaleX);
            set.setDuration(duration);
            set.setInterpolator(show ? Easing.EASE_OUT_QUAD : Easing.EASE_IN_QUAD);
            if (!show) {
                set.addListener(new AnimatorListener() {
                    @Override
                    public void onAnimationEnd(@NonNull Animator animation) {
                        button.setVisibility(View.GONE);
                        button.setScaleX(1f);
                        button.setAlpha(1f);
                    }
                });
            }
            set.start();
        }
    }
}
