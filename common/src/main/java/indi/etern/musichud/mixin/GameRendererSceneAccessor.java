package indi.etern.musichud.mixin;

import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererSceneAccessor {
    @Accessor("guiRenderState")
    GuiRenderState music_hud$guiRenderState();
}
