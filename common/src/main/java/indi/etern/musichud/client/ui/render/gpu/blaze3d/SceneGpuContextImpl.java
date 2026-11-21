package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.RenderStats;
import indi.etern.musichud.client.ui.render.gpu.*;
import indi.etern.musichud.client.ui.render.interop.SceneInterop;
import indi.etern.musichud.client.ui.render.interop.SceneInteropBackends;
import indi.etern.musichud.client.ui.render.pipeline.ScenePipeline;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.interfaces.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@SuppressWarnings("resource")
public final class SceneGpuContextImpl implements SceneGpuContext {
    /** Bumped per context; a retirement can close an older context after a newer one exists. */
    private static int latestGeneration;

    private final SceneNativePassRenderer renderer;
    private final SceneTextureAllocator blazeAllocator;
    private final int maxTextureSize;
    private final boolean rgba16fTargets;
    private final int generation = ++latestGeneration;
    private final Map<PipelineKey, ScenePipeline> pipelines = new HashMap<>();
    // Uniform storages are ring buffers, so in-flight draws never see a slice overwritten by a
    // later frame.
    private final Map<UniformKey, DynamicUniformStorage<UniformAdapter>> uniformStorages =
            new HashMap<>();
    private final Set<DynamicUniformStorage<UniformAdapter>> writtenStorages =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<SceneGpuTextureImpl> textures = new ArrayList<>();
    private final List<SceneGpuMeshImpl> meshes = new ArrayList<>();
    private final Map<String, SceneNativePassRenderer.BoundTexture> samplerScratch = new HashMap<>();
    private final Map<String, GpuBufferSlice> uniformScratch = new HashMap<>();
    private final List<Runnable> deferredBackdrops = new ArrayList<>();
    private boolean closed;

    public SceneGpuContextImpl() {
        RenderSystem.assertOnRenderThread();
        var device = RenderSystem.getDevice();
        // Native Vulkan uses a standard positive viewport, so NDC y maps to the same texture row as
        // on OpenGL and the passes need no mirroring (that was VulkanMod's negative-height viewport).
        this.renderer = new SceneNativePassRenderer(false);
        // GpuFormat.RGBA16_FLOAT is available on both backends, so one allocator serves both
        // formats and no raw-GL shim is needed.
        this.blazeAllocator = new BlazeTextureAllocator(device);
        this.maxTextureSize = device.getDeviceInfo().limits().maxTextureSize();
        this.rgba16fTargets = blazeAllocator.supportsHalfFloat();
        SceneInterop.install(SceneInteropBackends.create(device));
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
        SceneTextureAllocator.SceneAllocatedTexture allocated;
        try {
            allocated = blazeAllocator.allocate(label, width, height, halfFloat);
        } catch (RuntimeException | LinkageError failure) {
            if (!halfFloat) throw failure;
            allocated = blazeAllocator.allocate(label, width, height, false);
        }
        var texture = new SceneGpuTextureImpl(format, allocated, maxTextureSize);
        textures.add(texture);
        RenderStats.targetCreated(compactLabel(label), texture.byteSize());
        return texture;
    }

