package indi.etern.musichud.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.audio.Library;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.ALC10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.nio.IntBuffer;

@Mixin(Library.class)
public class Blaze3DAudioLibraryInitMixin {

    @Unique
    private static boolean music_hud$hrtfSupported;
    @Unique
    private static boolean music_hud$hrtfEnabled;

    @Shadow
    private long currentDevice;

    @WrapOperation(method = "init", at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/audio/Library;setHrtf(Z)V"
    ))
    private void music_hud$captureHrtfState(Library self, boolean enable, Operation<Void> original) {
        // Capture combined = ALC_SOFT_HRTF && directionalAudio and detect device HRTF support.
        // The original setHrtf call is skipped on purpose; HRTF is applied at context creation
        // instead, which is the only place it actually takes effect.
        music_hud$hrtfEnabled = enable;
        music_hud$hrtfSupported = ALC10.alcGetInteger(this.currentDevice, 6548) > 0;
    }

    @ModifyArg(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/openal/ALC10;alcCreateContext(JLjava/nio/IntBuffer;)J",
                    remap = false
            ),
            index = 1
    )
    private static IntBuffer music_hud$injectHrtfAttributes(IntBuffer oldAttrs) {
        if (!music_hud$hrtfSupported) {
            return oldAttrs;
        }
        // Always pass ALC_HRTF_SOFT explicitly (1 = on, 0 = off), then the vanilla attributes.
        // Heap buffers are rejected by LWJGL here, so a direct buffer is required.
        IntBuffer attrs = BufferUtils.createIntBuffer(7);
        attrs.put(6546).put(music_hud$hrtfEnabled ? 1 : 0); // ALC_HRTF_SOFT
        attrs.put(6550).put(0);                             // ALC_HRTF_ID_SOFT
        attrs.put(6554).put(1).put(0);                      // vanilla attribute
        attrs.flip();
        return attrs;
    }
}
