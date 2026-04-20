package indi.etern.musichud.client.ui.hud.piplines;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import indi.etern.musichud.client.ui.hud.renderer.HudRenderContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

public class UniformWriter {
    protected long initTimestamp;
    VarHandle guiRenderStateHandle;

    public UniformWriter() {
        initTimestamp = System.currentTimeMillis();
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

    /**
     * 将渲染数据写入 uniform buffer
     * @return GpuBufferSlice 用于后续绑定
     */
    public GpuBufferSlice write(UniformData data, HudRenderContext context) {
        Matrix3x2f localMatrix = new Matrix3x2f();
        Layout.AbsolutePosition absolutePosition = data.getLayout().calcAbsoluteCenterPosition(context);
        localMatrix.translate(absolutePosition.x(), absolutePosition.y());
        return RenderSystem.getDynamicUniforms().writeTransform(
                new Matrix4f().mul(localMatrix),
                data.vector4f(),
                data.vector3f(),
                data.matrix4f(),
                0
        );
    }

    public void submitGuiElementRenderState(
            GuiGraphics gr,
            GuiElementRenderState renderState
    ) {
        getGuiRenderState(gr).submitGuiElement(renderState);
    }
}
