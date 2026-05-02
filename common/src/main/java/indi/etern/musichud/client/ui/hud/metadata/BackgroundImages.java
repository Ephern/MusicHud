package indi.etern.musichud.client.ui.hud.metadata;

import net.minecraft.resources.Identifier;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class BackgroundImages {
    public volatile Identifier blurredLocation;
    public volatile Identifier unblurredLocation;
    public volatile float imageAspect;

    public BackgroundImages(Identifier currentBlurred, Identifier currentUnblurred, float imageAspect) {
        this.blurredLocation = currentBlurred;
        this.unblurredLocation = currentUnblurred;
        this.imageAspect = imageAspect;
    }
}
