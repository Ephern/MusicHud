package indi.etern.musichud.client.utils.ui;

import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.animation.StateListAnimator;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.graphics.drawable.LayerDrawable;
import icyllis.modernui.graphics.drawable.RippleDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.util.IntProperty;
import icyllis.modernui.view.View;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.drawable.BackdropBlurDrawable;
import indi.etern.musichud.client.ui.drawable.LayerBackdropBlurDrawable;
import lombok.Builder;
import org.jetbrains.annotations.Nullable;

@Builder
public class InsetBackgroundFactory {
    public enum BackdropBlur {
        NONE,
        /** Blurs the screen framebuffer behind the whole UI. */
        SCREEN,
        /** Blurs the UI content already recorded underneath the view. */
        UI_LAYER
    }

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
    BackdropBlur backdropBlur = BackdropBlur.NONE;
    @Builder.Default
    int backdropBlurRadius = 0;
    public record Padding(int left,int top,int right,int bottom) {}

    public ShapeDrawable applyBackgroundTo(View view) {
        releaseBackdropBlur(view.getBackground());

        ShapeDrawable background = newShapeDrawable();
        RippleDrawable ripple = new RippleDrawable(Theme.ITEM_RIPPLE_COLOR_STATES, background, null);
        InsetDrawable tint = new InsetDrawable(ripple, inset);
        Drawable blur = createBackdropBlur();
        if (blur == null) {
            view.setBackground(tint);
        } else {
            view.setBackground(new LayerDrawable(new InsetDrawable(blur, inset), tint));
            attachBackdropBlur(blur, view);
        }
        attachStateTransition(view, background);
        return background;
    }

    private @Nullable Drawable createBackdropBlur() {
        if (backdropBlurRadius <= 0) {
            return null;
        }
        return switch (backdropBlur) {
            case NONE -> null;
            case SCREEN -> new BackdropBlurDrawable(cornerRadius, backdropBlurRadius);
            case UI_LAYER -> new LayerBackdropBlurDrawable(cornerRadius, backdropBlurRadius);
        };
    }

    private static void attachBackdropBlur(Drawable blur, View view) {
        if (blur instanceof BackdropBlurDrawable screenBlur) {
            screenBlur.attachTo(view);
        } else if (blur instanceof LayerBackdropBlurDrawable layerBlur) {
            layerBlur.attachTo(view);
        }
    }

    /** Frees the GPU resources of a blur installed by a previous call, which re-applying would leak. */
    private static void releaseBackdropBlur(@Nullable Drawable drawable) {
        if (drawable instanceof LayerDrawable layer) {
            for (int i = 0; i < layer.getNumberOfLayers(); i++) {
                releaseBackdropBlur(layer.getDrawable(i));
            }
        } else if (drawable instanceof InsetDrawable insetDrawable) {
            releaseBackdropBlur(insetDrawable.getDrawable());
        } else if (drawable instanceof BackdropBlurDrawable blur) {
            blur.release();
        } else if (drawable instanceof LayerBackdropBlurDrawable blur) {
            blur.release();
        }
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
            ObjectAnimator animator = ObjectAnimator.ofArgb(null, colorProperty,
                            Color.toArgb(colors[i]))
                    .setDuration(transitionDuration);
            stateListAnimator.addState(states[i], animator);
        }
        view.setStateListAnimator(stateListAnimator);
    }
}
