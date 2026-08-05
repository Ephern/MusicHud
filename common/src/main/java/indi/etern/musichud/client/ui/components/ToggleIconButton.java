package indi.etern.musichud.client.ui.components;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.widget.CheckableImageButton;
import icyllis.modernui.widget.ImageView;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;

import java.util.function.Supplier;

public class ToggleIconButton extends CheckableImageButton {
    public ToggleIconButton(Context context, ToggleTrackLikeStateButton.Appearance appearance) {
        this(context, appearance, false);
    }

    public ToggleIconButton(Context context, ToggleTrackLikeStateButton.Appearance appearance, boolean initialChecked) {
        super(context);

        setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        CharSequence tooltipOn = appearance.tooltipOn.get();
        if (tooltipOn != null && !tooltipOn.isEmpty()) {
            setTooltipTextOn(tooltipOn);
        }
        Image imageOn = appearance.imageOn.get();

        CharSequence tooltipOff = appearance.tooltipOff.get();
        if (tooltipOff != null && !tooltipOff.isEmpty()) {
            setTooltipTextOff(tooltipOff);
        }
        Image imageOff = appearance.imageOff.get();

        StateListDrawable selector = new StateListDrawable();
        var resources = getContext().getResources();
        selector.addState(new int[]{R.attr.state_checkable, R.attr.state_checked},
                new InsetDrawable(new ScaledImageDrawable(resources, imageOn, dp(12), dp(16)), dp(3)));
        selector.addState(new int[]{R.attr.state_checkable},
                new InsetDrawable(new ScaledImageDrawable(resources, imageOff, dp(12), dp(16)), dp(3)));
        setImageDrawable(selector);

        setChecked(initialChecked);
    }

    public record Appearance(
            Supplier<CharSequence> tooltipOn,
            Supplier<CharSequence> tooltipOff,
            Supplier<Image> imageOn,
            Supplier<Image> imageOff) {
    }
}
