package indi.etern.musichud.client.ui.hud.metadata;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class ThemedColors {
    public volatile int primary, secondary, bright, dark;

    public ThemedColors(int primary, int secondary, int bright, int dark) {
        this.primary = primary;
        this.secondary = secondary;
        this.bright = bright;
        this.dark = dark;
    }

    public static ThemedColors solid(int color) {
        return new ThemedColors(color, color, color, color);
    }
}
