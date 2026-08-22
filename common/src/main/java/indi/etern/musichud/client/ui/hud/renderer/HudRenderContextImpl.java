package indi.etern.musichud.client.ui.hud.renderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import indi.etern.musichud.client.ui.hud.pipelines.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.gui.Font;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glGetIntegerv;
import static org.lwjgl.opengl.GL11.glIsEnabled;
import static org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;

public class HudRenderContextImpl implements HudRenderContext {
    @Getter
    private static HudRenderContext current;

    @Getter
    @Setter
    private HudGraphics graphics;
    private final Map<String, HudShaderProgram.UniformBufferHandle> uboHandles = new HashMap<>();
    private final Map<String, ByteBuffer> uboBuffers = new HashMap<>();

    public HudRenderContextImpl() {
        current = this;
    }

    public void submitHudRenderState(HudRenderState hudRenderState) {
        HudPipeline pipeline = hudRenderState.pipeline();
        if (!(pipeline instanceof HudShaderProgram program)) {
            renderFallback(hudRenderState);
            return;
        }
        if (program.getProgramId() <= 0) {
            renderFallback(hudRenderState);
            return;
        }

        // Save MC's GL state before we take over
        int[] savedProgram = new int[1];
        glGetIntegerv(GL_CURRENT_PROGRAM, savedProgram);
        int[] savedActiveTexture = new int[1];
        glGetIntegerv(GL_ACTIVE_TEXTURE, savedActiveTexture);
        boolean depthEnabled = glIsEnabled(GL_DEPTH_TEST);
        boolean blendEnabled = glIsEnabled(GL_BLEND);
        int[] savedBlendSrcRgb = new int[1], savedBlendDstRgb = new int[1];
        int[] savedBlendSrcAlpha = new int[1], savedBlendDstAlpha = new int[1];
        if (blendEnabled) {
            glGetIntegerv(GL_BLEND_SRC_RGB, savedBlendSrcRgb);
            glGetIntegerv(GL_BLEND_DST_RGB, savedBlendDstRgb);
            glGetIntegerv(GL_BLEND_SRC_ALPHA, savedBlendSrcAlpha);
            glGetIntegerv(GL_BLEND_DST_ALPHA, savedBlendDstAlpha);
        }

        try {
            glUseProgram(program.getProgramId());
            if (depthEnabled) glDisable(GL_DEPTH_TEST);
            if (!blendEnabled) glEnable(GL_BLEND);
            GlStateManager._blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

            setBuiltinUniforms(program);

            HudUniform[] uniforms = hudRenderState.uniforms();
            if (uniforms != null) {
                for (HudUniform uniform : uniforms) {
                    uploadUniform(program, uniform);
                }
            }

            @NonNull HudTextureSetup textures = hudRenderState.textureSetup();
            int i = 0;
            if (textures.primary() instanceof GpuTextureViewRef(int id)) {
                glActiveTexture(GL_TEXTURE0 + i);
                glBindTexture(GL_TEXTURE_2D, id);
                int samplerLoc = program.getUniformOrSamplerLocation("Sampler" + i);
                if (samplerLoc >= 0) {
                    glUniform1i(samplerLoc, i);
                }
                i++;
            }
            if (textures.secondary() instanceof GpuTextureViewRef(int id)) {
                glActiveTexture(GL_TEXTURE0 + i);
                glBindTexture(GL_TEXTURE_2D, id);
                int samplerLoc = program.getUniformOrSamplerLocation("Sampler" + i);
                if (samplerLoc >= 0) {
                    glUniform1i(samplerLoc, i);
                }
            }

            drawQuad(hudRenderState.pose(), hudRenderState.width(), hudRenderState.height());
        } finally {
            glUseProgram(savedProgram[0]);
            glActiveTexture(savedActiveTexture[0]);
            if (blendEnabled) {
                GlStateManager._blendFuncSeparate(
                        savedBlendSrcRgb[0], savedBlendDstRgb[0],
                        savedBlendSrcAlpha[0], savedBlendDstAlpha[0]);
            } else {
                glDisable(GL_BLEND);
            }
            if (depthEnabled) glEnable(GL_DEPTH_TEST);
        }
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
        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f mv = new Matrix4f().translate(0, 0, -1000);
        int projLoc = program.getUniformOrSamplerLocation("ProjMat");
        if (projLoc >= 0) {
            float[] buf = new float[16];
            proj.get(buf);
            glUniformMatrix4fv(projLoc, false, buf);
        }
        int mvLoc = program.getUniformOrSamplerLocation("ModelViewMat");
        if (mvLoc >= 0) {
            float[] buf = new float[16];
            mv.get(buf);
            glUniformMatrix4fv(mvLoc, false, buf);
        }
    }

