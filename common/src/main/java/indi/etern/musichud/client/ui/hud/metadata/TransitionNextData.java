package indi.etern.musichud.client.ui.hud.metadata;

import net.minecraft.resources.ResourceLocation;

public record TransitionNextData(
        ResourceLocation nextBlurred,
        ResourceLocation nextUnblurred,
        float nextAspect
) {
}
