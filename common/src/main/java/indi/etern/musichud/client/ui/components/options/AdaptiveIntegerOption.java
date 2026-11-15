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

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Integer option with a right-aligned, width-adaptive seek bar.
 * <p>
 * Modified from Modern UI's {@code PreferencesFragment.IntegerOption}.
 */
public class AdaptiveIntegerOption implements
        View.OnFocusChangeListener,
        SeekBar.OnSeekBarChangeListener,
        View.OnCreateContextMenuListener,
        MenuItem.OnMenuItemClickListener {

    protected final LinearLayout layout;
    protected final EditText input;
    private final SliderHost sliderHost;

    protected final Supplier<Integer> getter;
    protected final Consumer<Integer> setter;
    @Nullable
    protected Runnable onChanged;

    @Nullable
    protected SeekBar slider;
    protected int minValue = Integer.MIN_VALUE;
    protected int maxValue = Integer.MAX_VALUE;
    protected int stepSize = 1;

    protected boolean hasDefaultValue = false;
    protected int defaultValue;

    public AdaptiveIntegerOption(Context context, String name,
                                 Supplier<Integer> getter,
                                 Consumer<Integer> setter) {
        OptionRow row = OptionRow.create(context, name);
        layout = row.layout;
        input = row.input;
        sliderHost = row.sliderHost;
        this.getter = getter;
        this.setter = setter;
    }

    public AdaptiveIntegerOption setRange(int minValue, int maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;
        return this;
    }

    public AdaptiveIntegerOption setRange(int minValue, int maxValue, int stepSize) {
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.stepSize = stepSize;
        return this;
    }

    public AdaptiveIntegerOption setDefaultValue(int defaultValue) {
        this.hasDefaultValue = true;
        this.defaultValue = defaultValue;
        return this;
    }

    public AdaptiveIntegerOption setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
        return this;
    }

    @NotNull
    public LinearLayout create(@Nullable ViewGroup parent) {
        return create(parent, -1);
    }

    @NotNull
    public LinearLayout create(@Nullable ViewGroup parent, int maxLength) {
        int curValue = getter.get();
        int steps = (maxValue - minValue) / stepSize;
        if (steps <= 200) {
            slider = addSliderToLayout(steps <= 10);
            slider.setMax(steps);
            slider.setProgress((curValue - minValue) / stepSize);
            slider.setOnSeekBarChangeListener(this);
        }
        InputFilter digitsFilter = DigitsInputFilter.getInstance((Locale) null);
        if (maxLength > 0) {
            input.setFilters(digitsFilter, new InputFilter.LengthFilter(maxLength));
        } else {
            input.setFilters(digitsFilter);
        }
        input.setText(Integer.toString(curValue));
        input.setOnFocusChangeListener(this);
        input.setMinWidth(slider != null ? slider.dp(60) : input.dp(80));
        if (hasDefaultValue) {
            defaultValue = (defaultValue / stepSize) * stepSize;
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
            int newValue;
            try {
                newValue = MathUtil.clamp(Integer.parseInt(v.getText().toString()),
                        minValue, maxValue);
            } catch (NumberFormatException e) {
                newValue = hasDefaultValue ? defaultValue : minValue;
            }
            replaceText(v, Integer.toString(newValue));
            if (newValue != getter.get()) {
                setter.accept(newValue);
                if (slider != null) {
                    int curProgress = (newValue - minValue) / stepSize;
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
        int newValue = seekBar.getProgress() * stepSize + minValue;
        replaceText(input, Integer.toString(newValue));
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        int newValue = seekBar.getProgress() * stepSize + minValue;
        if (newValue != getter.get()) {
            setter.accept(newValue);
            replaceText(input, Integer.toString(newValue));
            if (onChanged != null) {
                onChanged.run();
            }
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        int reset = resetValue();
        boolean canReset;
        if (slider != null) {
            int curValue = slider.getProgress() * stepSize + minValue;
            canReset = curValue != reset;
        } else {
            try {
                int curValue = Integer.parseInt(input.getText().toString());
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
            int reset = resetValue();
            replaceText(input, Integer.toString(reset));
            setter.accept(reset);
            if (slider != null) {
                int newProgress = (reset - minValue) / stepSize;
                slider.setProgress(newProgress, true);
            }
            if (onChanged != null) {
                onChanged.run();
            }
            return true;
        }
        return false;
    }

    private int resetValue() {
        return MathUtil.clamp(defaultValue, minValue, maxValue);
    }

    private static void replaceText(EditText editText, CharSequence newText) {
        Editable text = editText.getText();
        text.replace(0, text.length(), newText);
    }
}
