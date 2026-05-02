package indi.etern.musichud.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexFormat;
import indi.etern.musichud.client.ui.hud.renderer.HudRenderContext;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.function.Supplier;

@Mixin(GuiRenderer.class)
public class MixinGuiRendererHud {
    @Inject(method = "executeDrawRange",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;bindDefaultUniforms" +
                    "(Lcom/mojang/blaze3d/systems/RenderPass;)V", shift = At.Shift.AFTER, remap = false),
            locals = LocalCapture.CAPTURE_FAILSOFT)
    private void onExecuteDrawRange(Supplier<String> $$0, RenderTarget $$1,
                                    GpuBufferSlice $$2, GpuBufferSlice $$3,
                                    GpuBuffer $$4, VertexFormat.IndexType $$5,
                                    int $$6, int $$7, CallbackInfo ci,
                                    RenderPass renderPass) {
        HudRenderContext ctx = HudRenderContext.getCurrent();
        if (ctx != null) {
            ctx.bindAllUniforms(renderPass);
        }
    }
}
