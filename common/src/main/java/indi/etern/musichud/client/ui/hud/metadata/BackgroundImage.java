package indi.etern.musichud.client.ui.hud.metadata;

import net.minecraft.resources.ResourceLocation;

public class BackgroundImage {
    public volatile ResourceLocation currentBlurredLocation;
    public volatile ResourceLocation currentUnblurredLocation;

    public volatile float currentAspect;

    public BackgroundImage(ResourceLocation currentBlurred, ResourceLocation currentUnblurred, float currentAspect) {
        this.currentBlurredLocation = currentBlurred;
        this.currentUnblurredLocation = currentUnblurred;
        this.currentAspect = currentAspect;
    }
}
