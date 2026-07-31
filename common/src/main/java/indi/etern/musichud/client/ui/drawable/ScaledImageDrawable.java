package indi.etern.musichud.client.ui.drawable;

import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.resources.Resources;
import lombok.Getter;
import lombok.Setter;

public class ScaledImageDrawable extends ImageDrawable {
    @Setter
    @Getter
    private int padding;
    @Setter
    @Getter
    private int minHeight;
    @Setter
    @Getter
    private int maxHeight;

    public ScaledImageDrawable(Resources resources, Image image) {
        this(resources, image, 0,0,0);
    }

    public ScaledImageDrawable(Resources resources, Image image, int padding) {
        this(resources, image, padding, 0, 0);
    }

    public ScaledImageDrawable(Resources resources, Image image, int padding, int minHeight, int maxHeight) {
        super(resources, image);
        this.padding = padding;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
    }

    @Override
    public int getIntrinsicWidth() {
        if (maxHeight > 0 || minHeight > 0) {
            int targetH = maxHeight > 0 ? maxHeight : minHeight;
            int baseW = super.getIntrinsicWidth();
            int baseH = super.getIntrinsicHeight();
            if (baseW > 0 && baseH > 0) {
                return Math.round((float) targetH * baseW / baseH);
            }
            return targetH;
        }
        return super.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        if (maxHeight > 0 || minHeight > 0) {
            return maxHeight > 0 ? maxHeight : minHeight;
        }
        return super.getIntrinsicHeight();
    }
}
