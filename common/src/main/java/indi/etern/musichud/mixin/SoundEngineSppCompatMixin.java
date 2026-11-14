package indi.etern.musichud.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import indi.etern.musichud.client.audio.OpenAlSource;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps Sound Physics Perfected from applying reverb to this mod's OpenAL sources: SPP brute-forces
 * an EFX auxiliary send onto every live source each tick, so its {@code alIsSource} guard is
 * redirected to skip ours. No-ops when SPP is absent.
 */
@Mixin(value = SoundEngine.class, priority = 9100)
public abstract class SoundEngineSppCompatMixin {

    @SuppressWarnings({"UnresolvedMixinReference", "MixinAnnotationTarget"})
    @WrapOperation(
            method = "updateActiveSources",
            remap = false,
            require = 0,
            expect = 0,
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL10;alIsSource(I)Z", remap = false))
    private static boolean music_hud$skipOwnedSources(int sourceId, Operation<Boolean> original) {
        boolean originalResult = original.call(sourceId);
        if (OpenAlSource.isMusicHudSource(sourceId)) {
            return false;
        }
        return originalResult;
    }
}