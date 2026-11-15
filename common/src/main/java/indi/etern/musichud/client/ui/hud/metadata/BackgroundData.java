package indi.etern.musichud.client.ui.hud.metadata;

import indi.etern.musichud.client.utils.ui.Mixable;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public final class BackgroundData implements Mixable<BackgroundData> {
    private final BackgroundImages image;
    public static final BackgroundData NONE = new BackgroundData(null);

    public BackgroundData(BackgroundImages image) {
        this.image = image;
    }

    public BackgroundImages image() {
        return image;
    }

    @Override
    public String toString() {
        return "BackgroundData[" +
                "image=" + image + ']';
    }

    @Override
    public BackgroundData mix(BackgroundData next, float transitionProgress) {
        BackgroundImages nextImage = next != null ? next.image : image;
        return new BackgroundData(nextImage);
    }
}
