package indi.etern.musichud.client.ui.hud.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import indi.etern.musichud.client.ui.hud.pipelines.HudRenderState;
import indi.etern.musichud.client.ui.hud.pipelines.HudShaderProgram;
import indi.etern.musichud.client.ui.hud.pipelines.HudUniform;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL20.*;

public class HudRenderContext {
    @Getter
    private static HudRenderContext current;
    @Setter
    private GuiGraphics graphics;
    private final Map<String, HudShaderProgram.UniformBufferHandle> uboHandles = new HashMap<>();

    public HudRenderContext() {
        current = this;
    }

    public void clearContext() {
        graphics = null;
    }

    public void submitHudRenderState(HudRenderState hudRenderState) {
        HudShaderProgram program = hudRenderState.pipeline();
        if (program == null || program.getProgramId() <= 0) {
            renderFallback(hudRenderState);
            return;
        }

        glUseProgram(program.getProgramId());

        setBuiltinUniforms(program);

        HudUniform[] uniforms = hudRenderState.uniforms();
        if (uniforms != null) {
            for (HudUniform uniform : uniforms) {
                uploadUniform(program, uniform);
            }
        }

        Integer[] textures = hudRenderState.textures();
        if (textures != null) {
            for (int i = 0; i < textures.length; i++) {
                if (textures[i] != null) {
                    RenderSystem.setShaderTexture(i, textures[i]);
                }
            }
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        drawQuad(hudRenderState.pose(), hudRenderState.width(), hudRenderState.height());
    }

    private void renderFallback(HudRenderState hudRenderState) {
        float left = graphics.guiWidth() / 2f + hudRenderState.pose().m20;
        float top = graphics.guiHeight() / 2f + hudRenderState.pose().m21;
        int x0 = (int)(left - hudRenderState.width() / 2f);
        int y0 = (int)(top - hudRenderState.height() / 2f);
        int x1 = (int)(left + hudRenderState.width() / 2f);
        int y1 = (int)(top + hudRenderState.height() / 2f);

        graphics.fill(x0, y0, x1, y1, 0x33FFFFFF);
    }

    private void setBuiltinUniforms(HudShaderProgram program) {
        int projLoc = program.getUniformLocation("ProjMat");
        if (projLoc >= 0) {
            float[] projBuf = new float[16];
            RenderSystem.getProjectionMatrix().get(projBuf);
            glUniformMatrix4fv(projLoc, false, projBuf);
        }
        int mvLoc = program.getUniformLocation("ModelViewMat");
        if (mvLoc >= 0) {
            float[] mvBuf = new float[16];
            RenderSystem.getModelViewMatrix().get(mvBuf);
            glUniformMatrix4fv(mvLoc, false, mvBuf);
        }
    }

    private void uploadUniform(HudShaderProgram program, HudUniform uniform) {
        String uboName = uniform.getUBOName();
        Integer bindingPoint = program.getUniformBlockBindingPoint(uboName);
        if (bindingPoint == null) return;

        String cacheKey = program.getProgramId() + "/" + uboName;

        ByteBuffer buffer = ByteBuffer.allocateDirect(uniform.getUBOSize()).order(ByteOrder.nativeOrder());
        uniform.write(buffer);
        buffer.flip();

        HudShaderProgram.UniformBufferHandle handle = uboHandles.computeIfAbsent(cacheKey,
                k -> HudShaderProgram.UniformBufferHandle.createAndUpload(bindingPoint, buffer));
        handle.upload(buffer);
        handle.bind();
    }

    private void drawQuad(Matrix3x2f pose, float width, float height) {
        float left = -width / 2f;
        float right = width / 2f;
        float top = -height / 2f;
        float bottom = height / 2f;

        Matrix4f m = graphics.pose().last().pose();

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        float x1 = pose.m00 * left + pose.m10 * bottom;
        float y1 = pose.m01 * left + pose.m11 * bottom;
        float x2 = pose.m00 * right + pose.m10 * bottom;
        float y2 = pose.m01 * right + pose.m11 * bottom;
        float x3 = pose.m00 * right + pose.m10 * top;
        float y3 = pose.m01 * right + pose.m11 * top;
        float x4 = pose.m00 * left + pose.m10 * top;
        float y4 = pose.m01 * left + pose.m11 * top;

        builder.addVertex(m, x1, y1, 0).setColor(-1);
        builder.addVertex(m, x2, y2, 0).setColor(-1);
        builder.addVertex(m, x3, y3, 0).setColor(-1);
        builder.addVertex(m, x1, y1, 0).setColor(-1);
        builder.addVertex(m, x3, y3, 0).setColor(-1);
        builder.addVertex(m, x4, y4, 0).setColor(-1);

        // BufferUploader.draw() uploads + draws without touching the shader —
        // our glUseProgram() sticks.
        BufferUploader.draw(builder.buildOrThrow());
    }

    /**
     * Returns the current pose matrix without translation — element position is
     * handled by the u_Translation uniform in the vertex shader (set via Layout UBO).
     */
    public @NonNull Matrix3x2f currentPose() {
        PoseStack.Pose last = graphics.pose().last();
        Matrix4f pose = last.pose();
        return new Matrix3x2f(
                pose.m00(), pose.m01(),
                pose.m10(), pose.m11(),
                0, 0
        );
    }

    public Transforming transform() {
        return new Transforming(graphics);
    }

    public void nextStratum() {
        // no-op in 1.21.1
    }

    // -- blit signatures matching 1.21.1 GuiGraphics (no RenderPipeline param) --

    public void blit(ResourceLocation resourceLocation,
                     int x, int y, int width, int height,
                     int texWidth, int texHeight) {
        graphics.blit(resourceLocation, x, y, width, height, 0, 0, width, height, texWidth, texHeight);
    }

    public void blit(ResourceLocation resourceLocation,
                     int x, int y, int z,
                     float u0, float v0, int width, int height,
                     int texWidth, int texHeight) {
        graphics.blit(resourceLocation, x, y, z, u0, v0, width, height, texWidth, texHeight);
    }

    public void blit(ResourceLocation resourceLocation,
                     int x, int y, int width, int height,
                     float u0, float v0, int uWidth, int vHeight,
                     int texWidth, int texHeight) {
        graphics.blit(resourceLocation, x, y, width, height, u0, v0, uWidth, vHeight, texWidth, texHeight);
    }

    /** Legacy 12-int blit (texel coords) — converts to 1.21.1 UV-based blit. */
    public void blit(ResourceLocation resourceLocation,
                     int targetX, int targetY,
                     int sourceX, int sourceY,
                     int targetWidth, int targetHeight,
                     int sourceWidth, int sourceHeight,
                     int textureWidth, int textureHeight) {
        float u0 = (float)sourceX / textureWidth;
        float v0 = (float)sourceY / textureHeight;
        graphics.blit(resourceLocation, targetX, targetY, targetWidth, targetHeight,
                u0, v0, sourceWidth, sourceHeight, textureWidth, textureHeight);
    }

    /** Legacy 8-int blit (target=source size) — converts to UV-based. */
    public void blit(ResourceLocation resourceLocation,
                     int targetX, int targetY,
                     int sourceX, int sourceY,
                     int targetWidth, int targetHeight,
                     int sourceWidth, int sourceHeight) {
        float u0 = (float)sourceX / sourceWidth;
        float v0 = (float)sourceY / sourceHeight;
        graphics.blit(resourceLocation, targetX, targetY, targetWidth, targetHeight,
                u0, v0, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
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
