package indi.etern.musichud.client.ui.hud.renderer.text;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Renders a string with a per-vertex horizontal gradient on 1.21.1.
 * <p>
 * Primary path: {@link LegacyTextGeometry} emits the glyph quads itself into the shared
 * buffer source, snapping positions in layout space (the animated text origin is excluded)
 * and sampling the font atlas through a linear GL sampler object while its batches are
 * flushed. That is what removes the marquee jitter and keeps sub-pixel motion smooth.
 * <p>
 * Fallback path (used when the Modern UI text engine is disabled or the layout cannot be
 * prepared): the engine that draws the text anyway (Modern UI's {@code Font} overwrite, or
 * the vanilla {@code Font}) is asked to draw it, and a wrapping {@link MultiBufferSource}
 * returns a wrapping {@link VertexConsumer} that replaces every glyph vertex color with the
 * gradient sampled at that vertex's own local X. Glyph shapes, atlas, pipeline/render type
 * selection and metrics stay byte-for-byte identical to normal text drawing; only the colors
 * change.
 * <p>
 * In both paths the caller must keep {@code ColorModulator} neutral (white) for the duration
 * of {@link #draw}: text alpha is carried by the vertex color, and the shared
 * {@code BufferSource} would otherwise multiply the gradient by whatever {@code ColorModulator}
 * a later {@code GuiGraphics} draw flushes it under.
 */
public final class GradientTextRenderer {

    private GradientTextRenderer() {
    }

    /**
     * @param x,y        text origin in the current (already posed) coordinate space
     * @param gradient   gradient normalized over the whole drawn string ({@code [0, 1]})
     * @param alpha      global opacity multiplier folded into the sampled colors
     * @param pose       the current GUI pose matrix (as passed to {@link Font#drawInBatch})
     * @param packedLight packed lightmap coordinate (usually {@code LightTexture.FULL_BRIGHT})
     */
    public static void draw(Font font, String text, float x, float y, GradientSpec gradient, float alpha,
                            Matrix4f pose, MultiBufferSource.BufferSource bufferSource, int packedLight) {
        if (font == null || text == null || text.isEmpty() || gradient == null || alpha <= 0f) {
            return;
        }
        if (LegacyTextGeometry.draw(text, x, y, gradient, alpha, pose, bufferSource, packedLight)) {
            return;
        }
        drawDelegated(font, text, x, y, gradient, alpha, pose, bufferSource, packedLight);
    }

    private static void drawDelegated(Font font, String text, float x, float y, GradientSpec gradient, float alpha,
                                      Matrix4f pose, MultiBufferSource.BufferSource bufferSource, int packedLight) {
        // Local glyph coordinates are in unscaled font units, so normalizing by the unscaled
        // advance makes the gradient independent of any pose scale applied by the caller.
        float totalAdvance = Math.max(1f, font.width(text));
        GradientMultiBufferSource source =
                new GradientMultiBufferSource(bufferSource, gradient, alpha, totalAdvance);
        font.drawInBatch(text, x, y, 0xFFFFFFFF, false, pose, source, Font.DisplayMode.NORMAL,
                /*colorBackground*/ 0, packedLight);
        // Flush the gradient now, while the caller keeps ColorModulator neutral. If left buffered, a
        // later GuiGraphics draw would flush these vertices under its own ColorModulator, folding a
        // second alpha (e.g. a following flat lyric line's) onto the gradient. Only the text render
        // types we touched are flushed, so unrelated pending batches stay untouched.
        for (RenderType renderType : source.renderTypes) {
            bufferSource.endBatch(renderType);
        }
    }

    private static final class GradientMultiBufferSource implements MultiBufferSource {
        private final MultiBufferSource delegate;
        private final GradientSpec gradient;
        private final float alpha;
        private final float totalAdvance;
        private final Set<RenderType> renderTypes = new LinkedHashSet<>();
        /**
         * Run origin shared by every consumer handed out for one {@link #draw} call. The vanilla engine
         * requests a fresh buffer per glyph, so this must live here and not on the consumer: otherwise
         * each glyph would reset the origin to its own left edge and the gradient would collapse to
         * {@code t = 0} across the whole string.
         */
        boolean originResolved;
        float originX;

        GradientMultiBufferSource(MultiBufferSource delegate, GradientSpec gradient, float alpha, float totalAdvance) {
            this.delegate = delegate;
            this.gradient = gradient;
            this.alpha = alpha;
            this.totalAdvance = totalAdvance;
        }

        @Override
        public @NotNull VertexConsumer getBuffer(@NotNull RenderType renderType) {
            renderTypes.add(renderType);
            return new GradientVertexConsumer(this, delegate.getBuffer(renderType));
        }
    }

    /**
     * Forwards every vertex to the underlying engine consumer, but rewrites the color of each
     * vertex with the gradient sampled at the local X recorded by the preceding {@code addVertex}.
     * <p>
     * The sample position is relative to the first vertex seen (the leftmost glyph), not absolute:
     * Modern UI folds the pose translation into the text origin ({@code x += matrix.m30()/m00}), so
     * glyph X carries an arbitrary origin offset. Subtracting it makes {@code t} span {@code [0, 1]}
     * over the string on every engine, whether or not the translation was folded.
     */
    private static final class GradientVertexConsumer implements VertexConsumer {
        private final GradientMultiBufferSource source;
        private final VertexConsumer delegate;
        private float localX;

        GradientVertexConsumer(GradientMultiBufferSource source, VertexConsumer delegate) {
            this.source = source;
            this.delegate = delegate;
        }

        private void recordLocalX(float x) {
            if (!source.originResolved) {
                source.originX = x;
                source.originResolved = true;
            }
            localX = x;
        }

        @Override
        public @NotNull VertexConsumer addVertex(float x, float y, float z) {
            recordLocalX(x);
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public @NotNull VertexConsumer addVertex(@NotNull Matrix4f matrix, float x, float y, float z) {
            recordLocalX(x);
            delegate.addVertex(matrix, x, y, z);
            return this;
        }

        @Override
        public @NotNull VertexConsumer addVertex(PoseStack.@NotNull Pose pose, float x, float y, float z) {
            recordLocalX(x);
            delegate.addVertex(pose, x, y, z);
            return this;
        }

        @Override
        public @NotNull VertexConsumer setColor(int red, int green, int blue, int a) {
            applyGradientColor();
            return this;
        }

        @Override
        public @NotNull VertexConsumer setColor(int argb) {
            applyGradientColor();
            return this;
        }

        private void applyGradientColor() {
            int color = source.gradient.sample((localX - source.originX) / source.totalAdvance, source.alpha);
            delegate.setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF);
        }

        @Override
        public @NotNull VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public @NotNull VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public @NotNull VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public @NotNull VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
