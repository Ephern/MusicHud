package indi.etern.musichud.client.ui.hud.metadata;

import indi.etern.musichud.client.ui.hud.pipelines.HudUniform;
import indi.etern.musichud.client.ui.hud.pipelines.Std140BufferWriter;
import indi.etern.musichud.client.ui.utils.ColorExtractor;
import indi.etern.musichud.client.ui.utils.Mixable;
import indi.etern.musichud.client.ui.utils.UniformDataUtils;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public final class BackgroundData implements Mixable<BackgroundData>, HudUniform {
    private final BackgroundImages image;
    private final ThemedColors colors;
    public static final BackgroundData NONE = new BackgroundData(null, ColorExtractor.getDefaultColors());

    public BackgroundData(
            BackgroundImages image
    ) {
        this.image = image;
        this.colors = ColorExtractor.mixBaseColorsWithAlpha(ColorExtractor.extractColors(image.current.getTexture()), 0xFF1A1A1A, 0.25f);
    }

    BackgroundData(BackgroundImages image, ThemedColors colors) {
        this.image = image;
        this.colors = colors;
    }

    public ThemedColors color() {
        return colors;
    }

    public BackgroundImages image() {
        return image;
    }

    @Override
    public String toString() {
        return "BackgroundData[" +
                "color=" + colors + ", " +
                "image=" + image + ']';
    }

    @Override
    public BackgroundData mix(BackgroundData next, float transitionProgress) {
        return new BackgroundData(image, next != null ? mixColor(next.colors, transitionProgress) : colors);
    }

    private ThemedColors mixColor(ThemedColors next, float t) {
        if (t <= 0.01f) return new ThemedColors(colors.primary, colors.secondary, colors.bright, colors.dark);
        if (t >= 0.99f) return new ThemedColors(next.primary, next.secondary, next.bright, next.dark);

        int c1 = interpolateARGB(colors.primary, next.primary, t);
        int c2 = interpolateARGB(colors.secondary, next.secondary, t);
        int c3 = interpolateARGB(colors.bright, next.bright, t);
        int c4 = interpolateARGB(colors.dark, next.dark, t);
        return new ThemedColors(c1, c2, c3, c4);
    }

    private int interpolateARGB(int a, int b, float t) {
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

    public static final int UBO_SIZE = new Std140BufferWriter.Calculator().putVec4().putVec4().putVec4().putVec4().align(16).get();

    @Override
    public String getUBOName() {
        return "MHNowPlayingThemeColor";
    }

    @Override
    public int getUBOSize() {
        return UBO_SIZE;
    }

    @Override
    public void write(Std140BufferWriter builder) {
        builder.putVec4(UniformDataUtils.colorToVector(colors.primary));
        builder.putVec4(UniformDataUtils.colorToVector(colors.secondary));
        builder.putVec4(UniformDataUtils.colorToVector(colors.bright));
        builder.putVec4(UniformDataUtils.colorToVector(colors.dark));
    }

    @Override
    public boolean shouldUseBuffer(HudUniform lastBuffered) {
        return lastBuffered instanceof BackgroundData data && colors.equals(data.colors);
    }
}
