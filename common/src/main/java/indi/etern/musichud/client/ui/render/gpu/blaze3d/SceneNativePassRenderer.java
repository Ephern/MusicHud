package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import indi.etern.musichud.MusicHud;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;

/** Does not own input textures or returned views. */
final class SceneNativePassRenderer implements AutoCloseable {
    /** A texture to sample with the sampler the backend needs for it. */
    record BoundTexture(GpuTextureView view, GpuSampler sampler) {
    }

    private final boolean flipVertexV;
    private GpuBuffer fullscreenVertices;

    /**
     * @param flipVertexV true when a pass mirrors its output (a bottom-left viewport); the vertex
     *                    shaders then mirror the sampled row so passes stay orientation-neutral
     */
    SceneNativePassRenderer(boolean flipVertexV) {
        this.flipVertexV = flipVertexV;
    }

    public RenderPipeline.Builder pipeline(String fragment, GpuFormat format) {
        return RenderPipeline.builder()
                .withLocation(id("pipeline/" + fragment + formatSuffix(format)))
                .withVertexShader(id(flipVertexV ? "core/fullscreen_flip" : "core/fullscreen"))
                .withFragmentShader(id("core/" + fragment))
                .withVertexBinding(0, DefaultVertexFormat.POSITION)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withDepthStencilState(Optional.empty())
                .withCull(false)
                .withColorTargetState(colorTarget(format));
    }

    public static RenderPipeline.Builder meshPipeline(String shader, GpuFormat format) {
        return RenderPipeline.builder()
                .withLocation(id("pipeline/" + shader + formatSuffix(format)))
                .withVertexShader(id("core/" + shader))
                .withFragmentShader(id("core/" + shader))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withDepthStencilState(Optional.empty())
                .withCull(false)
                .withColorTargetState(colorTarget(format));
    }

    private static ColorTargetState colorTarget(GpuFormat format) {
        return new ColorTargetState(Optional.empty(), format, ColorTargetState.WRITE_ALL);
    }

    /**
     * The declared color target format is part of the compiled pipeline, so each format needs its
     * own pipeline location.
     */
    private static String formatSuffix(GpuFormat format) {
        return format == GpuFormat.RGBA16_FLOAT ? "_rgba16f" : "";
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, path);
    }

    public void draw(RenderPipeline pipeline, GpuTextureView destination,
                     Map<String, BoundTexture> textures, Map<String, GpuBufferSlice> uniforms) {
        RenderSystem.assertOnRenderThread();
        for (BoundTexture source : textures.values()) {
            if (source.view() == destination || source.view().texture() == destination.texture()) {
                throw new IllegalArgumentException("Scene pass cannot sample its own attachment");
            }
        }
        // Attribute-less shaders still need a live vertex buffer for the VAO cache; it is sized for
        // the three positions the pipeline declares but never reads.
        if (fullscreenVertices == null) {
            fullscreenVertices = RenderSystem.getDevice().createBuffer(() -> "MusicHud fullscreen",
                    GpuBuffer.USAGE_VERTEX, 48);
        }
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "MusicHud scene pass", destination, Optional.empty())) {
            pass.setPipeline(pipeline);
            pass.setVertexBuffer(0, fullscreenVertices.slice(0, fullscreenVertices.size()));
            textures.forEach((name, bound) -> pass.bindTexture(name, bound.view(), bound.sampler()));
            uniforms.forEach(pass::setUniform);
            pass.draw(3, 1, 0, 0);
        }
    }

    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        if (fullscreenVertices != null) {
            fullscreenVertices.close();
            fullscreenVertices = null;
        }
    }
}
