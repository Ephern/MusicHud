
package indi.etern.musichud.mixin;

import indi.etern.musichud.client.ui.hud.scene.HudScene;
import indi.etern.musichud.client.ui.render.RenderRuntime;
import indi.etern.musichud.client.ui.render.VanillaSceneGraphics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "render",
            at = @At("RETURN"))
    private void music_hud$onRender(GuiGraphics guiGraphics, int i, int j, float f, CallbackInfo ci) {
        //noinspection ConstantValue (incorrect warning)
        if (((Object) this instanceof ServerReconfigScreen) || ((Object) this instanceof ReceivingLevelScreen)) {
            guiGraphics.nextStratum();
            RenderRuntime.getInstance().renderFrame(new VanillaSceneGraphics(guiGraphics), HudScene.getInstance());
        }
    }
}
