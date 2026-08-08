package indi.etern.musichud.client.utils.ui;

import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.animation.StateListAnimator;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.graphics.drawable.RippleDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.IntProperty;
import icyllis.modernui.view.View;
import indi.etern.musichud.client.ui.Theme;
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
    long transitionDuration = 150;
    public record Padding(int left,int top,int right,int bottom) {}

/*
    public Drawable newBackgroundDrawable() {
        ShapeDrawable background = newShapeDrawable();
        RippleDrawable ripple = new RippleDrawable(Theme.ITEM_RIPPLE_COLOR_STATES, background, null);
        return new InsetDrawable(ripple, inset);
    }
*/

    public void applyBackgroundTo(View view) {
        ShapeDrawable background = newShapeDrawable();
        RippleDrawable ripple = new RippleDrawable(Theme.ITEM_RIPPLE_COLOR_STATES, background, null);
        view.setBackground(new InsetDrawable(ripple, inset));
        attachStateTransition(view, background);
    }

    private ShapeDrawable newShapeDrawable() {
        ShapeDrawable background = new ShapeDrawable();
        if (padding != null) {
            background.setPadding(padding.left, padding.top, padding.right, padding.bottom);
        }
        background.setCornerRadius(cornerRadius);
        background.setColor(backgroundColor);
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
                return background.getColor().getDefaultColor();
            }
        };
        StateListAnimator stateListAnimator = new StateListAnimator();
        //noinspection UnstableApiUsage
        int[][] states = backgroundColor.getStates();
        //noinspection UnstableApiUsage
        int[] colors = backgroundColor.getColors();
        for (int i = 0; i < states.length; i++) {
            ObjectAnimator animator = ObjectAnimator.ofArgb(null, colorProperty, colors[i])
                    .setDuration(transitionDuration);
            stateListAnimator.addState(states[i], animator);
        }
        view.setStateListAnimator(stateListAnimator);
    }
}
