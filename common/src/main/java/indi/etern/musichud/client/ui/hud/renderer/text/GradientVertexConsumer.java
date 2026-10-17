package indi.etern.musichud.client.ui.hud.renderer.text;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * A pass-through {@link VertexConsumer} that replaces the color of every vertex emitted by a
 * text renderable with a gradient color sampled at the vertex's local x position.
 * <p>
 * Vanilla glyph renderables write their quads as
 * {@code addVertex(matrix, localX, localY, localZ).setColor(argb).setUv(...).setLight(...)}.
 * The local x is captured before the matrix transform, so the gradient parameter stays
 * independent of whatever pose (translation, scale) the HUD has applied. Every other vertex
 * attribute is forwarded unchanged, which keeps glyph shapes, UVs, bold/italic passes and
 * depth offsets exactly as the text engine produced them.
 * <p>
 * All chained methods return this wrapper (not the delegate) so that a later {@code setColor}
 * in the same chain is still intercepted. If a color is set without a preceding
 * {@code addVertex(Matrix4f, ...)} the original color is passed through, so renderables using
 * a different write path are never corrupted.
 */
final class GradientVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float originX;
    private final float totalAdvance;
    private final GradientSpec gradient;
    private final float alpha;

    private float localX;
    private boolean hasLocalX;

    GradientVertexConsumer(VertexConsumer delegate, float originX, float totalAdvance,
                           GradientSpec gradient, float alpha) {
        this.delegate = delegate;
        this.originX = originX;
        this.totalAdvance = totalAdvance;
        this.gradient = gradient;
        this.alpha = alpha;
    }

    /**
     * Captures the vertex's local x and forwards the call with the same matrix, so the delegate
     * performs the transform exactly like it would without this wrapper.
     */
    @Override
    public @NotNull VertexConsumer addVertex(@NotNull Matrix4f matrix, float x, float y, float z) {
        this.localX = x;
        this.hasLocalX = true;
        this.delegate.addVertex(matrix, x, y, z);
        return this;
    }

    /**
     * A direct (already transformed) vertex add cannot be mapped back to text space, so colors
     * pass through untouched for such quads.
     */
    @Override
    public @NotNull VertexConsumer addVertex(float x, float y, float z) {
        this.hasLocalX = false;
        this.delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public @NotNull VertexConsumer setColor(int argb) {
        if (this.hasLocalX) {
            this.delegate.setColor(this.sampleColor());
        } else {
            this.delegate.setColor(argb);
        }
        return this;
    }

    @Override
    public @NotNull VertexConsumer setColor(int red, int green, int blue, int alpha) {
        if (this.hasLocalX) {
            this.delegate.setColor(this.sampleColor());
        } else {
            this.delegate.setColor(red, green, blue, alpha);
        }
        return this;
    }

    @Override
    public @NotNull VertexConsumer setUv(float u, float v) {
        this.delegate.setUv(u, v);
        return this;
    }

    @Override
    public @NotNull VertexConsumer setUv1(int u, int v) {
        this.delegate.setUv1(u, v);
        return this;
    }

    @Override
    public @NotNull VertexConsumer setUv2(int u, int v) {
        this.delegate.setUv2(u, v);
        return this;
    }

    @Override
    public @NotNull VertexConsumer setNormal(float x, float y, float z) {
        this.delegate.setNormal(x, y, z);
        return this;
    }

    /**
     * {@link GradientSpec#sample} clamps both t and the global alpha, so glyph overhang outside
     * the text width and faded-out states resolve to the nearest endpoint color.
     */
    private int sampleColor() {
        return this.gradient.sample((this.localX - this.originX) / this.totalAdvance, this.alpha);
    }
}