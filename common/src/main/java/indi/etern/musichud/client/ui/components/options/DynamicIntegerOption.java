package indi.etern.musichud.client.ui.components.options;

import icyllis.arc3d.core.MathUtil;
import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.SeekBar;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class DynamicIntegerOption extends AdaptiveIntegerOption {
    public DynamicIntegerOption(Context context, String name,
                                Supplier<Integer> getter,
                                Consumer<Integer> setter) {
        super(context, name, getter, setter);
    }

    public DynamicIntegerOption updateRange(int min, int max, int step) {
        super.setRange(min, max, step);
        if (slider == null) {
            return this;
        }

        slider.setOnSeekBarChangeListener(null);

        int currentValue = getter.get();
        int clampedValue = MathUtil.clamp(currentValue, min, max);

        input.setText(Integer.toString(clampedValue));

        if (clampedValue != currentValue) {
            setter.accept(clampedValue);
            if (onChanged != null) {
                onChanged.run();
            }
        }

        rebuildSlider(min, max, step);
        return this;
    }

    public void rebuildSlider(int newMinValue, int newMaxValue, int newStepSize) {
        if (slider == null) {
            return;
        }
        // The discrete style depends on the step count, so the bar is rebuilt instead of resized.
        ViewGroup parent = (ViewGroup) slider.getParent();
        assert parent != null;
        int index = parent.indexOfChild(slider);
        ViewGroup.LayoutParams params = slider.getLayoutParams();

        parent.removeView(slider);

        minValue = newMinValue;
        maxValue = newMaxValue;
        stepSize = newStepSize;

        int newSteps = (newMaxValue - newMinValue) / newStepSize;
        slider = new SeekBar(parent.getContext(), null, null,
                newSteps <= 10 ? R.style.Widget_Material3_SeekBar_Discrete_Slider
                        : R.style.Widget_Material3_SeekBar_Slider);
        slider.setId(R.id.button2);
        slider.setUserAnimationEnabled(true);
        slider.setMax(newSteps);

        int currentValue = getter.get();
        int clampedValue = MathUtil.clamp(currentValue, newMinValue, newMaxValue);
        int newProgress = (clampedValue - newMinValue) / newStepSize;
        slider.setProgress(newProgress);
        slider.setOnSeekBarChangeListener(this);

        parent.addView(slider, index, params);

        input.setText(Integer.toString(clampedValue));
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