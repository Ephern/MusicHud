package indi.etern.musichud.mixin;

import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.interfaces.ClientConfig;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    @Unique
    private static final ClientConfig music_hud$clientConfig = ClientConfig.getInstance();

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
            at = @At("HEAD"), cancellable = true)
    private void onPlaySound(SoundInstance soundInstance, CallbackInfo ci) {
        if (music_hud$clientConfig.isConfigured()
                && soundInstance.getSource() == SoundSource.MUSIC
                && music_hud$clientConfig.getDisableVanillaMusic()
                && StreamAudioPlayer.getInstance().getStatus() == StreamAudioPlayer.Status.PLAYING) {
            ci.cancel();
        }
    }
}