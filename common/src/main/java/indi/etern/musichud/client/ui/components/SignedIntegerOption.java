package indi.etern.musichud.client.ui.components;

import icyllis.arc3d.core.MathUtil;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.InputFilter;
import icyllis.modernui.text.method.DigitsInputFilter;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SignedIntegerOption extends AdaptiveIntegerOption {
    public SignedIntegerOption(Context context, String name,
                               Supplier<Integer> getter,
                               Consumer<Integer> setter) {
        super(context, name, getter, setter);
    }

    @Override
    @NotNull
    public LinearLayout create(ViewGroup parent, int maxLength) {
        LinearLayout result = super.create(parent, maxLength);
        InputFilter digits = DigitsInputFilter.getInstance(null, minValue < 0, false);
        if (maxLength > 0) {
            input.setFilters(digits, new InputFilter.LengthFilter(maxLength));
        } else {
            input.setFilters(digits);
        }
        input.setText(Integer.toString(getter.get()));
        return result;
    }

    public void refresh() {
        MuiModApi.postToUiThread(() -> {
            int currentValue = getter.get();
            input.setText(Integer.toString(currentValue));
            if (slider != null) {
                int progress = MathUtil.clamp((currentValue - minValue) / stepSize, 0, slider.getMax());
                slider.setProgress(progress);
            }
        });
    }
}
