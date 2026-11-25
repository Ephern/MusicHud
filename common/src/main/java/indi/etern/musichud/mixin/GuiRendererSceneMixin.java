package indi.etern.musichud.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexFormat;
import indi.etern.musichud.client.ui.render.RenderRuntime;
import indi.etern.musichud.client.ui.render.SceneRenderContext;
import indi.etern.musichud.client.ui.render.SceneRenderContextImpl;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Only the main GUI frame drives the singleton scene context; other {@link GuiRenderer} instances
 * render their own state into offscreen targets.
 */
@Mixin(GuiRenderer.class)
public class GuiRendererSceneMixin {
    @Shadow @Final
    GuiRenderState renderState;

    @Unique
    private boolean music_hud$ownsFrame() {
        return SceneRenderContext.getCurrent() instanceof SceneRenderContextImpl impl
                && impl.ownsState(renderState);
    }

    @Inject(method = "prepare", at = @At("HEAD"))
    private void music_hud$beginGuiFrame(CallbackInfo ci) {
        if (!music_hud$ownsFrame()) return;
        RenderRuntime.getInstance().beginFrameStamp();
    }

    /** The world is already in the main target here and the GUI ranges are not drawn yet. */
    @Inject(method = "draw", at = @At("HEAD"))
    private void music_hud$drawDeferredBackdrop(GpuBufferSlice fog, CallbackInfo ci) {
        if (!music_hud$ownsFrame()) return;
        RenderRuntime.getInstance().drawDeferredBackdrop();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void music_hud$endGuiFrame(GpuBufferSlice fog, CallbackInfo ci) {
        if (!music_hud$ownsFrame()) return;
        SceneRenderContext.getCurrent().endGuiFrame();
    }

    @Inject(method = "executeDraw", at = @At("HEAD"))
    private void music_hud$bindDrawUniforms(@Coerce Object draw, RenderPass pass, GpuBuffer indexBuffer,
                                            VertexFormat.IndexType indexType, CallbackInfo ci) {
        if (!(SceneRenderContext.getCurrent() instanceof SceneRenderContextImpl impl)) return;
        if (!impl.ownsState(renderState)) return;
        GuiRendererDrawAccessor accessor = (GuiRendererDrawAccessor) draw;
        impl.bindDrawUniforms(pass, accessor.music_hud$textureSetup(), accessor.music_hud$pipeline());
    }
}