    @Override
    public SceneGpuTexture createNativeRenderTarget(String label, int width, int height,
                                                    SceneTextureFormat format) {
        assertOpen();
        if (width < 1 || height < 1 || width > maxTextureSize || height > maxTextureSize) {
            throw new IllegalArgumentException(
                    "Invalid scene target size: " + width + "x" + height);
        }
        boolean halfFloat = format == SceneTextureFormat.RGBA16F
                && rgba16fTargets
                && !ClientConfig.getInstance().getForceRgba8Fallback();
        SceneTextureAllocator.SceneAllocatedTexture allocated;
        try {
            allocated = blazeAllocator.allocate(label, width, height, halfFloat);
        } catch (RuntimeException | LinkageError failure) {
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
        // 26.2 declares the color target format in the pipeline and Vulkan derives the dynamic
        // rendering attachment formats from it, so each key is compiled once per target format.
        GpuFormat format = gpuFormat(targetFormat);
        return pipelines.computeIfAbsent(new PipelineKey(key, format),
                ignored -> new VanillaScenePipeline(key.name(), createPipeline(key, format)));
    }

    private static GpuFormat gpuFormat(SceneTextureFormat format) {
        return format == SceneTextureFormat.RGBA16F
                ? GpuFormat.RGBA16_FLOAT : GpuFormat.RGBA8_UNORM;
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
                .createRenderPass(() -> "MusicHud indexed draw", target.view(), Optional.empty())) {
            pass.setPipeline(requirePipeline(draw.draw().pipeline()));
            GpuBuffer vertices = mesh.vertices();
            pass.setVertexBuffer(0, vertices.slice(0, vertices.size()));
            pass.setIndexBuffer(mesh.indices(), IndexType.SHORT);
            samplers.forEach((name, bound) -> pass.bindTexture(name, bound.view(), bound.sampler()));
            uniforms.forEach(pass::setUniform);
            pass.drawIndexed(mesh.indexCount(), 1, 0, 0, 0);
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
    public boolean passOutputMirrored() {
        return false;
    }

    @Override
    public @Nullable MainColorSource mainColorSource() {
        assertOpen();
        var minecraft = Minecraft.getInstance();
        var target = minecraft.gameRenderer.mainRenderTarget();
        var view = target.getColorTextureView();
        if (view == null) return null;
        Window window = minecraft.getWindow();
        int guiScale = window.getGuiScale();
        return new MainColorSource(SceneTextureAdapters.wrap(view), target.width, target.height,
                guiScale, false);
    }

    private Map<String, SceneNativePassRenderer.BoundTexture> samplers(SceneDraw draw) {
        // Reused across passes: the map is consumed synchronously by the render pass, and the
        // context only ever draws on the render thread.
        samplerScratch.clear();
        for (SceneSamplerBinding binding : draw.samplers()) {
            samplerScratch.put(binding.name(), new SceneNativePassRenderer.BoundTexture(
                    SceneTextureAdapters.view(binding.texture()), SceneSamplers.linearClampSingleLevel()));
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
                        "MusicHud GPU uniform " + name, size, 256));
        writtenStorages.add(storage);
        return storage.writeUniform(new UniformAdapter(uniform));
    }

    private RenderPipeline createPipeline(ScenePipelineKey key, GpuFormat format) {
        return switch (key) {
            case SCENE_CAPTURE -> renderer.pipeline("scene_capture", format)
                    .withBindGroupLayout(sampler("Sampler0"))
                    .withBindGroupLayout(uniform("MHCapture"))
                    .build();
            case SCENE_COPY -> renderer.pipeline("scene_copy", format)
                    .withBindGroupLayout(sampler("Sampler0"))
                    .build();
            case SCENE_BACKGROUND_BAKE -> renderer.pipeline("scene_background_bake", format)
                    .withBindGroupLayout(sampler("Sampler0"))
                    .withBindGroupLayout(uniform("MHBackgroundBake"))
                    .build();
            case SCENE_BLUR_DOWNSAMPLE -> blurPipeline("scene_blur_downsample", format);
            case SCENE_BLUR_GAUSSIAN -> blurPipeline("scene_blur_gaussian", format);
            case SCENE_BLUR_UPSAMPLE -> blurPipeline("scene_blur_upsample", format);
            case SCENE_BLUR_COPY -> blurPipeline("scene_blur_copy", format);
            case SCENE_BLUR_CROP -> renderer.pipeline("scene_blur_crop", format)
                    .withBindGroupLayout(sampler("Sampler0"))
                    .withBindGroupLayout(uniform("MHCrop"))
                    .build();
            case SCENE_BLOOM_COMPOSITE -> renderer.pipeline("scene_bloom_composite", format)
                    .withBindGroupLayout(sampler("Sampler0"))
                    .withBindGroupLayout(uniform("MHBloom"))
                    .build();
            case SCENE_COVER_RESOLVE -> renderer.pipeline("scene_cover_resolve", format)
                    .withBindGroupLayout(sampler("Sampler0"))
                    .withBindGroupLayout(uniform("MHCoverResolve"))
                    .build();
            case SCENE_COVER_WARP_PROCEDURAL -> SceneNativePassRenderer.meshPipeline("scene_cover_warp_procedural", format)
                    .withBindGroupLayout(sampler("Sampler0"))
                    .withBindGroupLayout(uniform("MHWarp"))
                    .build();
            case SCENE_COVER_LAYERS -> renderer.pipeline("scene_cover_layers", format)
                    .withBindGroupLayout(sampler("Sampler0"))
                    .withBindGroupLayout(uniform("MHCover"))
                    .build();
            case SCENE_COVER_OVERLAY -> renderer.pipeline("scene_cover_overlay", format)
                    .withBindGroupLayout(sampler("Sampler0"))
                    .withBindGroupLayout(sampler("Sampler1"))
                    .withBindGroupLayout(uniform("MHOverlay"))
                    .withBindGroupLayout(uniform("MHBackgroundAdjust"))
                    .build();
            case SCENE_COVER_BLEND -> renderer.pipeline("scene_cover_blend", format)
                    .withBindGroupLayout(sampler("Sampler0"))
                    .withBindGroupLayout(sampler("Sampler1"))
                    .withBindGroupLayout(uniform("MHCoverBlend"))
                    .build();
            case SCENE_ARTWORK_DOWNSAMPLE -> renderer.pipeline("scene_artwork_downsample", format)
                    .withBindGroupLayout(sampler("Sampler0"))
                    .withBindGroupLayout(uniform("MHArtworkDownsample"))
                    .build();
        };
    }

    private RenderPipeline blurPipeline(String fragment, GpuFormat format) {
        return renderer.pipeline(fragment, format)
                .withBindGroupLayout(sampler("Sampler0"))
                .withBindGroupLayout(uniform("MHBlur"))
                .build();
    }

    private static BindGroupLayout sampler(String name) {
        return BindGroupLayout.builder().withSampler(name).build();
    }

    private static BindGroupLayout uniform(String name) {
        return BindGroupLayout.builder().withUniform(name, UniformType.UNIFORM_BUFFER).build();
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
        deferredBackdrops.clear();
        MinecraftBackgroundTexture.clear();
        HudPanelBackdrop.clear();
        for (SceneGpuTextureImpl texture : new ArrayList<>(textures)) {
            RenderStats.targetReleased(texture.byteSize());
            texture.close();
        }
        textures.clear();
        for (SceneGpuMeshImpl mesh : new ArrayList<>(meshes)) mesh.close();
        meshes.clear();
        for (DynamicUniformStorage<UniformAdapter> storage : uniformStorages.values()) storage.close();
        uniformStorages.clear();
        writtenStorages.clear();
        pipelines.clear();
        renderer.close();
        if (generation == latestGeneration) {
            SceneInterop.install(null);
        }
    }

    @Override
    public void deferBackdrop(@Nullable Runnable work) {
        assertOpen();
        if (work != null) {
            deferredBackdrops.add(work);
        }
    }

    @Override
    public void runDeferredBackdrop() {
        if (deferredBackdrops.isEmpty()) return;
        List<Runnable> work = new ArrayList<>(deferredBackdrops);
        deferredBackdrops.clear();
        for (Runnable step : work) {
            try {
                step.run();
            } catch (RuntimeException failure) {
                MusicHud.getLogger(SceneGpuContextImpl.class)
                        .debug("While composing a deferred backdrop", failure);
            }
        }
    }

    /** Advances the uniform ring buffers; call once per frame after the scene was recorded. */
    @Override
    public void endFrame() {
        if (closed) return;
        // Advancing a ring that was not written this frame would only create and close a fence.
        for (DynamicUniformStorage<UniformAdapter> storage : writtenStorages) {
            storage.endFrame();
        }
        writtenStorages.clear();
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
          for (SceneTextureFormat targetFormat : SceneTextureFormat.values()) {
            if (targetFormat == SceneTextureFormat.RGBA16F && !rgba16fTargets) {
                continue;
            }
            GpuFormat format = gpuFormat(targetFormat);
            ScenePipeline pipeline = pipelines.computeIfAbsent(new PipelineKey(key, format),
                    ignored -> new VanillaScenePipeline(key.name(), createPipeline(key, format)));
            if (pipeline instanceof VanillaScenePipeline vanilla) {
                try {
                    allValid &= device.precompilePipeline(vanilla.renderPipeline()).isValid();
                } catch (RuntimeException | LinkageError failure) {
                    MusicHud.getLogger(SceneGpuContextImpl.class)
                            .debug("While precompiling scene pipeline " + key, failure);
                    allValid = false;
                }
            }
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

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, path);
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

    private record PipelineKey(ScenePipelineKey key, GpuFormat format) {
    }
}
