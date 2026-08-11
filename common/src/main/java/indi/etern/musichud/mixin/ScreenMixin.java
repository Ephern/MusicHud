
package indi.etern.musichud.mixin;

import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.ui.hud.renderer.VanillaHudGraphics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "extractBackground",
            at = @At("RETURN"))
    private void music_hud$onRender(GuiGraphicsExtractor guiGraphics, int i, int j, float f, CallbackInfo ci) {
        //noinspection ConstantValue (incorrect warning)
        if (((Object) this instanceof ServerReconfigScreen) || ((Object) this instanceof LevelLoadingScreen)) {
            guiGraphics.nextStratum();
            HudRendererManager.getInstance().renderFrame(new VanillaHudGraphics(guiGraphics), null);
        }
    }
}
