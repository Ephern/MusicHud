package indi.etern.musichud.mixin;

import icyllis.modernui.mc.UIManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiGlobalRenderMixin {

    @Shadow
    @Final
    private GuiRenderState guiRenderState;

    @Inject(method = "extractRenderState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/toasts/ToastManager;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
    private void music_hud$afterVanillaToasts(CallbackInfo ci) {
        //noinspection UnstableApiUsage
        UIManager.getInstance().renderAbove(guiRenderState);
    }
}
