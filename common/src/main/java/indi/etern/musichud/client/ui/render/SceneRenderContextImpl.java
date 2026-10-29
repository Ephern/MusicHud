package indi.etern.musichud.client.ui.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.gpu.legacy.LegacyTextureResolver;
import indi.etern.musichud.client.ui.render.pipeline.HudShaderManager;
import indi.etern.musichud.client.ui.render.pipeline.HudShaderProgram;
import indi.etern.musichud.client.ui.render.pipeline.ScenePipeline;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureSetup;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.text.GradientSpec;
import indi.etern.musichud.client.ui.render.text.GradientTextRenderer;
import indi.etern.musichud.client.ui.render.text.TextEdgeFade;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

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
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;

public class SceneRenderContextImpl implements SceneRenderContext {
    @Getter
    private static SceneRenderContext current;

    @Getter
    @Setter
    private SceneGraphics graphics;
    private final Map<String, HudShaderProgram.UniformBufferHandle> uboHandles = new HashMap<>();
    private final Map<String, ByteBuffer> uboBuffers = new HashMap<>();
    private long frameTimeMillis;
    private long frameTimeNanos;

    public SceneRenderContextImpl() {
        current = this;
    }

    @Override
    public void submit(RenderLayer owner, SceneElementRenderState state) {
        ScenePipeline pipeline = state.pipeline();
        if (!(pipeline instanceof HudShaderProgram program) || program.getProgramId() <= 0) {
            renderFallback(state);
            return;
        }

        int[] savedProgram = new int[1];
        glGetIntegerv(GL_CURRENT_PROGRAM, savedProgram);
        int[] savedActiveTexture = new int[1];
        glGetIntegerv(GL_ACTIVE_TEXTURE, savedActiveTexture);
        // Reading a texture binding requires selecting the unit, so go through
        // GlStateManager to keep its active-unit cache in sync with the real state.
        int[] savedTexture0 = new int[1];
        int[] savedTexture1 = new int[1];
        GlStateManager._activeTexture(GL_TEXTURE0);
        glGetIntegerv(GL_TEXTURE_BINDING_2D, savedTexture0);
        GlStateManager._activeTexture(GL_TEXTURE0 + 1);
        glGetIntegerv(GL_TEXTURE_BINDING_2D, savedTexture1);
        GlStateManager._activeTexture(savedActiveTexture[0]);
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
            GlStateManager._disableDepthTest();
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA,
                    GL_ONE, GL_ONE_MINUS_SRC_ALPHA);

            setBuiltinUniforms(program);

            SceneUniform[] uniforms = state.uniforms();
            if (uniforms != null) {
                for (SceneUniform uniform : uniforms) {
                    uploadUniform(program, uniform);
                }
            }

            SceneTextureSetup textures = state.textureSetup();
            int unit = 0;
            if (textures.primary() != null) {
                bindTexture(program, textures.primary(), unit++);
            }
            if (textures.secondary() != null) {
                bindTexture(program, textures.secondary(), unit);
            }

