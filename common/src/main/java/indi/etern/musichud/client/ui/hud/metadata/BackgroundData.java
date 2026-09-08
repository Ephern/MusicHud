package indi.etern.musichud.client.ui.hud.metadata;

import indi.etern.musichud.client.ui.hud.pipelines.HudUniform;
import indi.etern.musichud.client.ui.hud.pipelines.Std140Sizes;
import indi.etern.musichud.client.ui.hud.pipelines.Std140Writer;
import indi.etern.musichud.client.utils.ui.ColorExtractor;
import indi.etern.musichud.client.utils.ui.Easing;
import indi.etern.musichud.client.utils.ui.Mixable;
import indi.etern.musichud.client.utils.ui.UniformDataUtils;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.EqualsAndHashCode;

import java.util.Objects;

@EqualsAndHashCode
public final class BackgroundData implements Mixable<BackgroundData>, HudUniform {
    private final BackgroundImages image;
    private final ThemedColors themedColors;
    private ThemedColors mixedColors;
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private float mixAlpha;
    public static final BackgroundData NONE = new BackgroundData(null, ColorExtractor.getDefaultColors());

    public BackgroundData(
            BackgroundImages image
    ) {
        this.image = image;
        this.mixAlpha = (float) clientConfig.getHudBackgroundMixAlpha();
        this.themedColors = ColorExtractor.extractColors(image.current.getTexture());
        this.mixedColors = ColorExtractor.mixBaseColorsWithAlpha(themedColors, 0xFF1A1A1A, mixAlpha);
    }

    public BackgroundData(BackgroundImages image, ThemedColors themedColors) {
        this.image = image;
        this.mixAlpha = (float) clientConfig.getHudBackgroundMixAlpha();
        this.themedColors = themedColors;
        this.mixedColors = ColorExtractor.mixBaseColorsWithAlpha(themedColors, 0xFF1A1A1A, mixAlpha);
    }

    public ThemedColors color() {
        return themedColors;
    }

    public BackgroundImages image() {
        return image;
    }

    @Override
    public String toString() {
        return "BackgroundData[" +
                "themedColors=" + themedColors + ", " +
                "image=" + image + ']';
    }

    @Override
    public BackgroundData mix(BackgroundData next, float transitionProgress) {
        return new BackgroundData(image, next != null ? mixColor(next.themedColors, transitionProgress) : themedColors);
    }

    private ThemedColors mixColor(ThemedColors next, float t) {
        if (t <= 0.01f)
            return new ThemedColors(themedColors.primary, themedColors.secondary, themedColors.bright, themedColors.dark);
        if (t >= 0.99f) return new ThemedColors(next.primary, next.secondary, next.bright, next.dark);

        // RGB transition; alpha channels are shares, lerped then renormalized to sum 1.0.
        int c1 = mixRgbLerpAlpha(themedColors.primary, next.primary, t);
        int c2 = mixRgbLerpAlpha(themedColors.secondary, next.secondary, t);
        int c3 = mixRgbLerpAlpha(themedColors.bright, next.bright, t);
        int c4 = mixRgbLerpAlpha(themedColors.dark, next.dark, t);
        // Renormalize the four lerped alphas so they sum to 1.0.
        float a1 = ((c1 >>> 24) & 0xFF) / 255.0f;
        float a2 = ((c2 >>> 24) & 0xFF) / 255.0f;
        float a3 = ((c3 >>> 24) & 0xFF) / 255.0f;
        float a4 = ((c4 >>> 24) & 0xFF) / 255.0f;
        float sum = a1 + a2 + a3 + a4;
        if (sum <= 1e-6f) return new ThemedColors(c1, c2, c3, c4);
        int[] alphas = ColorExtractor.sharesToAlphaBytes(a1 / sum, a2 / sum, a3 / sum, a4 / sum);
        c1 = (alphas[0] << 24) | (c1 & 0x00FFFFFF);
        c2 = (alphas[1] << 24) | (c2 & 0x00FFFFFF);
        c3 = (alphas[2] << 24) | (c3 & 0x00FFFFFF);
        c4 = (alphas[3] << 24) | (c4 & 0x00FFFFFF);
        return new ThemedColors(c1, c2, c3, c4);
    }

    // Interpolate RGB opaquely, lerp alpha separately without clamping to opaque.
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

    public static final int UBO_SIZE = Std140Sizes.calc().putVec4().putVec4().putVec4().putVec4().align(16).get();

    @Override
    public String getUBOName() {
        return "MHNowPlayingThemeColor";
    }

    @Override
    public int getUBOSize() {
        return UBO_SIZE;
    }

    @Override
    public void write(Std140Writer builder) {
        builder.putVec4(UniformDataUtils.colorToVector(mixedColors.primary));
        builder.putVec4(UniformDataUtils.colorToVector(mixedColors.secondary));
        builder.putVec4(UniformDataUtils.colorToVector(mixedColors.bright));
        builder.putVec4(UniformDataUtils.colorToVector(mixedColors.dark));
    }

    @Override
    public boolean shouldUseBuffer(HudUniform lastBuffered) {
        if (lastBuffered instanceof BackgroundData data) {
            float mixAlpha = (float) clientConfig.getHudBackgroundMixAlpha();
            if (mixAlpha != this.mixAlpha) {
                this.mixAlpha = mixAlpha;
                mixedColors = ColorExtractor.mixBaseColorsWithAlpha(themedColors, 0xFF1A1A1A, mixAlpha);
                return false;
            } else {
                return Objects.equals(mixedColors, data.mixedColors);
            }
        } else {
            return false;
        }
    }
}
