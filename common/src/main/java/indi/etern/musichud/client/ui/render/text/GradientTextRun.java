package indi.etern.musichud.client.ui.render.text;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3x2f;

/** One batchable gradient text run. */
public interface GradientTextRun {
    RenderPipeline pipeline();

    GpuTextureView texture();

    /** Sampler for {@link #texture()}: the engines pick nearest for pixel art, linear for SDF atlases. */
    GpuSampler sampler();

    /** {@code pose} maps text-local coordinates to GUI space. */
    void writeVertices(VertexConsumer consumer, Matrix3x2f pose, GradientSpec gradient, float alpha,
                       TextEdgeFade fade);
}
