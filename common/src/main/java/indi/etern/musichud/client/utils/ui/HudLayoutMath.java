package indi.etern.musichud.client.utils.ui;

import icyllis.modernui.graphics.RectF;
import indi.etern.musichud.client.ui.hud.metadata.HorizontalAlign;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import indi.etern.musichud.client.ui.hud.metadata.VerticalAlign;

public final class HudLayoutMath {
    private HudLayoutMath() {
    }

    public static RectF computeGuiRect(int guiWidth, int guiHeight,
                                       HorizontalAlign horizontalAlign, VerticalAlign verticalAlign,
                                       int offsetX, int offsetY, int width, int height) {
        Layout root = new Layout(offsetX, offsetY, width, height, 0, horizontalAlign, verticalAlign);
        float x = horizontalAlign.calcX(offsetX, guiWidth, root);
        float y = verticalAlign.calcY(offsetY, guiHeight, root);
        return new RectF(x, y, x + width, y + height);
    }

    public static Values reverseGuiRect(int guiWidth, int guiHeight,
                                        HorizontalAlign horizontalAlign, VerticalAlign verticalAlign,
                                        float left, float top, float right, float bottom) {
        int width = Math.round(right - left);
        int height = Math.round(bottom - top);
        int offsetX = switch (horizontalAlign) {
            case LEFT -> Math.round(left);
            case CENTER -> Math.round(left - guiWidth / 2f + width / 2f);
            case RIGHT -> Math.round(guiWidth - right);
        };
        int offsetY = switch (verticalAlign) {
            case TOP -> Math.round(top);
            case CENTER -> Math.round(top - guiHeight / 2f + height / 2f);
            case BOTTOM -> Math.round(guiHeight - bottom);
        };
        return new Values(offsetX, offsetY, width, height);
    }

    public record Values(int offsetX, int offsetY, int width, int height) {
    }
}
