package indi.etern.musichud.client.ui.components;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.widget.CheckableImageButton;
import icyllis.modernui.widget.ImageView;
import indi.etern.musichud.beans.state.IMusicTrackState;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;

import java.util.function.Supplier;

public class ToggleIconButton extends CheckableImageButton {
    public record Appearance(
            Supplier<CharSequence> tooltipOn,
            Supplier<CharSequence> tooltipOff,
            Supplier<Image> imageOn,
            Supplier<Image> imageOff) {
    }

    public ToggleIconButton(Context context, ToggleTrackLikeStateButton.Appearance appearance) {
        this(context, appearance, false);
    }

    public ToggleIconButton(Context context, ToggleTrackLikeStateButton.Appearance appearance, boolean initialChecked) {
        super(context);

        setScaleType(ImageView.ScaleType.CENTER);

        CharSequence tooltipOn = appearance.tooltipOn.get();
        Image imageOn = appearance.imageOn.get();
        setTooltipTextOn(tooltipOn);

        CharSequence tooltipOff = appearance.tooltipOff.get();
        Image imageOff = appearance.imageOff.get();
        setTooltipTextOff(tooltipOff);

        StateListDrawable selector = new StateListDrawable();
        var resources = getContext().getResources();
        selector.addState(new int[]{R.attr.state_checkable, R.attr.state_checked},
                new ScaledImageDrawable(resources, imageOn, dp(8), dp(16), dp(16)));
        selector.addState(new int[]{R.attr.state_checkable},
                new ScaledImageDrawable(resources, imageOff, dp(8), dp(16), dp(16)));
        setImageDrawable(selector);

        setChecked(initialChecked);
    }

    protected IMusicTrackState.IPlaylistSubState playlistSubState;
}
