package indi.etern.musichud.client.utils.ui;

import indi.etern.musichud.client.utils.image.ImageTextureData;
import net.minecraft.client.renderer.texture.DynamicTexture;

public final class ThemeColorSource {
    private final Transitionable<ThemedColors> transition;

    public ThemeColorSource(ThemedColors initial, int durationMs) {
        this.transition = new Transitionable<>(
                initial != null ? initial : ColorExtractor.getDefaultColors(), durationMs);
    }

    public ThemeColorSource(int durationMs) {
        this(null, durationMs);
    }

    public ThemeColorSource() {
        this(500);
    }

    public void switchTo(ThemedColors colors) {
        if (colors != null) {
            transition.startTransition(colors);
        }
    }

    public void switchTo(DynamicTexture texture) {
        if (texture != null) {
            switchTo(ColorExtractor.extractColors(texture));
        }
    }

    public void switchTo(ImageTextureData image) {
        if (image != null) {
            switchTo(image.getTexture());
        }
    }

    public void update() {
        transition.updateTransition();
    }

    public ThemedColors getMixed() {
        return transition.getMixed();
    }

    public ThemedColors getCurrent() {
        return transition.getCurrent();
    }

    public float getProgress() {
        return transition.getProgress();
    }

    public boolean isTransitioning() {
        return transition.isTransitioning();
    }

    public void reset() {
        switchTo(ColorExtractor.getDefaultColors());
    }
}
