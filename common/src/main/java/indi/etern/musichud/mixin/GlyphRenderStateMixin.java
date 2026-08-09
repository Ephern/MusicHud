package indi.etern.musichud.mixin;

import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GlyphRenderState.class)
public abstract class GlyphRenderStateMixin {

    @ModifyArg(method = "textureSetup",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/SamplerCache;getClampToEdge(Lcom/mojang/blaze3d/textures/FilterMode;)Lcom/mojang/blaze3d/textures/GpuSampler;"),
            index = 0)
    private static FilterMode music_hud$linear(FilterMode mode) {
        return FilterMode.LINEAR;
    }
}
