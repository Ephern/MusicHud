package indi.etern.musichud.client.ui.hud.metadata;

public class ThemedColors {
    public volatile int primary, secondary, bright, dark;

    public ThemedColors(int primary, int secondary, int dark, int bright) {
        this.primary = primary;
        this.secondary = secondary;
        this.bright = bright;
        this.dark = dark;
    }

    public static ThemedColors solid(int color) {
        return new ThemedColors(color, color, color, color);
    }
}
