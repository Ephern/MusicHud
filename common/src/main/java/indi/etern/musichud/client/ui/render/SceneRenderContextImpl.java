package indi.etern.musichud.client.ui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import indi.etern.musichud.MusicHud;
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

public class SceneRenderContextImpl implements SceneRenderContext {
    @Getter
    private static SceneRenderContext current;

    @Getter
    @Setter
    private SceneGraphics graphics;
    private long frameTimeMillis;
    private long frameTimeNanos;

    public SceneRenderContextImpl() {
        current = this;
    }

    @Override
    public void submit(RenderLayer owner, SceneElementRenderState state) {
        //noinspection resource
        RenderRuntime.getInstance().pipelineRenderer().submit(owner, state);
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
        if (fade == null) {
            fade = TextEdgeFade.NONE;
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
            GradientTextRenderer.draw(font, text, x, y, gradient, alpha, fade, pose,
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
