
package indi.etern.musichud.mixin;

import indi.etern.musichud.client.ui.hud.HudRendererManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public class LevelLoadingScreenMixin {
    @Inject(method = "render",
            at = @At("RETURN"))
    private void render(GuiGraphics guiGraphics, int i, int j, float f, CallbackInfo ci) {
        guiGraphics.nextStratum();
        HudRendererManager.getInstance().renderFrame(guiGraphics, null);
    }
}
