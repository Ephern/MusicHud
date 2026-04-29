package indi.etern.musichud.client.ui.hud.pipelines;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class RenderStateUtil {
    VarHandle guiRenderStateHandle;

    public RenderStateUtil() {
        try {
            Class<?> clazz = GuiGraphics.class;
            Field found = null;
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getType() == GuiRenderState.class) {
                    if (found != null) {
                        throw new IllegalStateException("Multiple GuiRenderState fields in GuiGraphics");
                    }
                    found = f;
                }
            }
            if (found == null) {
                throw new IllegalStateException("No GuiRenderState field found in GuiGraphics");
            }
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());
            guiRenderStateHandle = lookup.unreflectVarHandle(found);
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    private GuiRenderState getGuiRenderState(GuiGraphics graphics) {
        return (GuiRenderState) guiRenderStateHandle.get(graphics);
    }

    public void submitGuiElementRenderState(
            GuiGraphics gr,
            GuiElementRenderState renderState
    ) {
        getGuiRenderState(gr).submitGuiElement(renderState);
    }
}
