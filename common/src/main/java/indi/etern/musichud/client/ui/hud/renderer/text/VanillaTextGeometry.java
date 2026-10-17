package indi.etern.musichud.client.ui.hud.renderer.text;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
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


public final class VanillaTextGeometry {

    private VanillaTextGeometry() {
    }

    public static List<GradientTextRenderState> build(Font font, String text, int x, int y,
                                                      GradientSpec gradient, float alpha,
                                                      @Nullable ScreenRectangle scissor, Matrix3x2f pose) {
        FormattedCharSequence sequence = Language.getInstance().getVisualOrder(FormattedText.of(text));
        Font.PreparedText prepared = font.prepareText(sequence, x, y, 0xFFFFFFFF, false, 0);

        List<BakedGlyph.GlyphInstance> instances = new ArrayList<>();
        prepared.visit(new Font.GlyphVisitor() {
            @Override
            public void acceptGlyph(BakedGlyph.@NotNull GlyphInstance glyph) {
                if (glyph.glyph().textureView() != null) {
                    instances.add(glyph);
                }
            }

            @Override
            public void acceptEffect(@NotNull BakedGlyph glyph, BakedGlyph.@NotNull Effect effect) {
                // underline/strikethrough are intentionally not supported
            }
        });
        if (instances.isEmpty()) {
            return List.of();
        }

        float totalAdvance = Math.max(1f, font.getSplitter().stringWidth(sequence));
        List<GradientTextRenderState> states = new ArrayList<>();
        int start = 0;
        for (int i = 1; i <= instances.size(); i++) {
            if (i == instances.size() || !sameBatch(instances.get(start), instances.get(i))) {
                List<BakedGlyph.GlyphInstance> runInstances = instances.subList(start, i);
                GradientTextRun run = new VanillaTextRun(runInstances, x, totalAdvance);
                states.add(new GradientTextRenderState(pose, run, gradient, alpha, scissor));
                start = i;
            }
        }
        return states;
    }

    private static boolean sameBatch(BakedGlyph.GlyphInstance a, BakedGlyph.GlyphInstance b) {
        return a.glyph().textureView() == b.glyph().textureView()
                && a.glyph().guiPipeline() == b.glyph().guiPipeline();
    }

    private static final class VanillaTextRun implements GradientTextRun {
        private final List<BakedGlyph.GlyphInstance> instances;
        private final float originX;
        private final float totalAdvance;
        private final GpuTextureView texture;
        private final RenderPipeline pipeline;

        VanillaTextRun(List<BakedGlyph.GlyphInstance> instances, float originX, float totalAdvance) {
            this.instances = instances;
            this.originX = originX;
            this.totalAdvance = totalAdvance;
            BakedGlyph first = instances.getFirst().glyph();
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
        public void writeVertices(VertexConsumer consumer, float z, Matrix3x2f pose, GradientSpec gradient, float alpha) {
            Matrix4f matrix = new Matrix4f().mul(pose).translate(0f, 0f, z);
            VertexConsumer wrapper = new GradientVertexConsumer(consumer, originX, totalAdvance, gradient, alpha);
            for (BakedGlyph.GlyphInstance instance : instances) {
                BakedGlyph glyph = instance.glyph();
                if (glyph.textureView() == null) continue;
                glyph.renderChar(instance, matrix, wrapper, LightTexture.FULL_BRIGHT, true);
            }
        }
    }
}
