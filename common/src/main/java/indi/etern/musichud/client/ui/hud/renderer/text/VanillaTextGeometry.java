package indi.etern.musichud.client.ui.hud.renderer.text;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import indi.etern.musichud.mixin.BakedGlyphAccessor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

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

        List<BakedGlyph.GlyphInstance> instances = new ArrayList<>();
        prepared.visit(new Font.GlyphVisitor() {
            @Override
            public void acceptGlyph(BakedGlyph.GlyphInstance glyph) {
                if (glyph.glyph().textureView() != null) {
                    instances.add(glyph);
                }
            }

            @Override
            public void acceptEffect(BakedGlyph glyph, BakedGlyph.Effect effect) {
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
            for (BakedGlyph.GlyphInstance instance : instances) {
                BakedGlyph glyph = instance.glyph();
                BakedGlyphAccessor access = (BakedGlyphAccessor) glyph;
                boolean italic = instance.style().isItalic();
                boolean bold = instance.style().isBold();

                float localLeft = instance.x() - originX + access.music_hud$left();
                float localRight = instance.x() - originX + access.music_hud$right();
                float tLeft = localLeft / totalAdvance;
                float tRight = localRight / totalAdvance;
                int colorLeft = gradient.sample(tLeft, alpha);
                int colorRight = gradient.sample(tRight, alpha);

                // main pass
                render(consumer, z, pose, glyph, access, italic, instance.x(), instance.y(),
                        colorLeft, colorRight, bold);
                if (bold) {
                    // bold pass, offset by the glyph advance-independent bold offset
                    render(consumer, z, pose, glyph, access, italic,
                            instance.x() + instance.boldOffset(), instance.y(),
                            colorLeft, colorRight, true);
                }
            }
        }

        private void render(VertexConsumer consumer, float z, Matrix3x2f pose, BakedGlyph glyph,
                            BakedGlyphAccessor access, boolean italic, float x, float y,
                            int colorLeft, int colorRight, boolean bold) {
            float left = x + access.music_hud$left();
            float right = x + access.music_hud$right();
            float up = y + access.music_hud$up();
            float down = y + access.music_hud$down();
            float shearTop = italic ? (1.0F - 0.25F * access.music_hud$up()) : 0.0F;
            float shearBottom = italic ? (1.0F - 0.25F * access.music_hud$down()) : 0.0F;
            float thickness = bold ? 0.1F : 0.0F;
            float u0 = access.music_hud$u0();
            float u1 = access.music_hud$u1();
            float v0 = access.music_hud$v0();
            float v1 = access.music_hud$v1();
            consumer.addVertexWith2DPose(pose, left + shearTop - thickness, up - thickness, z)
                    .setColor(colorLeft).setUv(u0, v0).setLight(LightTexture.FULL_BRIGHT);
            consumer.addVertexWith2DPose(pose, left + shearBottom - thickness, down + thickness, z)
                    .setColor(colorLeft).setUv(u0, v1).setLight(LightTexture.FULL_BRIGHT);
            consumer.addVertexWith2DPose(pose, right + shearBottom + thickness, down + thickness, z)
                    .setColor(colorRight).setUv(u1, v1).setLight(LightTexture.FULL_BRIGHT);
            consumer.addVertexWith2DPose(pose, right + shearTop + thickness, up - thickness, z)
                    .setColor(colorRight).setUv(u1, v0).setLight(LightTexture.FULL_BRIGHT);
        }
    }
}
