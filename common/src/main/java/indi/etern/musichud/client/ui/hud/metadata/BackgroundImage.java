package indi.etern.musichud.client.ui.hud.metadata;

import net.minecraft.resources.Identifier;

public class BackgroundImage {
    public volatile Identifier currentBlurredLocation;
    public volatile Identifier currentUnblurredLocation;

    public volatile float currentAspect;

    public BackgroundImage(Identifier currentBlurred, Identifier currentUnblurred, float currentAspect) {
        this.currentBlurredLocation = currentBlurred;
        this.currentUnblurredLocation = currentUnblurred;
        this.currentAspect = currentAspect;
    }
}
