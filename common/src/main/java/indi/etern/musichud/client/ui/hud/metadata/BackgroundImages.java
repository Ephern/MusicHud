package indi.etern.musichud.client.ui.hud.metadata;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.utils.image.ImageTextureData;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class BackgroundImages {
    public volatile ImageTextureData current;
    public volatile float imageAspect;
    /** Borrowed full-resolution decode, before HUD thumbnail resampling. */
    public final ImageTextureData original;
    /** Wall-clock time when this image set became available for rendering. */
    public final long changedAtMillis;

    public BackgroundImages(ImageTextureData current, float imageAspect) {
        this(current, imageAspect, null);
    }

    public BackgroundImages(ImageTextureData current, float imageAspect, ImageTextureData original) {
        this.current = current;
        this.imageAspect = imageAspect;
        this.original = original;
        this.changedAtMillis = MusicHud.getRunningMillis();
    }
}
