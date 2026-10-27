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

import icyllis.modernui.core.Context;
import icyllis.modernui.view.MeasureSpec;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;

final class SliderHost extends FrameLayout {
    private static final int MIN_SLIDER_WIDTH_DP = 40;
    private static final int MAX_SLIDER_WIDTH_DP = 210;

    SliderHost(Context context) {
        super(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int maxWidth = dp(MAX_SLIDER_WIDTH_DP);
        int contentWidth = Math.max(0, widthSize - getPaddingLeft() - getPaddingRight());
        int childWidth = Math.min(contentWidth, maxWidth);

        int childHeight = 0;
        View child = getChildCount() > 0 ? getChildAt(0) : null;
        if (child != null && child.getVisibility() != GONE) {
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            int childHeightSpec = getChildMeasureSpec(heightMeasureSpec,
                    getPaddingTop() + getPaddingBottom(),
                    lp != null ? lp.height : ViewGroup.LayoutParams.WRAP_CONTENT);
            child.measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY), childHeightSpec);
            childHeight = child.getMeasuredHeight();
        }

        int measuredWidth = widthMode == MeasureSpec.UNSPECIFIED
                ? Math.min(widthSize, maxWidth)
                : widthSize;
        int measuredHeight = Math.max(childHeight + getPaddingTop() + getPaddingBottom(),
                getSuggestedMinimumHeight());
        setMeasuredDimension(measuredWidth, resolveSize(measuredHeight, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (getChildCount() == 0) {
            return;
        }
        View child = getChildAt(0);
        if (child.getVisibility() == GONE) {
            return;
        }
        int childWidth = child.getMeasuredWidth();
        int childHeight = child.getMeasuredHeight();
        //noinspection UnstableApiUsage
        int childLeft = isLayoutRtl()
                ? getPaddingLeft()
                : getWidth() - getPaddingRight() - childWidth;
        int childTop = getPaddingTop()
                + (getHeight() - getPaddingTop() - getPaddingBottom() - childHeight) / 2;
        child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
    }

    @Override
    protected void onSizeChanged(int width, int height, int prevWidth, int prevHeight) {
        super.onSizeChanged(width, height, prevWidth, prevHeight);
        updateSliderVisibility();
    }

    @Override
    protected void onViewAdded(View child) {
        super.onViewAdded(child);
        updateSliderVisibility();
    }

    private void updateSliderVisibility() {
        if (getChildCount() == 0) {
            return;
        }
        int width = getWidth();
        if (width > 0) {
            getChildAt(0).setVisibility(width < dp(MIN_SLIDER_WIDTH_DP) ? View.GONE : View.VISIBLE);
        }
    }
}
