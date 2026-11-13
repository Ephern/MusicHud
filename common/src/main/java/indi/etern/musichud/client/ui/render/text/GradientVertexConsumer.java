package indi.etern.musichud.client.ui.render.text;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/** Replaces text vertex colors with a gradient sampled from local x. */
final class GradientVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float originX;
    private final float totalAdvance;
    private final GradientSpec gradient;
    private final float alpha;
    private final TextEdgeFade fade;

    private float localX;
    private boolean hasLocalX;

    GradientVertexConsumer(VertexConsumer delegate, float originX, float totalAdvance,
                           GradientSpec gradient, float alpha, TextEdgeFade fade) {
        this.delegate = delegate;
        this.originX = originX;
        this.totalAdvance = totalAdvance;
        this.gradient = gradient;
        this.alpha = alpha;
        this.fade = fade;
    }

    @Override
    public @NotNull VertexConsumer addVertex(@NotNull Matrix4f matrix, float x, float y, float z) {
        this.localX = x;
        this.hasLocalX = true;
        this.delegate.addVertex(matrix, x, y, z);
        return this;
    }

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

    private int sampleColor() {
        float edgeAlpha = this.alpha * this.fade.factorAt(this.localX);
        return this.gradient.sample((this.localX - this.originX) / this.totalAdvance, edgeAlpha);
    }
}