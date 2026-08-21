package indi.etern.musichud.mixin;

import indi.etern.musichud.client.audio.OpenAlSource;
import indi.etern.musichud.client.audio.SoundEngineState;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.interfaces.ClientConfig;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    @Unique
    private static final ClientConfig music_hud$clientConfig = ClientConfig.getInstance();

    @Inject(method = "tick", at = @At("RETURN"))
    private void music_hud$clearExternalSfx(CallbackInfo ci) {
        if (ALC10.alcGetCurrentContext() == 0) {
            return;
        }
        for (int sourceId : OpenAlSource.ownedSourceIds()) {
            if (!AL11.alIsSource(sourceId)) {
                continue;
            }
            // Disconnect any EFX auxiliary send (reverb wet path) applied by
            // third-party audio mods running earlier in this same tick.
            AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, 0, 0, 0);
            AL11.alSourcef(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER_GAIN_AUTO, AL11.AL_FALSE);
            AL11.alSourcef(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER_GAINHF_AUTO, AL11.AL_FALSE);
            AL11.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, 0);
        }
    }

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At("HEAD"), cancellable = true)
    private void onPlaySound(SoundInstance soundInstance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (music_hud$clientConfig.isConfigured()
                && soundInstance.getSource() == SoundSource.MUSIC
                && music_hud$clientConfig.getDisableVanillaMusic()
                && StreamAudioPlayer.getInstance().getStatus() == StreamAudioPlayer.Status.PLAYING) {
            cir.cancel();
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }

    @Inject(method = "reload", at = @At("HEAD"))
    private void onReloadStart(CallbackInfo ci) {
        SoundEngineState.setCurrent(SoundEngineState.LOADING);
    }

    @Inject(method = "reload", at = @At("RETURN"))
    private void onReloadDone(CallbackInfo ci) {
        SoundEngineState.setCurrent(SoundEngineState.RUNNING);
    }

    @Inject(method = "emergencyShutdown", at = @At("HEAD"))
    private void emergencyShutdown(CallbackInfo ci) {
        SoundEngineState.setCurrent(SoundEngineState.SHUTDOWN);
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void shutdown(CallbackInfo ci) {
        if (SoundEngineState.getCurrent() == SoundEngineState.RUNNING) {
            SoundEngineState.setCurrent(SoundEngineState.SHUTDOWN);
        }
    }
}