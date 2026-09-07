package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.widget.ImageButton;
import icyllis.modernui.widget.ImageView;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class CycleIconButton extends ImageButton {
    @Getter
    List<State> states = new ArrayList<>();
    int index = 0;
    State current = null;
    private boolean warned = false;
    private Runnable warnedClickHandler = null;

    public CycleIconButton(Context context) {
        super(context);
        setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        setOnClickListener((button) -> {
            if (states.isEmpty()) {
                return;
            }
            if (warned) {
                // Warning mode replaces the cycle behavior with a custom handler
                // (e.g. a re-sync attempt for a load-errored idle play source)
                if (warnedClickHandler != null) {
                    warnedClickHandler.run();
                }
                return;
            }
            State previous = current;
            if (previous != null) {
                previous.switchedOff.run();
            }
            apply(index + 1);
            current.switchedOn.run();
        });
    }

    /** Visually selects a state (icon/tooltip) without firing callbacks; wraps around. */
    public void apply(int index) {
        if (states.isEmpty()) {
            return;
        }
        int normalized = Math.floorMod(index, states.size());
        this.index = normalized;
        current = states.get(normalized);
        setTooltipText(current.tooltip.get());
        setImageDrawable(new InsetDrawable(new ScaledImageDrawable(getContext().getResources(), current.image.get(), dp(12), dp(16)), dp(3)));
    }

    /**
     * Warning mode: replaces the current state's icon/tooltip and routes clicks to
     * {@code clickHandler} instead of cycling. The selected index is preserved, so
     * {@code setWarned(false, ...)} restores the previously selected state's visuals.
     */
    public void setWarned(boolean warned, CharSequence tooltip, Image icon, Runnable clickHandler) {
        this.warned = warned;
        this.warnedClickHandler = warned ? clickHandler : null;
        if (warned) {
            setTooltipText(tooltip);
            setImageDrawable(new InsetDrawable(new ScaledImageDrawable(getContext().getResources(), icon, dp(12), dp(16)), dp(3)));
        } else {
            apply(index);
        }
    }

    public record State(
            Supplier<CharSequence> tooltip,
            Supplier<Image> image,
            Runnable switchedOn,
            Runnable switchedOff) {
    }
}
