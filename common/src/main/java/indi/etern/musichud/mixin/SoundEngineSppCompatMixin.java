package indi.etern.musichud.mixin;

import indi.etern.musichud.client.audio.OpenAlSource;
import net.minecraft.client.sounds.SoundEngine;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Soft compatibility with Sound Physics Perfected (SPP). SPP's
 * {@code SoundEngine.updateActiveSources()} brute-forces reverb (EFX auxiliary
 * send) onto every live OpenAL source each tick, which would otherwise wet the
 * raw sources created by this mod. Redirect its {@code alIsSource} guard so
 * it simply skips our owned sources.
 * <p>
 * Fully optional: the redirected method only exists when SPP is installed, so
 * on any other setup this injector no-ops.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineSppCompatMixin {

    @SuppressWarnings({"UnresolvedMixinReference", "MixinAnnotationTarget"})
    @Redirect(
            method = "updateActiveSources",
            remap = false,
            require = 0,
            expect = 0,
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL10;alIsSource(I)Z", remap = false))
    private static boolean music_hud$skipOwnedSources(int sourceId) {
        return !OpenAlSource.isMusicHudSource(sourceId) && AL10.alIsSource(sourceId);
    }
}