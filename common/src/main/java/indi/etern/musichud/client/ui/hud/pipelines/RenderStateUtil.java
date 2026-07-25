package indi.etern.musichud.client.ui.hud.pipelines;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class RenderStateUtil {
    VarHandle guiRenderStateHandle;

    public RenderStateUtil() {
        try {
            Class<?> clazz = GuiGraphicsExtractor.class;
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

    private GuiRenderState getGuiRenderState(GuiGraphicsExtractor graphics) {
        return (GuiRenderState) guiRenderStateHandle.get(graphics);
    }

    public void submitGuiElementRenderState(
            GuiGraphicsExtractor gr,
            GuiElementRenderState renderState
    ) {
        getGuiRenderState(gr).addGuiElement(renderState);
    }
}
