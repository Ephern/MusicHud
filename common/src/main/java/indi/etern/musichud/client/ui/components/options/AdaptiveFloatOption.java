/*
 * Modified from Modern UI.
 */

/*
 * Modern UI.
 * Copyright (C) 2023-2026 BloCamLimb. All rights reserved.
 *
 * Modern UI is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Modern UI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Modern UI. If not, see <https://www.gnu.org/licenses/>.
 */

package indi.etern.musichud.client.ui.components.options;

import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.MathUtil;
import icyllis.modernui.mc.ui.PreferencesFragment;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.InputFilter;
import icyllis.modernui.text.method.DigitsInputFilter;
import icyllis.modernui.view.ContextMenu;
import icyllis.modernui.view.MenuItem;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.SeekBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Float option with a right-aligned, width-adaptive seek bar.
 * <p>
 * Modified from Modern UI's {@code PreferencesFragment.FloatOption}.
 */
public class AdaptiveFloatOption implements
        View.OnFocusChangeListener,
        SeekBar.OnSeekBarChangeListener,
        View.OnCreateContextMenuListener,
        MenuItem.OnMenuItemClickListener {

    protected final LinearLayout layout;
    protected final EditText input;
    private final SliderHost sliderHost;

    protected final Supplier<Double> getter;
    protected final Consumer<Double> setter;
    @Nullable
    protected Runnable onChanged;

    @Nullable
    protected SeekBar slider;
    protected double minValue = Double.NEGATIVE_INFINITY;
    protected double maxValue = Double.POSITIVE_INFINITY;
    protected double denominator = Double.NaN;

    protected double defaultValue = Double.NaN;

    public AdaptiveFloatOption(Context context, String name,
                               Supplier<Double> getter,
                               Consumer<Double> setter) {
        OptionRow row = OptionRow.create(context, name);
        layout = row.layout;
        input = row.input;
        sliderHost = row.sliderHost;
        this.getter = getter;
        this.setter = setter;
    }

    public AdaptiveFloatOption setRange(double minValue, double maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;
        return this;
    }

    public AdaptiveFloatOption setRange(double minValue, double maxValue, double denominator) {
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.denominator = denominator;
        return this;
    }

    public AdaptiveFloatOption setDefaultValue(double defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public AdaptiveFloatOption setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
        return this;
    }

    @NotNull
    public LinearLayout create(@Nullable ViewGroup parent) {
        return create(parent, -1);
    }

    @NotNull
    public LinearLayout create(@Nullable ViewGroup parent, int maxLength) {
        double curValue = getter.get();
        if (!Double.isNaN(denominator)) {
            int steps = (int) Math.round((maxValue - minValue) * denominator);
            if (steps <= 200) {
                slider = addSliderToLayout(steps <= 10);
                slider.setMax(steps);
                int curProgress = (int) Math.round((curValue - minValue) * denominator);
                slider.setProgress(curProgress);
                slider.setOnSeekBarChangeListener(this);
            }
        }
        InputFilter digitsFilter = DigitsInputFilter.getInstance(
                null, minValue < 0, true);
        if (maxLength > 0) {
            input.setFilters(digitsFilter, new InputFilter.LengthFilter(maxLength));
        } else {
            input.setFilters(digitsFilter);
        }
        input.setText(floatValueToString(curValue, denominator));
        input.setOnFocusChangeListener(this);
        input.setMinWidth(slider != null ? slider.dp(60) : input.dp(80));
        if (!Double.isNaN(defaultValue)) {
            if (!Double.isNaN(denominator)) {
                defaultValue = Math.round(defaultValue * denominator) / denominator;
            }
            layout.setOnCreateContextMenuListener(this);
        }
        if (parent != null) {
            parent.addView(layout);
        }
        return layout;
    }

    private SeekBar addSliderToLayout(boolean discrete) {
        var newSlider = new SeekBar(layout.getContext(), null, null,
                discrete ? R.style.Widget_Material3_SeekBar_Discrete_Slider
                        : R.style.Widget_Material3_SeekBar_Slider);
        newSlider.setId(R.id.button2);
        newSlider.setUserAnimationEnabled(true);
        sliderHost.addView(newSlider, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        return newSlider;
    }

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        assert view == input;
        if (!hasFocus) {
            EditText v = (EditText) view;
            double newValue;
            try {
                newValue = MathUtil.clamp(Double.parseDouble(v.getText().toString()),
                        minValue, maxValue);
            } catch (NumberFormatException e) {
                newValue = defaultValue;
            }
            if (Double.isNaN(newValue)) {
                newValue = minValue;
            }
            replaceText(v, floatValueToString(newValue, denominator));
            if (newValue != getter.get()) {
                setter.accept(newValue);
                if (slider != null) {
                    int curProgress = (int) Math.round((newValue - minValue) * denominator);
                    slider.setProgress(curProgress, true);
                }
                if (onChanged != null) {
                    onChanged.run();
                }
            }
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        double newValue = seekBar.getProgress() / denominator + minValue;
        replaceText(input, floatValueToString(newValue, denominator));
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        double newValue = seekBar.getProgress() / denominator + minValue;
        if (newValue != getter.get()) {
            setter.accept(newValue);
            replaceText(input, floatValueToString(newValue, denominator));
            if (onChanged != null) {
                onChanged.run();
            }
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        double reset = resetValue();
        boolean canReset;
        if (slider != null) {
            double curValue = slider.getProgress() / denominator + minValue;
            canReset = curValue != reset;
        } else {
            try {
                double curValue = Double.parseDouble(input.getText().toString());
                canReset = curValue != reset;
            } catch (NumberFormatException e) {
                canReset = true;
            }
        }
        PreferencesFragment.addResetToDefaultMenuItem(menu)
                .setEnabled(canReset)
                .setOnMenuItemClickListener(this);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == PreferencesFragment.ID_RESET_TO_DEFAULT) {
            double reset = resetValue();
            replaceText(input, floatValueToString(reset, denominator));
            setter.accept(reset);
            if (slider != null) {
                int newProgress = (int) Math.round((reset - minValue) * denominator);
                slider.setProgress(newProgress, true);
            }
            if (onChanged != null) {
                onChanged.run();
            }
            return true;
        }
        return false;
    }

    private double resetValue() {
        double reset = defaultValue;
        if (!Double.isNaN(denominator)) {
            reset = Math.round(reset * denominator) / denominator;
        }
        return MathUtil.clamp(reset, minValue, maxValue);
    }

    private static String floatValueToString(double value, double denominator) {
        if (Double.isNaN(denominator)) {
            return Double.toString(value);
        }
        return Double.toString(Math.round(value * denominator) / denominator);
    }

    private static void replaceText(EditText editText, CharSequence newText) {
        Editable text = editText.getText();
        text.replace(0, text.length(), newText);
    }
}
