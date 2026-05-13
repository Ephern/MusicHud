package indi.etern.musichud.client.ui.hud.renderer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import indi.etern.musichud.client.ui.hud.pipelines.HudRenderState;
import indi.etern.musichud.client.ui.hud.pipelines.HudUniform;
import indi.etern.musichud.client.ui.hud.pipelines.RenderStateUtil;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class HudRenderContext {
    private static final RenderStateUtil UNIFORM_WRITER = new RenderStateUtil();
    @Getter
    private static HudRenderContext current;

    private final Map<StorageKey, DynamicUniformStorage<?>> storageMap = new HashMap<>();
    private final Map<StorageKey, HudUniform> pendingUniforms = new HashMap<>();
    private final Map<StorageKey, GpuBufferSlice> uniformSlices = new HashMap<>();

    private final Map<StorageKey, HudUniform> lastWrittenUniforms = new HashMap<>();
    private final Map<StorageKey, GpuBufferSlice> lastSlices = new HashMap<>();

    @Setter
    private GuiGraphics graphics;

    public HudRenderContext() {
        current = this;
    }

    public void clearContext() {
        for (DynamicUniformStorage<?> storage : storageMap.values()) {
            storage.endFrame();
        }
        pendingUniforms.clear();
        uniformSlices.clear();
        graphics = null;
    }

    public void prepareUniforms() {
        for (Map.Entry<StorageKey, HudUniform> entry : pendingUniforms.entrySet()) {
            StorageKey key = entry.getKey();
            HudUniform uniform = entry.getValue();

            // skip re-upload if same uniform data was already written last frame
            HudUniform lastWritten = lastWrittenUniforms.get(key);
            if (lastWritten != null && lastWritten.shouldUseBuffer(uniform)) {
                GpuBufferSlice cachedSlice = lastSlices.get(key);
                if (cachedSlice != null) {
                    uniformSlices.put(key, cachedSlice);
                    lastWrittenUniforms.put(key, uniform);
                    continue;
                }
            }

            @SuppressWarnings({"unchecked", "resource"})
            DynamicUniformStorage<HudUniform> storage = (DynamicUniformStorage<HudUniform>)
                    storageMap.computeIfAbsent(key, k ->
                            new DynamicUniformStorage<>(uniform.getUBOName(), uniform.getUBOSize(), 256)
                    );

            GpuBufferSlice slice = storage.writeUniform(uniform);
            uniformSlices.put(key, slice);
            lastSlices.put(key, slice);
            lastWrittenUniforms.put(key, uniform);
        }
    }

    public @NonNull Matrix3x2f currentPose() {
        return new Matrix3x2f(graphics.pose());
    }

    public void submitHudRenderState(HudRenderState hudRenderState) {
        UNIFORM_WRITER.submitGuiElementRenderState(graphics, hudRenderState);

        HudUniform[] uniforms = hudRenderState.uniforms();
        if (uniforms != null) {
            for (HudUniform uniform : uniforms) {
                StorageKey key = new StorageKey(hudRenderState.pipeline(), uniform.getUBOName());
                pendingUniforms.put(key, uniform);
            }
        }
    }

    public void bindAllUniforms(RenderPass pass) {
        if (pass == null) return;
        for (Map.Entry<StorageKey, GpuBufferSlice> entry : uniformSlices.entrySet()) {
            StorageKey key = entry.getKey();
            HudUniform uniform = pendingUniforms.get(key);
            if (uniform == null) continue;
            pass.setUniform(uniform.getUBOName(), entry.getValue());
        }
    }

    public void nextStratum() {
        graphics.nextStratum();
    }

    public Transforming transform() {
        return new Transforming(graphics);
    }

    public void blit(RenderPipeline renderPipeline, ResourceLocation resourceLocation,
                     int targetX, int targetY, int sourceX, int sourceY,
                     int targetWidth, int targetHeight, int sourceWidth, int sourceHeight,
                     int textureWidth, int textureHeight) {
        graphics.blit(renderPipeline, resourceLocation,
                targetX, targetY, sourceX, sourceY,
                targetWidth, targetHeight, sourceWidth, sourceHeight,
                textureWidth, textureHeight);
    }

    public void blit(RenderPipeline renderPipeline, ResourceLocation resourceLocation,
                     int targetX, int targetY, int sourceX, int sourceY,
                     int targetWidth, int targetHeight, int sourceWidth, int sourceHeight) {
        graphics.blit(renderPipeline, resourceLocation,
                targetX, targetY, sourceX, sourceY,
                targetWidth, targetHeight, sourceWidth, sourceHeight);
    }

    public int guiWidth() {
        return graphics.guiWidth();
    }

    public int guiHeight() {
        return graphics.guiHeight();
    }

    public void pushScissor(int fromX, int fromY, int toX, int toY) {
        graphics.enableScissor(fromX, fromY, toX, toY);
    }

    public void popScissor() {
        graphics.disableScissor();
    }

    public void drawString(Font font, String text, int x, int y, int color, boolean dropShadow) {
        graphics.drawString(font, text, x, y, color, dropShadow);
    }

    public void fill(int fromX, int fromY, int toX, int toY, int color) {
        graphics.fill(fromX, fromY, toX, toY, color);
    }

    private record StorageKey(RenderPipeline pipeline, String uboName) {
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
