package indi.etern.musichud.client.ui.components.buttons;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.graphics.drawable.StateListDrawable;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.CheckableImageButton;
import icyllis.modernui.widget.ImageView;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;

import java.util.function.Supplier;
import indi.etern.musichud.client.ui.components.Tooltip;

public class ToggleIconButton extends CheckableImageButton {
    private final CharSequence tooltipOn;
    private final CharSequence tooltipOff;

    public ToggleIconButton(Context context, ToggleTrackLikeStateButton.Appearance appearance) {
        this(context, appearance, false);
    }

    public ToggleIconButton(Context context, ToggleTrackLikeStateButton.Appearance appearance, boolean initialChecked) {
        super(context);

        setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        tooltipOn = appearance.tooltipOn.get();
        Image imageOn = appearance.imageOn.get();
        tooltipOff = appearance.tooltipOff.get();
        Image imageOff = appearance.imageOff.get();

        StateListDrawable selector = new StateListDrawable();
        var resources = getContext().getResources();
        selector.addState(new int[]{R.attr.state_checkable, R.attr.state_checked},
                new InsetDrawable(new ScaledImageDrawable(resources, imageOn, dp(12), dp(16)), dp(3)));
        selector.addState(new int[]{R.attr.state_checkable},
                new InsetDrawable(new ScaledImageDrawable(resources, imageOff, dp(12), dp(16)), dp(3)));
        setImageDrawable(selector);

        setChecked(initialChecked);
        applyTooltip();
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        applyTooltip();
    }

    private void applyTooltip() {
        Tooltip.attach(this, isChecked() ? tooltipOn : tooltipOff);
    }

    /** Disabled buttons must still show their tooltip, which the listener path skips. */
    @Override
    protected boolean dispatchHoverEvent(MotionEvent event) {
        Tooltip.handleHover(this, event);
        return super.dispatchHoverEvent(event);
    }

    public record Appearance(
            Supplier<CharSequence> tooltipOn,
            Supplier<CharSequence> tooltipOff,
            Supplier<Image> imageOn,
            Supplier<Image> imageOff) {
    }
}
