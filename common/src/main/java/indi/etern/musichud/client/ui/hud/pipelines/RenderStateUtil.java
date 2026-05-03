package indi.etern.musichud.client.ui.hud.pipelines;

import net.minecraft.client.gui.GuiGraphics;

// Stub for 1.21.1 — render state submission is handled directly in HudRenderContext
public class RenderStateUtil {

    public void submitGuiElementRenderState(
            GuiGraphics gr,
            HudRenderState renderState
    ) {
        // no-op in 1.21.1: rendering is done directly in HudRenderContext.submitHudRenderState()
    }
}
