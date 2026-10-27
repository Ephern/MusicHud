package indi.etern.musichud.client.utils.ui;

import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.view.View;

public final class PanelStyle {
    private static final int BORDER_RGB = 0x7F7F7F;
    private static final int STROKE_WIDTH_DP = 1;

    private PanelStyle() {
    }

    public static int fillColor(double opacity, double brightness) {
        int gray = (int) Math.round(Math.clamp(brightness, 0.0, 1.0) * 127.0);
        int alpha = (int) Math.round(Math.clamp(opacity, 0.0, 1.0) * 255.0);
        return (alpha << 24) | (gray << 16) | (gray << 8) | gray;
    }

    public static int borderColor(double opacity) {
        int alpha = (int) Math.round(Math.clamp(opacity, 0.0, 1.0) * 0.5 * 255.0);
        return (alpha << 24) | BORDER_RGB;
    }

    public static InsetBackgroundFactory factory(View view, double opacity, double brightness,
                                                 int cornerRadiusPx,
                                                 InsetBackgroundFactory.Padding padding) {
        return InsetBackgroundFactory.builder()
                .backgroundColor(ColorStateList.valueOf(fillColor(opacity, brightness)))
                .strokeWidth(view.dp(STROKE_WIDTH_DP))
                .strokeColor(borderColor(opacity))
                .cornerRadius(cornerRadiusPx)
                .padding(padding)
                .build();
    }
}
