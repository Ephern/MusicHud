package indi.etern.musichud.client.ui.utils;

import icyllis.modernui.graphics.MathUtil;
import lombok.NonNull;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public class UniformDataUtils {
    public static org.joml.Vector4f colorToVector(int color) {
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        return new org.joml.Vector4f(r, g, b, a);
    }

    @Nullable
    public static ScreenRectangle getBounds(float left, float top, float right, float bottom,
                                            @NonNull Matrix3x2f pose) {
        float x1 = pose.m00 * left + pose.m10 * top + pose.m20;
        float y1 = pose.m01 * left + pose.m11 * top + pose.m21;
        float x2 = pose.m00 * right + pose.m10 * top + pose.m20;
        float y2 = pose.m01 * right + pose.m11 * top + pose.m21;
        float x3 = pose.m00 * left + pose.m10 * bottom + pose.m20;
        float y3 = pose.m01 * left + pose.m11 * bottom + pose.m21;
        float x4 = pose.m00 * right + pose.m10 * bottom + pose.m20;
        float y4 = pose.m01 * right + pose.m11 * bottom + pose.m21;

        int L = (int) Math.floor(MathUtil.min(x1, x2, x3, x4));
        int T = (int) Math.floor(MathUtil.min(y1, y2, y3, y4));
        int R = (int) Math.ceil(MathUtil.max(x1, x2, x3, x4));
        int B = (int) Math.ceil(MathUtil.max(y1, y2, y3, y4));

        if (L >= R || T >= B) return null;
        return new ScreenRectangle(L, T, R - L, B - T);
    }
}
