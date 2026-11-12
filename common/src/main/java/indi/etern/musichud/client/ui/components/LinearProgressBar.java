package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.widget.ProgressBar;
import indi.etern.musichud.client.ui.drawable.IndeterminateBarDrawable;
import indi.etern.musichud.client.ui.drawable.LinearProgressDrawable;
import indi.etern.musichud.client.ui.drawable.LinearProgressStyle;

/**
 * ProgressBar whose drawing is entirely {@link LinearProgressDrawable}. Progress is addressed as
 * a fraction and the internal range is kept at {@link Drawable#MAX_LEVEL}, so per-frame updates
 * stay sub-pixel even on wide layouts.
 */
public class LinearProgressBar extends ProgressBar {

    public static final int MAX = Drawable.MAX_LEVEL;

    private LinearProgressStyle style;
    private int progress;

    public LinearProgressBar(Context context, LinearProgressStyle style) {
        super(context, null, null, null);
        setMin(0);
        setMax(MAX);
        applyStyle(style);
    }

    public void setStyle(LinearProgressStyle style) {
        applyStyle(style);
    }

    public void setProgressFraction(float fraction) {
        progress = Math.round(Math.clamp(fraction, 0f, 1f) * MAX);
        setProgress(progress);
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {
        super.setIndeterminate(indeterminate);
        if (!indeterminate) {
            // The progress drawable keeps the level it had before the swap, which may be stale.
            restoreProgress();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSizeAndState(
                getSuggestedMinimumWidth() + getPaddingLeft() + getPaddingRight(),
                widthMeasureSpec, 0);
        int height = style == null ? 0 : dp(style.getHeightDp());
        height += getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(width, resolveSizeAndState(height, heightMeasureSpec, 0));
    }

    private void applyStyle(LinearProgressStyle progressStyle) {
        style = progressStyle;
        int[] colors = progressStyle.getFillColors();
        int heightPx = dp(progressStyle.getHeightDp());
        int thicknessPx = progressStyle.isTrackFillsHeight()
                ? 0
                : Math.max(1, dp(progressStyle.getTrackThicknessDp()));
        float radiusPx = progressStyle.getCornerRadiusDp() < 0
                ? -1
                : dp(progressStyle.getCornerRadiusDp());

        setProgressDrawable(new LinearProgressDrawable(progressStyle.getTrackColor(), colors,
                heightPx, thicknessPx, radiusPx, dp(progressStyle.getGradientLengthDp()),
                progressStyle.getAlphaFadeIn()));
        setIndeterminateDrawable(new IndeterminateBarDrawable(progressStyle.getTrackColor(),
                colors[colors.length - 1], heightPx, thicknessPx, radiusPx));
        setMaximumHeight(heightPx);
        restoreProgress();
        requestLayout();
        invalidate();
    }

    private void restoreProgress() {
        // ProgressBar replays the (unused) secondary progress on setProgressDrawable, which
        // overwrites the level; setProgress() alone no-ops when the value is unchanged.
        setProgress(progress);
        Drawable drawable = getProgressDrawable();
        if (drawable != null) {
            drawable.setLevel(progress);
        }
    }
}
