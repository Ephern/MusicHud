package indi.etern.musichud.client.ui.hud.metadata;

import net.minecraft.resources.Identifier;

public record TransitionNextData(
        Identifier nextBlurred,
        Identifier nextUnblurred,
        float nextAspect
) {
}
