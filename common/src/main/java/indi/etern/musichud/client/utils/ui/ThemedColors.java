package indi.etern.musichud.client.utils.ui;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class ThemedColors implements Mixable<ThemedColors> {
    public volatile int primary, secondary, bright, dark;

    public ThemedColors(int primary, int secondary, int bright, int dark) {
        this.primary = primary;
        this.secondary = secondary;
        this.bright = bright;
        this.dark = dark;
    }

    @Override
    public ThemedColors mix(ThemedColors next, float t) {
        if (next == null) {
            return new ThemedColors(primary, secondary, bright, dark);
        }
        if (t <= 0.01f) {
            return new ThemedColors(primary, secondary, bright, dark);
        }
        if (t >= 0.99f) {
            return new ThemedColors(next.primary, next.secondary, next.bright, next.dark);
        }

        int c1 = mixRgbLerpAlpha(primary, next.primary, t);
        int c2 = mixRgbLerpAlpha(secondary, next.secondary, t);
        int c3 = mixRgbLerpAlpha(bright, next.bright, t);
        int c4 = mixRgbLerpAlpha(dark, next.dark, t);

        float a1 = ((c1 >>> 24) & 0xFF) / 255.0f;
        float a2 = ((c2 >>> 24) & 0xFF) / 255.0f;
        float a3 = ((c3 >>> 24) & 0xFF) / 255.0f;
        float a4 = ((c4 >>> 24) & 0xFF) / 255.0f;
        float sum = a1 + a2 + a3 + a4;
        if (sum <= 1e-6f) {
            return new ThemedColors(c1, c2, c3, c4);
        }
        int[] alphas = ColorExtractor.sharesToAlphaBytes(a1 / sum, a2 / sum, a3 / sum, a4 / sum);
        c1 = (alphas[0] << 24) | (c1 & 0x00FFFFFF);
        c2 = (alphas[1] << 24) | (c2 & 0x00FFFFFF);
        c3 = (alphas[2] << 24) | (c3 & 0x00FFFFFF);
        c4 = (alphas[3] << 24) | (c4 & 0x00FFFFFF);
        return new ThemedColors(c1, c2, c3, c4);
    }

    private static int mixRgbLerpAlpha(int a, int b, float t) {
        float t1 = Easing.EASE_IN_OUT_QUAD.getInterpolation(t);
        int opaqueA = 0xFF000000 | (a & 0x00FFFFFF);
        int opaqueB = 0xFF000000 | (b & 0x00FFFFFF);
        int mixedRgb = UniformDataUtils.interpolateARGB(opaqueA, opaqueB, t1);
        int aA = (a >>> 24) & 0xFF;
        int aB = (b >>> 24) & 0xFF;
        int aMixed = Math.round(aA + (aB - aA) * t1);
        aMixed = Math.clamp(aMixed, 1, 255);
        return (aMixed << 24) | (mixedRgb & 0x00FFFFFF);
    }
}
