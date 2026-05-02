package indi.etern.musichud.client.ui.hud.metadata;

import lombok.EqualsAndHashCode;
import net.minecraft.resources.ResourceLocation;

@EqualsAndHashCode
public class BackgroundImages {
    public volatile ResourceLocation blurredLocation;
    public volatile ResourceLocation unblurredLocation;
    public volatile float imageAspect;

    public BackgroundImages(ResourceLocation currentBlurred, ResourceLocation currentUnblurred, float imageAspect) {
        this.blurredLocation = currentBlurred;
        this.unblurredLocation = currentUnblurred;
        this.imageAspect = imageAspect;
    }
}
