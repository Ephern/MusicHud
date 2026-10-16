package indi.etern.musichud.client.ui.hud.renderer.text;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.joml.Matrix3x2f;

/**
 * One batchable chunk of gradient text: every glyph in a run shares the same pipeline and
 * font texture, matching how both the vanilla and the Modern UI text engines split their
 * GPU batches. A run knows how to emit its glyph quads with per-vertex gradient colors.
 */
public interface GradientTextRun {
    RenderPipeline pipeline();

    GpuTextureView texture();

    /**
     * Emits the glyph quads of this run. {@code pose} maps text-local coordinates (origin at
     * the text's left baseline start, {@code x} growing right) to GUI space.
     */
    void writeVertices(VertexConsumer consumer, Matrix3x2f pose, GradientSpec gradient, float alpha);
}
