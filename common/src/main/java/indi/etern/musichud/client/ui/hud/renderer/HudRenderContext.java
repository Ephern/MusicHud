package indi.etern.musichud.client.ui.hud.renderer;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import indi.etern.musichud.client.ui.hud.pipelines.HudRenderState;
import indi.etern.musichud.client.ui.hud.pipelines.HudUniform;
import lombok.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

import java.util.function.Consumer;

public class HudRenderContext {
    @Getter
    private static HudRenderContext current;
    @Setter
    private GuiGraphics graphics;

    public HudRenderContext() {
        current = this;
    }

    public void clearContext() {
        graphics = null;
    }

    public void submitHudRenderState(HudRenderState hudRenderState) {
        // Shader rendering is delegated to a future platform-specific render path.
        // In 1.21.1, custom shader rendering requires a different approach
        // (no BufferUploader/VertexBuffer). Use the drawQuad delegate or
        // GuiGraphics fallback below.
        renderFallback(hudRenderState);
    }

    private void renderFallback(HudRenderState hudRenderState) {
        // Fallback: render a colored rectangle using GuiGraphics
        // Uniform data is still computed and can be accessed if needed
        HudUniform[] uniforms = hudRenderState.uniforms();
        // Uniforms are available for inspection but not uploaded to GPU in fallback mode

        float left = graphics.guiWidth() / 2f + hudRenderState.pose().m20;
        float top = graphics.guiHeight() / 2f + hudRenderState.pose().m21;
        int x0 = (int)(left - hudRenderState.width() / 2f);
        int y0 = (int)(top - hudRenderState.height() / 2f);
        int x1 = (int)(left + hudRenderState.width() / 2f);
        int y1 = (int)(top + hudRenderState.height() / 2f);

        graphics.fill(x0, y0, x1, y1, 0x33FFFFFF);
    }

    public @NonNull Matrix3x2f currentPose() {
        PoseStack.Pose last = graphics.pose().last();
        Matrix4f pose = last.pose();
        return new Matrix3x2f(
                pose.m00(), pose.m01(),
                pose.m10(), pose.m11(),
                pose.m30(), pose.m31()
        );
    }

    public Transforming transform() {
        return new Transforming(graphics);
    }

    public void nextStratum() {
        // no-op in 1.21.1
    }

    public void blit(ResourceLocation resourceLocation,
                     int targetX, int targetY, int sourceX, int sourceY,
                     int targetWidth, int targetHeight, int sourceWidth, int sourceHeight,
                     int textureWidth, int textureHeight) {
        graphics.blit(resourceLocation,
                targetX, targetY, sourceX, sourceY,
                targetWidth, targetHeight, sourceWidth, sourceHeight,
                textureWidth, textureHeight);
    }

    public void blit(ResourceLocation resourceLocation,
                     int targetX, int targetY, int sourceX, int sourceY,
                     int targetWidth, int targetHeight, int sourceWidth, int sourceHeight) {
        graphics.blit(resourceLocation,
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

    public void bindAllUniforms() {
        // no-op in 1.21.1
    }

    public void prepareUniforms() {
        // no-op in 1.21.1
    }

    public static class Transforming {
        private final PoseStack pose;

        private Transforming(GuiGraphics guiGraphics) {
            this.pose = guiGraphics.pose();
            pose.pushPose();
        }

        public Transforming translate(float x, float y) {
            pose.translate(x, y, 0);
            return this;
        }

        public Transforming rotate(float angle) {
            pose.mulPose(Axis.ZP.rotation(angle));
            return this;
        }

        public Transforming scale(float scale) {
            pose.scale(scale, scale, 1);
            return this;
        }

        public void then(Consumer<Transforming> task) {
            task.accept(this);
            pose.popPose();
        }

        public Transforming restore() {
            pose.popPose();
            return this;
        }
    }
}
