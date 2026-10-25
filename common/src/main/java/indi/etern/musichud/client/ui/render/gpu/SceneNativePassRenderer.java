package indi.etern.musichud.client.ui.render.gpu;

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

/** Immediate Minecraft GPU passes; does not own any input textures or returned views. */
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

    public void draw(RenderPipeline pipeline, SceneNativeTarget output,
                     Map<String, GpuTextureView> textures, Map<String, GpuBufferSlice> uniforms) {
        RenderSystem.assertOnRenderThread();
        GpuTextureView destination = output.view();
        for (GpuTextureView source : textures.values()) {
            if (source == destination || source.texture() == destination.texture()) {
                throw new IllegalArgumentException("Scene pass cannot sample its own attachment");
            }
        }
        // Minecraft's separate-attrib VAO cache requires a live buffer even for EMPTY formats.
        // The shader synthesizes its triangle with gl_VertexID and reads no vertex attributes.
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

    /** Release on resource reload/shutdown, after all queued GUI draws have consumed outputs. */
    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        if (fullscreenVertices != null) {
            fullscreenVertices.close();
            fullscreenVertices = null;
        }
    }
}
