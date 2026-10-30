package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.RenderStats;
import indi.etern.musichud.client.ui.render.gpu.*;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.gl.GlHalfFloatAllocator;
import indi.etern.musichud.client.ui.render.pipeline.ScenePipeline;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.interfaces.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public final class SceneGpuContextImpl implements SceneGpuContext {
    private final SceneNativePassRenderer renderer = new SceneNativePassRenderer();
    private final SceneTextureAllocator blazeAllocator;
    private final SceneTextureAllocator halfFloatAllocator;
    private final int maxTextureSize;
    private final boolean rgba16fTargets;
    private final Map<ScenePipelineKey, ScenePipeline> pipelines = new HashMap<>();
    // Uniform storages are ring buffers: a frame's uploads never overwrite the slices the previous
    // frames' in-flight draws still reference. Rewriting a single buffer per uniform (as before)
    // relies on driver-side buffer renaming and can stall.
    private final Map<UniformKey, DynamicUniformStorage<UniformAdapter>> uniformStorages =
            new HashMap<>();
    private final List<SceneGpuTextureImpl> textures = new ArrayList<>();
    private final List<SceneGpuMeshImpl> meshes = new ArrayList<>();
    private final Map<String, GpuTextureView> samplerScratch = new HashMap<>();
    private final Map<String, GpuBufferSlice> uniformScratch = new HashMap<>();
    private boolean closed;

    public SceneGpuContextImpl() {
        RenderSystem.assertOnRenderThread();
        var device = RenderSystem.getDevice();
        this.blazeAllocator = new BlazeTextureAllocator(device);
        this.halfFloatAllocator = new GlHalfFloatAllocator(device);
        this.maxTextureSize = device.getMaxTextureSize();
        this.rgba16fTargets = halfFloatAllocator.supportsHalfFloat();
    }

    @Override
    public SceneGpuTexture createRenderTarget(String label, int width, int height,
                                              SceneTextureFormat format) {
        assertOpen();
        if (width < 1 || height < 1 || width > maxTextureSize || height > maxTextureSize) {
            throw new IllegalArgumentException(
                    "Invalid scene target size: " + width + "x" + height);
        }
        boolean halfFloat = format == SceneTextureFormat.RGBA16F
                && rgba16fTargets
                && !ClientConfig.getInstance().getForceRgba8Fallback();
        boolean nativePath = halfFloat || ClientConfig.getInstance().getForceGL32Path();
        SceneTextureAllocator.SceneAllocatedTexture allocated;
        try {
            allocated = (nativePath ? halfFloatAllocator : blazeAllocator)
                    .allocate(label, width, height, halfFloat);
        } catch (RuntimeException | LinkageError failure) {
            if (!nativePath) throw failure;
            allocated = blazeAllocator.allocate(label, width, height, false);
        }
        var texture = new SceneGpuTextureImpl(format, allocated, maxTextureSize);
        textures.add(texture);
        RenderStats.targetCreated(compactLabel(label), texture.byteSize());
        return texture;
    }

    /** Trims the {@code MusicHud …} debug labels down to a stable, log-friendly tag. */
    private static String compactLabel(String label) {
        String tag = label.startsWith("MusicHud ") ? label.substring("MusicHud ".length()) : label;
        if (tag.startsWith("cover ")) {
            return tag.substring("cover ".length());
        }
        if (tag.startsWith("scene ")) {
            return tag.substring("scene ".length());
        }
        if (tag.startsWith("procedural ")) {
            return tag.substring("procedural ".length());
        }
        if (tag.equals("canonical cover")) {
            return "canonical";
        }
        return tag;
    }

    @Override
    public ScenePipeline pipeline(ScenePipelineKey key, SceneTextureFormat targetFormat) {
        assertOpen();
        // Pipelines are target-format agnostic here, so one pipeline serves RGBA8 and RGBA16F.
        return pipelines.computeIfAbsent(key,
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
        RenderStats.meshUploaded(vertices.remaining());
        requireMesh(mesh).update(vertices);
    }

    @Override
    public void draw(SceneDraw draw) {
        assertOpen();
        RenderStats.pass();
        var target = requireTexture(draw.target());
        var samplers = samplers(draw);
        var uniforms = uniforms(draw);
        renderer.draw(requirePipeline(draw.pipeline()), target.view(), samplers, uniforms);
    }

    @Override
    public void drawIndexed(SceneIndexedDraw draw) {
        assertOpen();
        RenderStats.pass();
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
        requireTexture(target).clear(argb);
    }

    @Override
    public void release(SceneGpuTexture texture) {
        if (!(texture instanceof SceneGpuTextureImpl impl)) return;
        if (!closed && textures.remove(impl)) {
            RenderStats.targetReleased(impl.byteSize());
        }
        impl.close();
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
        // Reused across passes: the map is consumed synchronously by the render pass, and the
        // context only ever draws on the render thread.
        samplerScratch.clear();
        for (SceneSamplerBinding binding : draw.samplers()) {
            samplerScratch.put(binding.name(), SceneTextureAdapters.view(binding.texture()));
        }
        return samplerScratch;
    }

    private Map<String, GpuBufferSlice> uniforms(SceneDraw draw) {
        uniformScratch.clear();
        for (SceneUniformBinding binding : draw.uniforms()) {
            uniformScratch.put(binding.name(), uniformSlice(draw.pipeline(), binding.name(), binding.uniform()));
        }
        return uniformScratch;
    }

    private GpuBufferSlice uniformSlice(ScenePipeline pipeline, String name, SceneUniform uniform) {
        int size = uniform.getUBOSize();
        RenderStats.uniformUploaded(size);
        var key = new UniformKey(pipeline.name(), name, size);
        DynamicUniformStorage<UniformAdapter> storage = uniformStorages.computeIfAbsent(key,
                ignored -> new DynamicUniformStorage<>(
                        "MusicHud GPU uniform " + name, size, 64));
        return storage.writeUniform(new UniformAdapter(uniform));
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
            case SCENE_BLUR_CROP -> SceneNativePassRenderer.pipeline("scene_blur_crop")
                    .withSampler("Sampler0")
                    .withUniform("MHCrop", UniformType.UNIFORM_BUFFER)
                    .build();
            case SCENE_COVER_RESOLVE -> SceneNativePassRenderer.pipeline("scene_cover_resolve")
                    .withSampler("Sampler0")
                    .withUniform("MHCoverResolve", UniformType.UNIFORM_BUFFER)
                    .build();
            case SCENE_COVER_WARP_PROCEDURAL -> SceneNativePassRenderer.meshPipeline(
                            "scene_cover_warp_procedural")
                    .withSampler("Sampler0")
                    .withUniform("MHWarp", UniformType.UNIFORM_BUFFER)
                    .build();
            case SCENE_COVER_LAYERS -> SceneNativePassRenderer.pipeline("scene_cover_layers")
                    .withSampler("Sampler0")
                    .withUniform("MHCover", UniformType.UNIFORM_BUFFER)
                    .build();
            case SCENE_COVER_OVERLAY -> SceneNativePassRenderer.pipeline("scene_cover_overlay")
                    .withSampler("Sampler0")
                    .withSampler("Sampler1")
                    .withUniform("MHOverlay", UniformType.UNIFORM_BUFFER)
                    .withUniform("MHBackgroundAdjust", UniformType.UNIFORM_BUFFER)
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
        for (SceneGpuTextureImpl texture : new ArrayList<>(textures)) {
            RenderStats.targetReleased(texture.byteSize());
            texture.close();
        }
        textures.clear();
        for (SceneGpuMeshImpl mesh : new ArrayList<>(meshes)) mesh.close();
        meshes.clear();
        for (DynamicUniformStorage<UniformAdapter> storage : uniformStorages.values()) storage.close();
        uniformStorages.clear();
        pipelines.clear();
        renderer.close();
    }

    /** Advances the uniform ring buffers; call once per frame after the scene was recorded. */
    @Override
    public void endFrame() {
        if (closed) return;
        for (DynamicUniformStorage<UniformAdapter> storage : uniformStorages.values()) {
            storage.endFrame();
        }
    }

    @Override
    public PrecompileResult precompilePipelines() {
        assertOpen();
        // The shader sources are provided by the resource reload, which finishes a few ticks after
        // the client starts. Compiling before that makes the device log "Couldn't find source" for
        // every pipeline, so probe a representative vertex/fragment pair first.
        if (!shaderSourcesReady()) {
            return PrecompileResult.NOT_READY;
        }
        var device = RenderSystem.getDevice();
        boolean allValid = true;
        for (ScenePipelineKey key : ScenePipelineKey.values()) {
            ScenePipeline pipeline = pipelines.computeIfAbsent(key,
                    ignored -> new VanillaScenePipeline(key.name(), createPipeline(key)));
            if (pipeline instanceof VanillaScenePipeline vanilla) {
                allValid &= device.precompilePipeline(vanilla.renderPipeline()).isValid();
            }
        }
        return allValid ? PrecompileResult.DONE : PrecompileResult.FAILED;
    }

    private static boolean shaderSourcesReady() {
        Minecraft minecraft = Minecraft.getInstance();
        //noinspection ConstantValue
        ShaderManager shaders = minecraft == null ? null : minecraft.getShaderManager();
        //noinspection ConstantValue
        if (shaders == null) {
            return false;
        }
        //noinspection ConstantValue
        return shaders.getShader(id("core/fullscreen"), ShaderType.VERTEX) != null
                && shaders.getShader(id("core/scene_capture"), ShaderType.FRAGMENT) != null;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, path);
    }

    private record UniformAdapter(SceneUniform uniform)
            implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(@NotNull ByteBuffer byteBuffer) {
            uniform.write(byteBuffer);
        }
    }

    private record UniformKey(String pipeline, String name, int size) {
    }
}
