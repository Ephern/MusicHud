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
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.resources.language.I18n;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

final class OptionRow {
    final LinearLayout layout;
    final EditText input;
    final SliderHost sliderHost;

    private OptionRow(LinearLayout layout, EditText input, SliderHost sliderHost) {
        this.layout = layout;
        this.input = input;
        this.sliderHost = sliderHost;
    }

    static OptionRow create(Context context, String name) {
        var layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setHorizontalGravity(Gravity.START);

        final int dp6 = layout.dp(6);
        final int dp8 = layout.dp(8);

        {
            var title = new TextView(context);
            title.setText(I18n.get(name));
            title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            title.setTextSize(14);

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0);
            params.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            layout.addView(title, params);
        }
        var sliderHost = new SliderHost(context);
        {
            var params = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1);
            params.gravity = Gravity.CENTER_VERTICAL;
            params.setMargins(dp8, 0, dp8, 0);
            layout.addView(sliderHost, params);
        }
        var input = new EditText(context, null, R.attr.editTextOutlinedStyle);
        {
            input.setId(R.id.input);
            input.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);

            var params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.gravity = Gravity.CENTER_VERTICAL;
            layout.addView(input, params);
        }

        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.setMargins(dp6, 0, dp6, 0);
        layout.setLayoutParams(params);

        String tooltip = name + ".tooltip";
        if (I18n.exists(tooltip)) {
            layout.setTooltipText(I18n.get(tooltip));
        }
        layout.setMinimumHeight(layout.dp(44));

        return new OptionRow(layout, input, sliderHost);
    }
}
