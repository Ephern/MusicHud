package indi.etern.musichud.mixin;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import icyllis.modernui.mc.UIManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(UIManager.class)
public abstract class UIManagerRenderMixin {

    @Redirect(method = "render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuSampler;IIIIFFFF)V"))
    private static void music_hud$onBlit(GuiGraphicsExtractor gr, GpuTextureView textureView, GpuSampler sampler,
                                         int x, int y, int w, int h, float u0, float v0, float u1, float v1) {
        GuiGraphicsExtractorAccessor accessor = (GuiGraphicsExtractorAccessor) gr;
        accessor.music_hud$getGuiRenderState().addGuiElement(new BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(textureView, sampler),
                new Matrix3x2f(gr.pose()),
                x, y, w, h,
                u0, v0, u1, v1,
                ~0,
                null));
    }
}
