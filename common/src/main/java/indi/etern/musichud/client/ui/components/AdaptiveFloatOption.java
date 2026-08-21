package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.ui.PreferencesFragment;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Float option that hides its seek bar when the row is squeezed too narrow
 */
public class AdaptiveFloatOption extends PreferencesFragment.FloatOption {
    private static final int MIN_SLIDER_WIDTH_DP = 40;

    private int lastLayoutWidth = -1;

    public AdaptiveFloatOption(Context context, String name,
                               Supplier<Double> getter,
                               Consumer<Double> setter) {
        super(context, name, getter, setter);
    }

    @Override
    @NotNull
    public LinearLayout create(ViewGroup parent, int maxLength) {
        LinearLayout result = super.create(parent, maxLength);
        if (slider != null) {
            LinearLayout.LayoutParams sp = (LinearLayout.LayoutParams) slider.getLayoutParams();
            sp.width = 0;
            sp.weight = 1;
            slider.setLayoutParams(sp);
            layout.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (slider == null) {
                    return;
                }
                int width = layout.getWidth();
                if (slider.getVisibility() == View.VISIBLE) {
                    int sliderWidth = slider.getWidth();
                    if (sliderWidth > 0 && sliderWidth < slider.dp(MIN_SLIDER_WIDTH_DP)) {
                        slider.setVisibility(View.GONE);
                    }
                } else if (width > lastLayoutWidth) {
                    // probe: show and let the next layout pass decide by measured width
                    slider.setVisibility(View.VISIBLE);
                }
                lastLayoutWidth = width;
            });
        }
        return result;
    }
}