            drawQuad(state.pose(), state.width(), state.height());
        } finally {
            glUseProgram(savedProgram[0]);
            GlStateManager._activeTexture(GL_TEXTURE0);
            GlStateManager._bindTexture(savedTexture0[0]);
            GlStateManager._activeTexture(GL_TEXTURE0 + 1);
            GlStateManager._bindTexture(savedTexture1[0]);
            GlStateManager._activeTexture(savedActiveTexture[0]);
            if (blendEnabled) {
                GlStateManager._enableBlend();
                GlStateManager._blendFuncSeparate(
                        savedBlendSrcRgb[0], savedBlendDstRgb[0],
                        savedBlendSrcAlpha[0], savedBlendDstAlpha[0]);
            } else {
                GlStateManager._disableBlend();
            }
            if (depthEnabled) {
                GlStateManager._enableDepthTest();
            } else {
                GlStateManager._disableDepthTest();
            }
        }
    }

    private static void bindTexture(HudShaderProgram program,
                                    indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef ref,
                                    int unit) {
        GlStateManager._activeTexture(GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(LegacyTextureResolver.id(ref));
        int samplerLoc = program.getUniformOrSamplerLocation("Sampler" + unit);
        if (samplerLoc >= 0) {
            glUniform1i(samplerLoc, unit);
        }
    }

    private void renderFallback(SceneElementRenderState state) {
        float left = graphics.guiWidth() / 2f + state.pose().m20;
        float top = graphics.guiHeight() / 2f + state.pose().m21;
        int x0 = (int) (left - state.width() / 2f);
        int y0 = (int) (top - state.height() / 2f);
        int x1 = (int) (left + state.width() / 2f);
        int y1 = (int) (top + state.height() / 2f);
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

    private void uploadUniform(HudShaderProgram program, SceneUniform uniform) {
        String uboName = uniform.getUBOName();
        Integer bindingPoint = HudShaderManager.getBindingPoint(uboName);
        if (bindingPoint == null) return;

        int uboSize = uniform.getUBOSize();
        String cacheKey = program.getProgramId() + "/" + uboName;
        ByteBuffer buffer = uboBuffers.computeIfAbsent(cacheKey,
                k -> ByteBuffer.allocateDirect(uboSize).order(ByteOrder.nativeOrder()));
        buffer.clear();
        uniform.write(buffer);
        buffer.flip();

        HudShaderProgram.UniformBufferHandle handle = uboHandles.computeIfAbsent(cacheKey,
                k -> HudShaderProgram.UniformBufferHandle.createAndUpload(bindingPoint, buffer));
        handle.upload(buffer);
    }

    private static final Matrix4f IDENTITY = new Matrix4f();

    private void drawQuad(Matrix3x2f pose, float width, float height) {
        float left = -width / 2f;
        float right = width / 2f;
        float top = -height / 2f;
        float bottom = height / 2f;

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
                DefaultVertexFormat.POSITION_COLOR);

        float x1 = pose.m00 * left + pose.m10 * bottom;
        float y1 = pose.m01 * left + pose.m11 * bottom;
        float x2 = pose.m00 * right + pose.m10 * bottom;
        float y2 = pose.m01 * right + pose.m11 * bottom;
        float x3 = pose.m00 * right + pose.m10 * top;
        float y3 = pose.m01 * right + pose.m11 * top;
        float x4 = pose.m00 * left + pose.m10 * top;
        float y4 = pose.m01 * left + pose.m11 * top;

        builder.addVertex(IDENTITY, x1, y1, 0).setColor(-1);
        builder.addVertex(IDENTITY, x2, y2, 0).setColor(-1);
        builder.addVertex(IDENTITY, x3, y3, 0).setColor(-1);
        builder.addVertex(IDENTITY, x1, y1, 0).setColor(-1);
        builder.addVertex(IDENTITY, x3, y3, 0).setColor(-1);
        builder.addVertex(IDENTITY, x4, y4, 0).setColor(-1);

        BufferUploader.draw(builder.buildOrThrow());
    }

    @Override
    public void beginFrame(SceneGraphics graphics) {
        this.graphics = graphics;
        frameTimeMillis = System.currentTimeMillis();
        frameTimeNanos = System.nanoTime();
    }

    @Override
    public void endFrame() {
    }

    @Override
    public void endGuiFrame() {
        RenderRuntime.getInstance().processPendingGpuRecreate();
    }

    @Override
    public long frameTimeMillis() {
        return frameTimeMillis;
    }

    @Override
    public long frameTimeNanos() {
        return frameTimeNanos;
    }

    @Override
    public SceneGraphics graphics() {
        return graphics;
    }

    @Override
    public @NonNull Matrix3x2f currentPose() {
        PoseStack.Pose last = graphics.pose().last();
        Matrix4f pose = last.pose();
        return new Matrix3x2f(pose.m00(), pose.m01(), pose.m10(), pose.m11(), 0, 0);
    }

    @Override
    public Transforming transform() {
        return Transforming.on(graphics.pose());
    }

    @Override
    public void nextStratum() {
    }

    @Override
    public int guiWidth() {
        return graphics.guiWidth();
    }

    @Override
    public int guiHeight() {
        return graphics.guiHeight();
    }

    @Override
    public void pushScissor(int fromX, int fromY, int toX, int toY) {
        graphics.pushScissor(fromX, fromY, toX, toY);
    }

    @Override
    public void popScissor() {
        graphics.popScissor();
    }

    @Override
    public void drawString(Font font, String text, int x, int y, int color, boolean dropShadow) {
        int alpha = (color >>> 24) & 0xFF;
        if (alpha == 0) return;
        if (!dropShadow) {
            drawGradientString(font, text, x, y, GradientSpec.flat(color), 1f);
            return;
        }
        int opaque = 0xFF000000 | (color & 0x00FFFFFF);
        float[] prevColor = RenderSystem.getShaderColor().clone();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha / 255f);
        graphics.drawString(font, text, x, y, opaque, dropShadow);
        RenderSystem.setShaderColor(prevColor[0], prevColor[1], prevColor[2], prevColor[3]);
    }

    @Override
    public void drawGradientString(Font font, String text, int x, int y,
                                   GradientSpec gradient, float alpha, TextEdgeFade fade) {
        if (font == null || text == null || text.isEmpty() || gradient == null || alpha <= 0f) {
            return;
        }
        if (!(graphics instanceof VanillaSceneGraphics vanillaGraphics)) {
            graphics.drawString(font, text, x, y, gradient.sample(0.5f, alpha), false);
            return;
        }
        GuiGraphics vanilla = vanillaGraphics.vanilla();
        MultiBufferSource.BufferSource bufferSource = vanilla.bufferSource();
        Matrix4f pose = vanilla.pose().last().pose();

        float[] prevColor = RenderSystem.getShaderColor().clone();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        try {
            GradientTextRenderer.draw(font, text, x, y, gradient, alpha, pose,
                    bufferSource, LightTexture.FULL_BRIGHT);
        } catch (Throwable t) {
            MusicHud.getLogger(SceneRenderContextImpl.class).debug("Gradient text rendering failed", t);
        } finally {
            RenderSystem.setShaderColor(prevColor[0], prevColor[1], prevColor[2], prevColor[3]);
        }
    }

    @Override
    public void fill(int fromX, int fromY, int toX, int toY, int color) {
        graphics.fill(fromX, fromY, toX, toY, color);
    }
}
