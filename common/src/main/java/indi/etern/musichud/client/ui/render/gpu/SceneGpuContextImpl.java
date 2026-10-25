package indi.etern.musichud.client.ui.render.gpu;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import indi.etern.musichud.client.ui.render.pipeline.ScenePipeline;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.VanillaScenePipeline;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public final class SceneGpuContextImpl implements SceneGpuContext {
    private final SceneNativePassRenderer renderer = new SceneNativePassRenderer();
    private final Map<PipelineKey, ScenePipeline> pipelines = new HashMap<>();
    private final Map<UniformKey, GpuBuffer> uniformBuffers = new HashMap<>();
    private final List<SceneGpuTextureImpl> textures = new ArrayList<>();
    private final List<SceneGpuMeshImpl> meshes = new ArrayList<>();
    private boolean closed;

    public SceneGpuContextImpl() {
        RenderSystem.assertOnRenderThread();
    }

    @Override
    public SceneGpuTexture createRenderTarget(String label, int width, int height,
                                              SceneTextureFormat format, SceneSamplerState sampler) {
        assertOpen();
        var texture = new SceneGpuTextureImpl(label, width, height, format, sampler);
        textures.add(texture);
        return texture;
    }

    @Override
    public SceneSampler sampler(SceneSamplerState state) {
        assertOpen();
        return new SceneGpuSamplerImpl(state);
    }

    @Override
    public ScenePipeline pipeline(ScenePipelineKey key, SceneTextureFormat targetFormat) {
        assertOpen();
        return pipelines.computeIfAbsent(new PipelineKey(key, targetFormat),
                ignored -> new VanillaScenePipeline(key.name(), createPipeline(key)));
    }

    @Override
    public SceneMesh createMesh(String label, int vertexBytes, short[] indices) {
        assertOpen();
        var mesh = new SceneGpuMeshImpl(label, vertexBytes, indices);
        meshes.add(mesh);
        return mesh;
    }

    @Override
    public void updateMesh(SceneMesh mesh, ByteBuffer vertices) {
        assertOpen();
        requireMesh(mesh).update(vertices);
    }

    @Override
    public void draw(SceneDraw draw) {
        assertOpen();
        var target = requireTexture(draw.target());
        var samplers = samplers(draw);
        var uniforms = uniforms(draw);
        renderer.draw(requirePipeline(draw.pipeline()), target.target(), samplers, uniforms);
    }

    @Override
    public void drawIndexed(SceneIndexedDraw draw) {
        assertOpen();
        var target = requireTexture(draw.draw().target());
        var mesh = requireMesh(draw.mesh());
        var samplers = samplers(draw.draw());
        var uniforms = uniforms(draw.draw());
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "MusicHud indexed draw", target.view(), OptionalInt.empty())) {
            pass.setPipeline(requirePipeline(draw.draw().pipeline()));
            pass.setVertexBuffer(0, mesh.vertices());
            pass.setIndexBuffer(mesh.indices(), VertexFormat.IndexType.SHORT);
            samplers.forEach(pass::bindSampler);
            uniforms.forEach(pass::setUniform);
            pass.drawIndexed(0, 0, mesh.indexCount(), 1);
        }
    }

    @Override
    public void clear(SceneGpuTexture target, int argb) {
        assertOpen();
        requireTexture(target).target().clear(argb);
    }

    @Override
    public byte[] readPixels(SceneGpuTexture target) {
        assertOpen();
        return requireTexture(target).target().readRgba8();
    }

    @Override
    public void release(SceneGpuTexture texture) {
        if (!(texture instanceof SceneGpuTextureImpl impl)) return;
        textures.remove(impl);
        impl.target().close();
    }

    @Override
    public void release(SceneMesh mesh) {
        if (!(mesh instanceof SceneGpuMeshImpl impl)) return;
        meshes.remove(impl);
        impl.close();
    }

    @Override
    public @Nullable MainColorSource mainColorSource() {
        assertOpen();
        var minecraft = Minecraft.getInstance();
        var target = minecraft.getMainRenderTarget();
        var view = target.getColorTextureView();
        if (view == null) return null;
        Window window = minecraft.getWindow();
        int guiScale = window.getGuiScale();
        return new MainColorSource(SceneTextureAdapters.wrap(view), target.width, target.height, guiScale);
    }

    private Map<String, GpuTextureView> samplers(SceneDraw draw) {
        Map<String, GpuTextureView> result = new HashMap<>();
        for (SceneSamplerBinding binding : draw.samplers()) {
            result.put(binding.name(), SceneTextureAdapters.view(binding.texture()));
        }
        return result;
    }

    private Map<String, GpuBufferSlice> uniforms(SceneDraw draw) {
        Map<String, GpuBufferSlice> result = new HashMap<>();
        for (SceneUniformBinding binding : draw.uniforms()) {
            result.put(binding.name(), uniformSlice(draw.pipeline(), binding.name(), binding.uniform()));
        }
        return result;
    }

    private GpuBufferSlice uniformSlice(ScenePipeline pipeline, String name, SceneUniform uniform) {
        int size = uniform.getUBOSize();
        var key = new UniformKey(pipeline.name(), name, size);
        GpuBuffer buffer = uniformBuffers.computeIfAbsent(key,
                ignored -> RenderSystem.getDevice().createBuffer(() -> "MusicHud GPU uniform " + name,
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, size));
        ByteBuffer data = MemoryUtil.memAlloc(size).order(ByteOrder.nativeOrder());
        try {
            data.position(0).limit(size);
            uniform.write(data);
            data.position(0).limit(size);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        } finally {
            MemoryUtil.memFree(data);
        }
        return buffer.slice();
    }

    private RenderPipeline createPipeline(ScenePipelineKey key) {
        return switch (key) {
            case SCENE_CAPTURE -> SceneNativePassRenderer.pipeline("scene_capture")
                    .withSampler("Sampler0")
                    .withUniform("MHCapture", UniformType.UNIFORM_BUFFER)
                    .build();
            case SCENE_BLUR_DOWNSAMPLE -> blurPipeline("scene_blur_downsample");
            case SCENE_BLUR_GAUSSIAN -> blurPipeline("scene_blur_gaussian");
            case SCENE_BLUR_UPSAMPLE -> blurPipeline("scene_blur_upsample");
            case SCENE_BLUR_COPY -> blurPipeline("scene_blur_copy");
            case SCENE_COVER_WARP -> SceneNativePassRenderer.pipeline("scene_cover_warp")
                    .withSampler("Sampler0")
                    .withUniform("MHCoverWarp", UniformType.UNIFORM_BUFFER)
                    .build();
            case SCENE_COVER_LAYERS -> SceneNativePassRenderer.pipeline("scene_cover_layers")
                    .withSampler("Sampler0")
                    .withUniform("MHCover", UniformType.UNIFORM_BUFFER)
                    .build();
            case SCENE_COVER_OVERLAY -> SceneNativePassRenderer.pipeline("scene_cover_overlay")
                    .withSampler("Sampler0")
                    .withSampler("Sampler1")
                    .withUniform("MHOverlay", UniformType.UNIFORM_BUFFER)
                    .build();
            case SCENE_COVER_FLUID_SCALE -> SceneNativePassRenderer.pipeline("scene_cover_fluid_scale")
                    .withSampler("Sampler0")
                    .withUniform("MHFluidScale", UniformType.UNIFORM_BUFFER)
                    .build();
            case SCENE_COVER_BLEND -> SceneNativePassRenderer.pipeline("scene_cover_blend")
                    .withSampler("Sampler0")
                    .withSampler("Sampler1")
                    .withUniform("MHCoverBlend", UniformType.UNIFORM_BUFFER)
                    .build();
        };
    }

    private static RenderPipeline blurPipeline(String fragment) {
        return SceneNativePassRenderer.pipeline(fragment)
                .withSampler("Sampler0")
                .withUniform("MHBlur", UniformType.UNIFORM_BUFFER)
                .build();
    }

    private static RenderPipeline requirePipeline(ScenePipeline pipeline) {
        if (pipeline instanceof VanillaScenePipeline vanilla) return vanilla.renderPipeline();
        throw new IllegalArgumentException("Unsupported scene pipeline: " + pipeline.name());
    }

    private static SceneGpuTextureImpl requireTexture(SceneGpuTexture texture) {
        if (texture instanceof SceneGpuTextureImpl impl) return impl;
        throw new IllegalArgumentException("Unsupported scene texture");
    }

    private static SceneGpuMeshImpl requireMesh(SceneMesh mesh) {
        if (mesh instanceof SceneGpuMeshImpl impl) return impl;
        throw new IllegalArgumentException("Unsupported scene mesh");
    }

    private void assertOpen() {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("Scene GPU context is closed");
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        for (SceneGpuTextureImpl texture : new ArrayList<>(textures)) texture.target().close();
        textures.clear();
        for (SceneGpuMeshImpl mesh : new ArrayList<>(meshes)) mesh.close();
        meshes.clear();
        for (GpuBuffer buffer : uniformBuffers.values()) buffer.close();
        uniformBuffers.clear();
        pipelines.clear();
        renderer.close();
    }

    private record PipelineKey(ScenePipelineKey key, SceneTextureFormat targetFormat) {
    }

    private record UniformKey(String pipeline, String name, int size) {
    }
}
