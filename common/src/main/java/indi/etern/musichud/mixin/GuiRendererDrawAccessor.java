package indi.etern.musichud.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.render.TextureSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.render.GuiRenderer$Draw")
public interface GuiRendererDrawAccessor {
    @Accessor("textureSetup")
    TextureSetup music_hud$textureSetup();

    @Accessor("pipeline")
    RenderPipeline music_hud$pipeline();
}
