package indi.etern.musichud.mixin;

import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.interfaces.ClientConfig;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At("HEAD"), cancellable = true)
    private void onPlaySound(SoundInstance soundInstance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        ClientConfig clientConfig = ClientConfig.getInstance();
        if (clientConfig.isConfigured()
                && soundInstance.getSource() == SoundSource.MUSIC
                && clientConfig.getDisableVanillaMusic()
                && StreamAudioPlayer.getInstance().getStatus() == StreamAudioPlayer.Status.PLAYING) {
            cir.cancel();
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }
}