package indi.etern.musichud.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import indi.etern.musichud.client.ui.hud.renderer.HudRenderContext;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.function.Supplier;

@Mixin(GuiRenderer.class)
public class GuiRendererHudMixin {
    @Inject(method = "executeDrawRange",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;bindDefaultUniforms" +
                    "(Lcom/mojang/blaze3d/systems/RenderPass;)V", shift = At.Shift.AFTER, remap = false),
            locals = LocalCapture.CAPTURE_FAILSOFT)
    private void onExecuteDrawRange(Supplier<String> label, RenderTarget mainRenderTarget,
                                    GpuBufferSlice dynamicTransforms, int startIndex, int endIndex,
                                    CallbackInfo ci, RenderPass renderPass) {
        HudRenderContext ctx = HudRenderContext.getCurrent();
        if (ctx != null) {
            ctx.bindAllUniforms(renderPass);
        }
    }
}
