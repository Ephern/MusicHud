package indi.etern.musichud.client.ui.utils;

import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.graphics.drawable.RippleDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import indi.etern.musichud.client.ui.Theme;
import lombok.Builder;

@Builder
public class ButtonInsetBackgroundFactory {
    Padding padding;
    int cornerRadius;
    int inset;
    public record Padding(int left,int top,int right,int bottom) {}

    public Drawable newBackgroundDrawable() {
        ShapeDrawable background = new ShapeDrawable();
        if (padding != null) {
            background.setPadding(padding.left,padding.top,padding.left,padding.bottom);
        }
        background.setCornerRadius(cornerRadius);
        background.setColor(Theme.GHOST_BUTTON_STATES);

        RippleDrawable ripple = new RippleDrawable(Theme.ITEM_RIPPLE_COLOR_STATES, background, null);
        return new InsetDrawable(ripple, inset);
    }
}