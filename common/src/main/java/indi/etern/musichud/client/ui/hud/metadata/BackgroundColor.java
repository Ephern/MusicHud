package indi.etern.musichud.client.ui.hud.metadata;

public class BackgroundColor {
    public volatile int color1, color2, color3, color4;

    public BackgroundColor(int color1, int color2, int color4, int color3) {
        this.color1 = color1;
        this.color2 = color2;
        this.color3 = color3;
        this.color4 = color4;
    }

    public static BackgroundColor solid(int color) {
        return new BackgroundColor(color, color, color, color);
    }
}
