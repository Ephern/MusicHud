package indi.etern.musichud.client.ui.utils.ui;

import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.graphics.drawable.RippleDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.util.ColorStateList;
import indi.etern.musichud.client.ui.Theme;
import lombok.Builder;

@Builder
public class ButtonInsetBackgroundFactory {
    @Builder.Default
    Padding padding = new Padding(0,0,0,0);
    @Builder.Default
    int cornerRadius = 0;
    @Builder.Default
    int inset = 0;
    @Builder.Default
    ColorStateList backgroundColor = Theme.GHOST_CHECK_BUTTON_STATES;
    public record Padding(int left,int top,int right,int bottom) {}

    public Drawable newBackgroundDrawable() {
        ShapeDrawable background = new ShapeDrawable();
        if (padding != null) {
            background.setPadding(padding.left,padding.top,padding.left,padding.bottom);
        }
        background.setCornerRadius(cornerRadius);
        background.setColor(backgroundColor);

        RippleDrawable ripple = new RippleDrawable(Theme.ITEM_RIPPLE_COLOR_STATES, background, null);
        return new InsetDrawable(ripple, inset);
    }
}