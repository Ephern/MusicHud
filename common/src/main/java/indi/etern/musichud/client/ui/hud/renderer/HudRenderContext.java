package indi.etern.musichud.client.ui.hud.renderer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import indi.etern.musichud.client.ui.hud.pipelines.UniformData;
import indi.etern.musichud.client.ui.hud.pipelines.UniformWriter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class HudRenderContext {
    @Setter
    private GuiGraphics graphics;
    private final Map<String, GpuBufferSlice> uniforms = new HashMap<>();
    private static final UniformWriter UNIFORM_WRITER = new UniformWriter();

    public HudRenderContext() {
    }

    public void clearContext() {
        graphics = null;
        uniforms.clear();
    }

    public void writeUniformData(String uniformName, UniformData uniformData) {
        GpuBufferSlice write = UNIFORM_WRITER.write(uniformData, this);
        uniforms.put(uniformName, write);
    }

    public @NonNull Matrix3x2f currentPose() {
        return new Matrix3x2f(graphics.pose());
    }

    public void submitGuiElementRenderState(GuiElementRenderState hudRenderState) {
        UNIFORM_WRITER.submitGuiElementRenderState(graphics, hudRenderState);
    }

    public void nextStratum() {
        graphics.nextStratum();
    }

    public void updateRenderPass(RenderPass renderPass) {
        for (Map.Entry<String, GpuBufferSlice> entry : uniforms.entrySet()) {
            renderPass.setUniform(entry.getKey(), entry.getValue());
        }
    }

    public Transforming transform() {
        return new Transforming(graphics);
    }

    public void blit(RenderPipeline renderPipeline, ResourceLocation resourceLocation,
                     int targetX, int targetY, int sourceX, int sourceY,
                     int targetWidth, int targetHeight, int sourceWidth, int sourceHeight,
                     int textureWidth, int textureHeight) {
        graphics.blit(
                renderPipeline,
                resourceLocation,
                targetX, targetY, sourceX, sourceY,
                targetWidth, targetHeight, sourceWidth, sourceHeight,
                textureWidth, textureHeight);
    }

    public void blit(RenderPipeline renderPipeline, ResourceLocation resourceLocation,
                     int targetX, int targetY, int sourceX, int sourceY,
                     int targetWidth, int targetHeight, int sourceWidth, int sourceHeight) {
        graphics.blit(
                renderPipeline,
                resourceLocation,
                targetX, targetY, sourceX, sourceY,
                targetWidth, targetHeight, sourceWidth, sourceHeight);
    }

    public int guiWidth() {
        return graphics.guiWidth();
    }

    public int guiHeight() {
        return graphics.guiHeight();
    }

    public void enableScissor(int x, int y, int width, int height) {
        graphics.enableScissor(x, y, width, height);
    }

    public void disableScissor() {
        graphics.disableScissor();
    }

    public void drawString(Font font, String text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color);
    }

    public void fill(int fromX, int fromY, int toX, int toY, int color) {
        graphics.fill(fromX, fromY, toX, toY, color);
    }

    public static class Transforming {
        private final Matrix3x2fStack pose;

        private Transforming(GuiGraphics guiGraphics) {
            this.pose = guiGraphics.pose();
            pose.pushMatrix();
        }

        public Transforming translate(float x, float y) {
            pose.translate(x, y);
            return this;
        }

        public Transforming rotate(float angle) {
            pose.rotate(angle);
            return this;
        }

        public Transforming scale(float scale) {
            pose.scale(scale);
            return this;
        }

        public void then(Consumer<Transforming> task) {
            task.accept(this);
            pose.popMatrix();
        }

        public Transforming restore() {
            pose.popMatrix();
            return this;
        }
    }
}
