package indi.etern.musichud.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import indi.etern.musichud.client.ui.hud.renderer.HudRenderContext;
import indi.etern.musichud.client.ui.hud.renderer.HudRenderContextImpl;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * Hooks {@link GuiRenderer} to support multiple HUD elements sharing one pipeline while each
 * carries its own uniform content.
 * <ul>
 *   <li>Merge breaking is driven by the per-element discriminator texture view that
 *       {@code HudRenderContextImpl.toVanillaTextureSetup} places into {@code texure2} of
 *       each element's {@link TextureSetup}: elements of different profiles are
 *       record-unequal, so {@code GuiRenderer} never merges different uniform content into a
 *       single draw.</li>
 *   <li>Per-draw uniform upload: the set of {@link TextureSetup}s recorded by
 *       {@code recordMesh} is exactly aligned with the draw list, so each draw uploads only
 *       the uniforms of the element it belongs to (the GUI-pass equivalent of
 *       {@code RenderPass.drawMultipleIndexed}'s per-draw {@code uniformUploaderConsumer}).</li>
 *   <li>A default binding for every uploaded uniform name keeps {@code GuiRenderer}'s
 *       dev-mode validation satisfied.</li>
 * </ul>
 */
@Mixin(GuiRenderer.class)
public class GuiRendererHudMixin {
    @Unique
    private @Nullable RenderPass music_hud$currentRenderPass;

    @Unique
    private final Deque<TextureSetup> music_hud$elementSetups = new ArrayDeque<>();

    /**
     * {@code recordMesh} is called once per mesh, in the exact order the corresponding draws
     * will later be executed, so the recorded setups form an index-aligned mirror of the
     * draw list.
     */
    @Inject(method = "recordMesh", at = @At("HEAD"))
    private void music_hud$recordElementSetup(BufferBuilder bufferBuilder, RenderPipeline pipeline,
                                              TextureSetup textureSetup, ScreenRectangle scissor, CallbackInfo ci) {
        music_hud$elementSetups.addLast(textureSetup);
    }

    @Inject(method = "prepare", at = @At("HEAD"))
    private void music_hud$resetFrameState(CallbackInfo ci) {
        music_hud$elementSetups.clear();
        music_hud$currentRenderPass = null;
    }

    /**
     * Binds a default value for every uniform uploaded this frame, right after the vanilla
     * default uniforms. Per-draw bindings injected in {@link #music_hud$bindElementUniforms}
     * override the values that belong to the element being drawn.
     */
    @Inject(method = "executeDrawRange",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;bindDefaultUniforms" +
                    "(Lcom/mojang/blaze3d/systems/RenderPass;)V", shift = At.Shift.AFTER, remap = false),
            locals = LocalCapture.CAPTURE_FAILSOFT)
    private void music_hud$bindAllUniforms(Supplier<String> label, RenderTarget mainRenderTarget,
                                           GpuBufferSlice dynamicTransforms, int startIndex, int endIndex,
                                           CallbackInfo ci, RenderPass renderPass) {
        music_hud$currentRenderPass = renderPass;
        if (HudRenderContext.getCurrent() instanceof HudRenderContextImpl impl) {
            impl.bindAllUniforms(renderPass);
        }
    }

    /**
     * Per-draw uniform upload: before each draw is issued, upload only the uniforms of the
     * element that draw belongs to. This is the GUI-pass equivalent of
     * {@code drawMultipleIndexed}'s per-draw {@code uniformUploaderConsumer}.
     */
    @Inject(method = "executeDrawRange",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;executeDraw(Lnet/minecraft/client/gui/render/GuiRenderer$Draw;Lcom/mojang/blaze3d/systems/RenderPass;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;)V"))
    private void music_hud$bindElementUniforms(Supplier<String> label, RenderTarget mainRenderTarget,
                                               GpuBufferSlice fog, GpuBufferSlice dynamicTransforms,
                                               GpuBuffer indexBuffer, VertexFormat.IndexType indexType,
                                               int startIndex, int endIndex, CallbackInfo ci) {
        RenderPass pass = music_hud$currentRenderPass;
        if (pass == null) return;
        TextureSetup textureSetup = music_hud$elementSetups.poll();
        if (textureSetup == null) return;
        if (HudRenderContext.getCurrent() instanceof HudRenderContextImpl impl) {
            impl.bindElementUniforms(pass, textureSetup, pass::setUniform);
        }
    }
}
