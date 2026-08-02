package indi.etern.musichud.client.ui.drawable;

import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.resources.Resources;
import lombok.Getter;
import lombok.Setter;

public class ScaledImageDrawable extends ImageDrawable {
    @Setter
    @Getter
    private int minHeight;
    @Setter
    @Getter
    private int maxHeight;

    public ScaledImageDrawable(Resources resources, Image image) {
        this(resources, image, 0,0);
    }

    public ScaledImageDrawable(Resources resources, Image image, int minHeight, int maxHeight) {
        super(resources, image);
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
    }

    private int computeTargetHeight(int baseHeight) {
        if (maxHeight <= 0 && minHeight <= 0) {
            return baseHeight;
        }
        int lower = Math.max(minHeight, 0);
        int upper = maxHeight > 0 ? maxHeight : Integer.MAX_VALUE;
        return Math.max(Math.clamp(baseHeight, lower, upper), 0);
    }

    @Override
    public int getIntrinsicWidth() {
        int baseW = super.getIntrinsicWidth();
        int baseH = super.getIntrinsicHeight();
        int targetH = computeTargetHeight(baseH);
        if (baseW > 0 && baseH > 0) {
            return Math.round((float) targetH * baseW / baseH);
        }
        return targetH;
    }

    @Override
    public int getIntrinsicHeight() {
        return computeTargetHeight(super.getIntrinsicHeight());
    }
}