    private void uploadUniform(HudShaderProgram program, HudUniform uniform) {
        String uboName = uniform.getUBOName();
        Integer bindingPoint = HudShaderManager.getBindingPoint(uboName);
        if (bindingPoint == null) return;

        int uboSize = uniform.getUBOSize();
        String cacheKey = program.getProgramId() + "/" + uboName;

        // Reuse buffer per UBO name to avoid allocation each frame
        ByteBuffer buffer = uboBuffers.computeIfAbsent(cacheKey,
                k -> ByteBuffer.allocateDirect(uboSize).order(ByteOrder.nativeOrder()));
        buffer.clear();
        uniform.write(buffer);
        buffer.flip();

        HudShaderProgram.UniformBufferHandle handle = uboHandles.computeIfAbsent(cacheKey,
                k -> HudShaderProgram.UniformBufferHandle.createAndUpload(bindingPoint, buffer));
        // upload updates buffer data + binds — no separate bind() needed
        handle.upload(buffer);
    }

    private static final Matrix4f IDENTITY = new Matrix4f();

    private void drawQuad(Matrix3x2f pose, float width, float height) {
        float left = -width / 2f;
        float right = width / 2f;
        float top = -height / 2f;
        float bottom = height / 2f;

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        float x1 = pose.m00 * left + pose.m10 * bottom;
        float y1 = pose.m01 * left + pose.m11 * bottom;
        float x2 = pose.m00 * right + pose.m10 * bottom;
        float y2 = pose.m01 * right + pose.m11 * bottom;
        float x3 = pose.m00 * right + pose.m10 * top;
        float y3 = pose.m01 * right + pose.m11 * top;
        float x4 = pose.m00 * left + pose.m10 * top;
        float y4 = pose.m01 * left + pose.m11 * top;

        // Use identity matrix — u_Translation UBO handles element position,
        // ProjMat * ModelViewMat in the shader handles orthographic projection.
        // Do NOT apply graphics.pose() here to avoid double-transforming.
        builder.addVertex(IDENTITY, x1, y1, 0).setColor(-1);
        builder.addVertex(IDENTITY, x2, y2, 0).setColor(-1);
        builder.addVertex(IDENTITY, x3, y3, 0).setColor(-1);
        builder.addVertex(IDENTITY, x1, y1, 0).setColor(-1);
        builder.addVertex(IDENTITY, x3, y3, 0).setColor(-1);
        builder.addVertex(IDENTITY, x4, y4, 0).setColor(-1);

        // BufferUploader.draw() uploads + draws without touching the shader —
        // our glUseProgram() sticks.
        BufferUploader.draw(builder.buildOrThrow());
    }

    @Override
    public void beginFrame(HudGraphics graphics) {
        this.graphics = graphics;
    }

    @Override
    public void endFrame() {}

    @Override
    public HudGraphics graphics() {
        return graphics;
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
        return Transforming.on(graphics.pose());
    }

    @Override
    public void nextStratum() {

    }

    public int guiWidth() {
        return graphics.guiWidth();
    }

    public int guiHeight() {
        return graphics.guiHeight();
    }

    public void pushScissor(int fromX, int fromY, int toX, int toY) {
        graphics.pushScissor(fromX, fromY, toX, toY);
    }

    public void popScissor() {
        graphics.popScissor();
    }

    public void drawString(Font font, String text, int x, int y, int color, boolean dropShadow) {
        graphics.drawString(font, text, x, y, color, dropShadow);
    }

    public void fill(int fromX, int fromY, int toX, int toY, int color) {
        graphics.fill(fromX, fromY, toX, toY, color);
    }
}
