package indi.etern.musichud.client.ui.hud.metadata;

import indi.etern.musichud.client.ui.utils.ColorExtractor;
import indi.etern.musichud.client.ui.utils.Mixable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class BackgroundData implements Mixable<BackgroundData> {
    private final BackgroundImages image;
    private final ThemedColors colors;
    public static BackgroundData NONE = new BackgroundData(null, ColorExtractor.getDefaultColors());

    public BackgroundData(
            BackgroundImages image
    ) {
        this.image = image;
        this.colors = ColorExtractor.adjustColors(ColorExtractor.extractColors(getDynamicTexture(image.blurredLocation)), 1.15f, 0.45f, 0.76f);
    }

    BackgroundData(BackgroundImages image, ThemedColors colors) {
        this.image = image;
        this.colors = colors;
    }

    private DynamicTexture getDynamicTexture(ResourceLocation imageLocation) {
        if (imageLocation == null) return null;
        AbstractTexture texture = Minecraft.getInstance()
                .getTextureManager()
                .getTexture(imageLocation);
        if (texture instanceof DynamicTexture dynamicTexture) {
            return dynamicTexture;
        }
        return null;
    }

    public ThemedColors color() {
        return colors;
    }

    public BackgroundImages image() {
        return image;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (BackgroundData) obj;
        return Objects.equals(this.colors, that.colors) &&
                Objects.equals(this.image, that.image);
    }

    @Override
    public int hashCode() {
        return Objects.hash(colors, image);
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
}
