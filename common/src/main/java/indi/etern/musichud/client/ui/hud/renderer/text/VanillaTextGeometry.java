package indi.etern.musichud.client.ui.hud.renderer.text;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry source for the vanilla text pipeline (no Modern UI). Glyph instances are collected
 * once from {@link Font#prepareText} and the quads are re-emitted at draw time with per-vertex
 * gradient colors, mirroring {@code BakedGlyph.renderChar} (bold double pass included).
 */
public final class VanillaTextGeometry {

    private VanillaTextGeometry() {
    }

    public static List<GradientTextRenderState> build(Font font, String text, int x, int y,
                                                      GradientSpec gradient, float alpha,
                                                      @Nullable ScreenRectangle scissor, Matrix3x2f pose) {
        FormattedCharSequence sequence = Language.getInstance().getVisualOrder(FormattedText.of(text));
        Font.PreparedText prepared = font.prepareText(sequence, x, y, 0xFFFFFFFF, false, 0);

        List<TextRenderable> textRenderables = new ArrayList<>();
        prepared.visit(new Font.GlyphVisitor() {
            @Override
            public void acceptGlyph(@NotNull TextRenderable textRenderable) {
                textRenderables.add(textRenderable);
            }

            @Override
            public void acceptEffect(@NotNull TextRenderable textRenderable) {
                // underline/strikethrough are intentionally not supported
            }
        });
        if (textRenderables.isEmpty()) {
            return List.of();
        }

        float totalAdvance = Math.max(1f, font.getSplitter().stringWidth(sequence));
        List<GradientTextRenderState> states = new ArrayList<>();
        int start = 0;
        for (int i = 1; i <= textRenderables.size(); i++) {
            if (i == textRenderables.size() || !sameBatch(textRenderables.get(start), textRenderables.get(i))) {
                List<TextRenderable> runInstances = textRenderables.subList(start, i);
                GradientTextRun run = new VanillaTextRun(runInstances, x, totalAdvance);
                states.add(new GradientTextRenderState(pose, run, gradient, alpha, scissor));
                start = i;
            }
        }
        return states;
    }

    private static boolean sameBatch(TextRenderable a, TextRenderable b) {
        //noinspection resource
        return a.textureView() == b.textureView()
                && a.guiPipeline() == b.guiPipeline();
    }

    private static final class VanillaTextRun implements GradientTextRun {
        private final List<TextRenderable> textRenderables;
        private final float originX;
        private final float totalAdvance;
        private final GpuTextureView texture;
        private final RenderPipeline pipeline;

        VanillaTextRun(List<TextRenderable> textRenderables, float originX, float totalAdvance) {
            this.textRenderables = textRenderables;
            this.originX = originX;
            this.totalAdvance = totalAdvance;
            TextRenderable first = textRenderables.getFirst();
            this.texture = first.textureView();
            this.pipeline = first.guiPipeline();
        }

        @Override
        public RenderPipeline pipeline() {
            return pipeline;
        }

        @Override
        public GpuTextureView texture() {
            return texture;
        }

        @Override
        public void writeVertices(VertexConsumer consumer, Matrix3x2f pose, GradientSpec gradient, float alpha) {
            VertexConsumer gradientConsumer = new GradientVertexConsumer(consumer, originX, totalAdvance, gradient, alpha);
            Matrix4f matrix4f = new Matrix4f().mul(pose);
            for (TextRenderable renderable : textRenderables) {
                renderable.render(matrix4f, gradientConsumer, LightTexture.FULL_BRIGHT, true);
            }
        }
    }
}
