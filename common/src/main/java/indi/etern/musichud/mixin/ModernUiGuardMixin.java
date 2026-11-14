package indi.etern.musichud.mixin;

import indi.etern.musichud.client.ui.ModernUiSupport;
import indi.etern.musichud.client.ui.screen.ModernUiRequiredScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shows the ModernUI requirement screen once, before the title screen is presented, when the client
 * UI cannot be loaded. References no ModernUI class so it also applies on an incompatible install.
 */
@Mixin(TitleScreen.class)
public class ModernUiGuardMixin {
    @Unique
    private static boolean music_hud$warned;

    @Inject(method = "init", at = @At("RETURN"))
    private void music_hud$warnIfUnsupported(CallbackInfo ci) {
        if (ModernUiSupport.available() || music_hud$warned) {
            return;
        }
        music_hud$warned = true;
        TitleScreen self = (TitleScreen) (Object) this;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen == self) {
                minecraft.setScreen(new ModernUiRequiredScreen(new TitleScreen()));
            }
        });
    }
}
