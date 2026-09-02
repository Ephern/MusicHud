package indi.etern.musichud.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import indi.etern.musichud.client.audio.OpenAlSource;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

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