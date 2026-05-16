package indi.etern.musichud.client.ui.hud.metadata;

import indi.etern.musichud.client.ui.utils.image.ImageTextureData;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class BackgroundImages {
    public volatile ImageTextureData blurred;
    public volatile ImageTextureData unblurred;
    public volatile float imageAspect;

    public BackgroundImages(ImageTextureData currentBlurred, ImageTextureData currentUnblurred, float imageAspect) {
        this.blurred = currentBlurred;
        this.unblurred = currentUnblurred;
        this.imageAspect = imageAspect;
    }
}
