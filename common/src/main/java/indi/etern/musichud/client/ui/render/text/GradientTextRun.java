package indi.etern.musichud.client.ui.render.text;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3x2f;

/** One batchable gradient text run. */
public interface GradientTextRun {
    RenderPipeline pipeline();

    GpuTextureView texture();

    /** {@code pose} maps text-local coordinates to GUI space. */
    void writeVertices(VertexConsumer consumer, float z, Matrix3x2f pose, GradientSpec gradient, float alpha);
}
