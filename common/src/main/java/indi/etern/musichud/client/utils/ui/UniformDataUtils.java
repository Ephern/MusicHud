package indi.etern.musichud.client.utils.ui;

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

    public static org.joml.Vector4f colorToVectorHSL(int color) {
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float[] hslColor = rgbToHsl(color);
        return new org.joml.Vector4f(hslColor[0], hslColor[1], hslColor[2], a);
    }

    public static float[] rgbToHsl(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        float h = 0;
        if (delta != 0) {
            if (max == r) {
                h = ((g - b) / delta) % 6;
            } else if (max == g) {
                h = (b - r) / delta + 2;
            } else {
                h = (r - g) / delta + 4;
            }
            h /= 6;
            if (h < 0) h += 1;
        }

        float l = (max + min) / 2;
        float s = (delta == 0) ? 0 : delta / (1 - Math.abs(2 * l - 1));

        return new float[]{h, s, l};
    }

    public static int hslToRgb(float[] hsl) {
        return hslToRgb(hsl[0], hsl[1], hsl[2]);
    }

    public static int hslToRgb(float h, float s, float l) {
        if (s == 0) {
            int v = Math.round(l * 255);
            return 0xFF000000 | (v << 16) | (v << 8) | v;
        }

        float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
        float p = 2 * l - q;

        int r = Math.round(hueToRgb(p, q, h + 1.0f / 3.0f) * 255);
        int g = Math.round(hueToRgb(p, q, h) * 255);
        int bVal = Math.round(hueToRgb(p, q, h - 1.0f / 3.0f) * 255);

        return 0xFF000000 | (r << 16) | (g << 8) | bVal;
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1.0f / 6.0f) return p + (q - p) * 6 * t;
        if (t < 1.0f / 2.0f) return q;
        if (t < 2.0f / 3.0f) return p + (q - p) * (2.0f / 3.0f - t) * 6;
        return p;
    }

    public static int interpolateARGB(int a, int b, float t) {
        int aA = (a >> 24) & 0xFF;
        int aR = (a >> 16) & 0xFF;
        int aG = (a >> 8) & 0xFF;
        int aB = a & 0xFF;

        int bA = (b >> 24) & 0xFF;
        int bR = (b >> 16) & 0xFF;
        int bG = (b >> 8) & 0xFF;
        int bB = b & 0xFF;

        int rA = (int) (aA + (bA - aA) * t);
        int rR = (int) (aR + (bR - aR) * t);
        int rG = (int) (aG + (bG - aG) * t);
        int rB = (int) (aB + (bB - aB) * t);

        return (rA << 24) | (rR << 16) | (rG << 8) | rB;
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
