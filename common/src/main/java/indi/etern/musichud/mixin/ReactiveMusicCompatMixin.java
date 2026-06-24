package indi.etern.musichud.mixin;

import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.interfaces.ClientConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "circuitlord.reactivemusic.ReactiveMusic", remap = false)
public abstract class ReactiveMusicCompatMixin {
    @Unique
    private static StreamAudioPlayer music_hud$streamAudioPlayer;
    @Unique
    private static ClientConfig music_hud$clientConfig;
    @Unique
    private static boolean music_hud$reactive_music_killed = false;

    @SuppressWarnings("UnresolvedMixinReference")
    @Inject(method = "newTick", at = @At("HEAD"), cancellable = true)
    private static void onNewTick(CallbackInfo ci) {
        if (music_hud$streamAudioPlayer == null) {
            music_hud$streamAudioPlayer = StreamAudioPlayer.getInstance();
        }
        if (music_hud$clientConfig == null) {
            music_hud$clientConfig = ClientConfig.getInstance();
        }
        boolean shouldMute = music_hud$streamAudioPlayer.getStatus() == StreamAudioPlayer.Status.PLAYING
                && music_hud$clientConfig.getDisableVanillaMusic();
        if (!shouldMute) {
            music_hud$reactive_music_killed = false;
            return;
        }

        if (!music_hud$reactive_music_killed) {
            music_hud$reactive_music_killed = true;
            try {
                Object thread = Class.forName("circuitlord.reactivemusic.ReactiveMusic")
                        .getField("thread").get(null);
                thread.getClass().getMethod("resetPlayer").invoke(thread);
            } catch (Exception ignored) {}
        }
        ci.cancel();
    }
}
