package indi.etern.musichud.client.ui.hud.metadata;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import indi.etern.musichud.client.ui.hud.pipelines.HudUniform;
import indi.etern.musichud.client.ui.utils.ColorExtractor;
import indi.etern.musichud.client.ui.utils.Mixable;
import indi.etern.musichud.client.ui.utils.UniformDataUtils;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.EqualsAndHashCode;

import java.util.Objects;

@EqualsAndHashCode
public final class BackgroundData implements Mixable<BackgroundData>, HudUniform {
    private final BackgroundImages image;
    private final ThemedColors themedColors;
    private ThemedColors mixedColors;
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private float mixAlpha = -1;
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
        if (t <= 0.01f) return new ThemedColors(themedColors.primary, themedColors.secondary, themedColors.bright, themedColors.dark);
        if (t >= 0.99f) return new ThemedColors(next.primary, next.secondary, next.bright, next.dark);

        int c1 = UniformDataUtils.interpolateARGB(themedColors.primary, next.primary, t);
        int c2 = UniformDataUtils.interpolateARGB(themedColors.secondary, next.secondary, t);
        int c3 = UniformDataUtils.interpolateARGB(themedColors.bright, next.bright, t);
        int c4 = UniformDataUtils.interpolateARGB(themedColors.dark, next.dark, t);
        return new ThemedColors(c1, c2, c3, c4);
    }

    public static final int UBO_SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().align(16).get();

    @Override
    public String getUBOName() {
        return "MHNowPlayingThemeColor";
    }

    @Override
    public int getUBOSize() {
        return UBO_SIZE;
    }

    @Override
    public void write(Std140Builder builder) {
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
