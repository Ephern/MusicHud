package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import indi.etern.musichud.MusicHud;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.OptionalInt;

/** Does not own input textures or returned views. */
final class SceneNativePassRenderer implements AutoCloseable {
    private GpuBuffer fullscreenVertices;

    public static RenderPipeline.Builder pipeline(String fragment) {
        return RenderPipeline.builder()
                .withLocation(id("pipeline/" + fragment))
                .withVertexShader(id("core/fullscreen"))
                .withFragmentShader(id("core/" + fragment))
                .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false).withCull(false).withoutBlend();
    }

    public static RenderPipeline.Builder meshPipeline(String shader) {
        return RenderPipeline.builder()
                .withLocation(id("pipeline/" + shader))
                .withVertexShader(id("core/" + shader))
                .withFragmentShader(id("core/" + shader))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.TRIANGLES)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false).withCull(false).withoutBlend();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, path);
    }

    public void draw(RenderPipeline pipeline, GpuTextureView destination,
                     Map<String, GpuTextureView> textures, Map<String, GpuBufferSlice> uniforms) {
        RenderSystem.assertOnRenderThread();
        for (GpuTextureView source : textures.values()) {
            if (source == destination || source.texture() == destination.texture()) {
                throw new IllegalArgumentException("Scene pass cannot sample its own attachment");
            }
        }
        // EMPTY-format pipelines still need a live vertex buffer for the VAO cache.
        if (fullscreenVertices == null) {
            fullscreenVertices = RenderSystem.getDevice().createBuffer(() -> "MusicHud fullscreen",
                    GpuBuffer.USAGE_VERTEX, 16);
        }
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "MusicHud scene pass", destination, OptionalInt.empty())) {
            pass.setPipeline(pipeline);
            pass.setVertexBuffer(0, fullscreenVertices);
            textures.forEach(pass::bindSampler);
            uniforms.forEach(pass::setUniform);
            pass.draw(0, 3);
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
