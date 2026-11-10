package indi.etern.musichud.client.utils.ui;

import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.animation.StateListAnimator;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.graphics.drawable.LayerDrawable;
import icyllis.modernui.graphics.drawable.RippleDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.IntProperty;
import icyllis.modernui.view.View;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.drawable.BackdropBlurDrawable;
import lombok.Builder;

@Builder
public class InsetBackgroundFactory {
    @Builder.Default
    Padding padding = new Padding(0,0,0,0);
    @Builder.Default
    int cornerRadius = 0;
    @Builder.Default
    int inset = 0;
    @Builder.Default
    ColorStateList backgroundColor = Theme.GHOST_CHECK_BUTTON_STATES;
    @Builder.Default
    int strokeWidth = 0;
    @Builder.Default
    int strokeColor = 0;
    @Builder.Default
    long transitionDuration = 100;
    @Builder.Default
    boolean backdropBlur = false;
    @Builder.Default
    int backdropBlurRadius = 0;
    public record Padding(int left,int top,int right,int bottom) {}

    public void applyBackgroundTo(View view) {
        ShapeDrawable background = newShapeDrawable();
        RippleDrawable ripple = new RippleDrawable(Theme.ITEM_RIPPLE_COLOR_STATES, background, null);
        InsetDrawable tint = new InsetDrawable(ripple, inset);
        if (backdropBlur && backdropBlurRadius > 0) {
            BackdropBlurDrawable blur = new BackdropBlurDrawable(cornerRadius, backdropBlurRadius);
            view.setBackground(new LayerDrawable(new InsetDrawable(blur, inset), tint));
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    blur.release();
                }
            });
        } else {
            view.setBackground(tint);
        }
        attachStateTransition(view, background);
    }

    private ShapeDrawable newShapeDrawable() {
        ShapeDrawable background = new ShapeDrawable();
        if (padding != null) {
            background.setPadding(padding.left, padding.top, padding.right, padding.bottom);
        }
        background.setCornerRadius(cornerRadius);
        background.setColor(backgroundColor);
        if (strokeWidth > 0) {
            background.setStroke(strokeWidth, strokeColor);
        }
        return background;
    }

    private void attachStateTransition(View view, ShapeDrawable background) {
        IntProperty<View> colorProperty = new IntProperty<>("backgroundColor") {
            @Override
            public void setValue(View target, int color) {
                background.setColor(color);
            }

            @Override
            public Integer get(View target) {
                ColorStateList color = background.getColor();
                return color == null ? 0x00000000 : Color.toArgb(color.getDefaultColor());
            }
        };
        StateListAnimator stateListAnimator = new StateListAnimator();
        //noinspection UnstableApiUsage
        int[][] states = backgroundColor.getStates();
        //noinspection UnstableApiUsage
        long[] colors = backgroundColor.getColors();
        for (int i = 0; i < states.length; i++) {
            ObjectAnimator animator = ObjectAnimator.ofArgb(null, colorProperty, Color.toArgb(colors[i]))
                    .setDuration(transitionDuration);
            stateListAnimator.addState(states[i], animator);
        }
        view.setStateListAnimator(stateListAnimator);
    }
}
