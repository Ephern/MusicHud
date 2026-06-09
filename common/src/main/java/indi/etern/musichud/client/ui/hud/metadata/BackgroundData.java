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
        this.colors = ColorExtractor.mixBaseColorsWithAlpha(ColorExtractor.extractColors(image.current.getTexture()), 0xFF1A1A1A, 0.3f);
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

        int c1 = UniformDataUtils.interpolateARGB(colors.primary, next.primary, t);
        int c2 = UniformDataUtils.interpolateARGB(colors.secondary, next.secondary, t);
        int c3 = UniformDataUtils.interpolateARGB(colors.bright, next.bright, t);
        int c4 = UniformDataUtils.interpolateARGB(colors.dark, next.dark, t);
        return new ThemedColors(c1, c2, c3, c4);
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
        // Use float overload to avoid JOML Vector4f.get(ByteBuffer) being stripped by transformers
        org.joml.Vector4f v = UniformDataUtils.colorToVector(colors.primary);
        builder.putVec4(v.x, v.y, v.z, v.w);
        v = UniformDataUtils.colorToVector(colors.secondary);
        builder.putVec4(v.x, v.y, v.z, v.w);
        v = UniformDataUtils.colorToVector(colors.bright);
        builder.putVec4(v.x, v.y, v.z, v.w);
        v = UniformDataUtils.colorToVector(colors.dark);
        builder.putVec4(v.x, v.y, v.z, v.w);
    }

    @Override
    public boolean shouldUseBuffer(HudUniform lastBuffered) {
        return lastBuffered instanceof BackgroundData data && colors.equals(data.colors);
    }
}
