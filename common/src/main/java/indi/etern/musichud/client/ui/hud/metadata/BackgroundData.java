package indi.etern.musichud.client.ui.hud.metadata;

import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Sizes;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;
import indi.etern.musichud.client.utils.ui.ColorExtractor;
import indi.etern.musichud.client.utils.ui.Mixable;
import indi.etern.musichud.client.utils.ui.ThemedColors;
import indi.etern.musichud.client.utils.ui.UniformDataUtils;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.EqualsAndHashCode;

import java.util.Objects;

@EqualsAndHashCode
public final class BackgroundData implements Mixable<BackgroundData>, SceneUniform {
    private static final int HUD_BASE_COLOR = 0xFF1A1A1A;
    private final BackgroundImages image;
    private final ThemedColors themedColors;
    private final int baseColor;
    private final boolean followHudConfig;
    private ThemedColors mixedColors;
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private float mixAlpha;
    public static final BackgroundData NONE = new BackgroundData(null, ColorExtractor.getDefaultColors());

    public static BackgroundData solid(int rgb) {
        int color = rgb & 0x00FFFFFF;
        ThemedColors colors = new ThemedColors(
                0x40000000 | color, 0x40000000 | color, 0x40000000 | color, 0x3F000000 | color);
        return new BackgroundData(null, colors).withThemeColors(colors);
    }

    public BackgroundData(
            BackgroundImages image
    ) {
        this(image, ColorExtractor.extractColors(image.current.getTexture()), HUD_BASE_COLOR,
                (float) clientConfig.getHudBackgroundMixAlpha(), true);
    }

    public BackgroundData(BackgroundImages image, ThemedColors themedColors) {
        this(image, themedColors, HUD_BASE_COLOR,
                (float) clientConfig.getHudBackgroundMixAlpha(), true);
    }

    private BackgroundData(BackgroundImages image, ThemedColors themedColors,
                           int baseColor, float mixAlpha, boolean followHudConfig) {
        this(image, themedColors, baseColor, mixAlpha, followHudConfig,
                ColorExtractor.mixBaseColorsWithAlpha(themedColors, baseColor, mixAlpha));
    }

    private BackgroundData(BackgroundImages image, ThemedColors themedColors,
                           int baseColor, float mixAlpha, boolean followHudConfig,
                           ThemedColors mixedColors) {
        this.image = image;
        this.themedColors = themedColors;
        this.baseColor = baseColor;
        this.mixAlpha = mixAlpha;
        this.followHudConfig = followHudConfig;
        this.mixedColors = mixedColors;
    }

    public BackgroundData withThemeColors(ThemedColors colors) {
        return new BackgroundData(image, colors, HUD_BASE_COLOR, 1f, false, colors);
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
        ThemedColors colors = next != null
                ? themedColors.mix(next.themedColors, transitionProgress)
                : themedColors;
        BackgroundImages nextImage = next != null && next.image != null ? next.image : image;
        return new BackgroundData(nextImage, colors, baseColor, mixAlpha, followHudConfig);
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
    public boolean shouldUseBuffer(SceneUniform lastBuffered) {
        if (lastBuffered instanceof BackgroundData data) {
            if (data.baseColor != baseColor || data.followHudConfig != followHudConfig) {
                return false;
            }
            if (followHudConfig) {
                float currentMixAlpha = (float) clientConfig.getHudBackgroundMixAlpha();
                if (currentMixAlpha != this.mixAlpha) {
                    this.mixAlpha = currentMixAlpha;
                    mixedColors = ColorExtractor.mixBaseColorsWithAlpha(
                            themedColors, HUD_BASE_COLOR, currentMixAlpha);
                    return false;
                }
            }
            return Objects.equals(mixedColors, data.mixedColors);
        } else {
            return false;
        }
    }
}
