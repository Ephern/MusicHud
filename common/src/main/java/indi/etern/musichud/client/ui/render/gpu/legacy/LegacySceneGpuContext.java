package indi.etern.musichud.client.ui.render.gpu.legacy;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.RenderStats;
import indi.etern.musichud.client.ui.render.gpu.SceneDraw;import indi.etern.musichud.client.ui.render.gpu.SceneGpuContext;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuTexture;
import indi.etern.musichud.client.ui.render.gpu.SceneIndexedDraw;
import indi.etern.musichud.client.ui.render.gpu.SceneMesh;
import indi.etern.musichud.client.ui.render.gpu.ScenePipelineKey;
import indi.etern.musichud.client.ui.render.gpu.SceneSamplerBinding;
import indi.etern.musichud.client.ui.render.gpu.SceneTextureFormat;
import indi.etern.musichud.client.ui.render.gpu.SceneUniformBinding;
import indi.etern.musichud.client.ui.render.interop.SceneInterop;
import indi.etern.musichud.client.ui.render.pipeline.HudShaderManager;
import indi.etern.musichud.client.ui.render.pipeline.HudShaderProgram;
import indi.etern.musichud.client.ui.render.pipeline.ScenePipeline;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("resource")
public final class LegacySceneGpuContext implements SceneGpuContext {
    private final Map<ScenePipelineKey, HudShaderProgram> pipelines =
            new EnumMap<>(ScenePipelineKey.class);
    private final Map<String, HudShaderProgram.UniformBufferHandle> uniformHandles = new HashMap<>();
    private final Map<String, ByteBuffer> uniformBuffers = new HashMap<>();
    private final List<LegacySceneTexture> textures = new ArrayList<>();
    private final List<LegacySceneMesh> meshes = new ArrayList<>();
    private final int fullscreenVertexArray;
    private boolean closed;

    public LegacySceneGpuContext() {
        RenderSystem.assertOnRenderThread();
        fullscreenVertexArray = GlStateManager._glGenVertexArrays();
        SceneInterop.install(LegacyInteropBackend.INSTANCE);
    }

    @Override
    public SceneGpuTexture createRenderTarget(String label, int width, int height,
                                              SceneTextureFormat format) {
        assertOpen();
        // TextureTarget allocates and binds textures/framebuffers, and RenderTarget#clear
        // unbinds to framebuffer 0. Keep that inside a state scope so the caller's target
        // (Minecraft's main render target while drawing the GUI) stays bound.
        try (LegacyGlStateScope ignored = LegacyGlStateScope.capture()) {
            LegacySceneTexture texture = new LegacySceneTexture(width, height, format);
            textures.add(texture);
            RenderStats.targetCreated(label, texture.byteSize());
            return texture;
        }
    }

    @Override
    public SceneGpuTexture createNativeRenderTarget(String label, int width, int height,
                                                    SceneTextureFormat format) {
        return createRenderTarget(label, width, height, format);
    }

    @Override
    public ScenePipeline pipeline(ScenePipelineKey key, SceneTextureFormat targetFormat) {
        assertOpen();
        return pipelines.computeIfAbsent(key, this::createPipeline);
    }

    private HudShaderProgram createPipeline(ScenePipelineKey key) {
        String name = "scene_" + key.name().substring("SCENE_".length()).toLowerCase();
        String vertex = key == ScenePipelineKey.SCENE_COVER_WARP_PROCEDURAL
                ? name + ".vsh" : "fullscreen.vsh";
        List<String> ubos = switch (key) {
            case SCENE_CAPTURE -> List.of("MHCapture");
            case SCENE_BACKGROUND_BAKE -> List.of("MHBackgroundBake");
            case SCENE_BLUR_DOWNSAMPLE, SCENE_BLUR_GAUSSIAN, SCENE_BLUR_UPSAMPLE,
                 SCENE_BLUR_COPY -> List.of("MHBlur");
            case SCENE_BLUR_CROP -> List.of("MHCrop");
            case SCENE_BLOOM_COMPOSITE -> List.of("MHBloom");
            case SCENE_COVER_RESOLVE -> List.of("MHCoverResolve");
            case SCENE_COVER_WARP_PROCEDURAL -> List.of("MHWarp");
            case SCENE_COVER_LAYERS -> List.of("MHCover");
            case SCENE_COVER_OVERLAY -> List.of("MHOverlay", "MHBackgroundAdjust");
            case SCENE_COVER_BLEND -> List.of("MHCoverBlend");
            case SCENE_ARTWORK_DOWNSAMPLE -> List.of("MHArtworkDownsample");
        };
        return HudShaderManager.getOrCreate(key.name(),
                shader(vertex), shader(name + ".fsh"), ubos);
    }

    private static ResourceLocation shader(String file) {
        return ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "shaders/core/" + file);
    }

    @Override
    public PrecompileResult precompilePipelines() {
        assertOpen();
        for (ScenePipelineKey key : ScenePipelineKey.values()) {
            if (requirePipeline(pipeline(key, SceneTextureFormat.RGBA8)).getProgramId() <= 0) {
                return PrecompileResult.FAILED;
            }
        }
        return PrecompileResult.DONE;
    }

    @Override
    public SceneMesh createMesh(String label, int vertexBytes, short[] indices) {
        assertOpen();
        try (LegacyGlStateScope ignored = LegacyGlStateScope.capture()) {
            LegacySceneMesh mesh = new LegacySceneMesh(vertexBytes, indices);
            meshes.add(mesh);
            return mesh;
        }
    }

    @Override
    public void updateMesh(SceneMesh mesh, ByteBuffer vertices) {
        assertOpen();
        try (LegacyGlStateScope ignored = LegacyGlStateScope.capture()) {
            requireMesh(mesh).update(vertices);
            RenderStats.meshUploaded(vertices.remaining());
        }
    }

    @Override
    public void draw(SceneDraw draw) {
        assertOpen();
        try (LegacyGlStateScope ignored = LegacyGlStateScope.capture()) {
            LegacySceneTexture target = requireTexture(draw.target());
            HudShaderProgram program = requirePipeline(draw.pipeline());
            target.bind();
            preparePass(program, draw.samplers(), draw.uniforms());
            GlStateManager._glBindVertexArray(fullscreenVertexArray);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            target.unbind();
            RenderStats.pass();
        }
    }

    @Override
    public void drawIndexed(SceneIndexedDraw draw) {
        assertOpen();
        try (LegacyGlStateScope ignored = LegacyGlStateScope.capture()) {
            LegacySceneTexture target = requireTexture(draw.draw().target());
            LegacySceneMesh mesh = requireMesh(draw.mesh());
            HudShaderProgram program = requirePipeline(draw.draw().pipeline());
            target.bind();
            preparePass(program, draw.draw().samplers(), draw.draw().uniforms());
            mesh.draw();
            GlStateManager._glBindVertexArray(0);
            target.unbind();
            RenderStats.pass();
        }
    }

    private void preparePass(HudShaderProgram program,
                             List<SceneSamplerBinding> samplers,
                             List<SceneUniformBinding> uniforms) {
        GlStateManager._disableScissorTest();
        GlStateManager._disableBlend();
        GlStateManager._disableDepthTest();
        GlStateManager._disableCull();
        GL20.glUseProgram(program.getProgramId());
        for (SceneSamplerBinding binding : samplers) {
            int unit = unit(binding.name());
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
            GlStateManager._bindTexture(textureId(binding.texture()));
            int location = program.getUniformOrSamplerLocation(binding.name());
            if (location >= 0) GL20.glUniform1i(location, unit);
        }
        if (!uniforms.isEmpty()) {
            long start = System.nanoTime();
            for (SceneUniformBinding binding : uniforms) {
                uploadUniform(program, binding.name(), binding.uniform());
            }
            RenderStats.addUniformNanos(System.nanoTime() - start);
        }
    }

    private void uploadUniform(HudShaderProgram program, String name,
                               indi.etern.musichud.client.ui.render.pipeline.SceneUniform uniform) {
        Integer binding = HudShaderManager.getBindingPoint(name);
        final int bindingPoint = binding != null
                ? binding : HudShaderManager.getOrCreateBindingPoint(name);
        int size = uniform.getUBOSize();
        RenderStats.uniformUploaded(size);
        String key = program.getProgramId() + "/" + name;
        ByteBuffer buffer = uniformBuffers.computeIfAbsent(key,
                ignored -> ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder()));
        buffer.clear();
        uniform.write(buffer);
        buffer.flip();
        HudShaderProgram.UniformBufferHandle handle = uniformHandles.computeIfAbsent(key,
                ignored -> HudShaderProgram.UniformBufferHandle.createAndUpload(bindingPoint, buffer));
        handle.upload(buffer);
    }

    private static int unit(String name) {
        if (name.startsWith("Sampler")) {
            try {
                return Integer.parseInt(name.substring("Sampler".length()));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private static int textureId(SceneTextureRef ref) {
        if (ref instanceof LegacyTextureRef(int id)) return id;
        if (ref instanceof LegacySceneTexture texture) return texture.textureId();
        if (ref instanceof LegacyTextureIdRef idRef) return idRef.textureId();
        throw new IllegalArgumentException("Unsupported scene texture reference");
    }

    @Override
    public void clear(SceneGpuTexture target, int argb) {
        assertOpen();
        try (LegacyGlStateScope ignored = LegacyGlStateScope.capture()) {
            LegacySceneTexture texture = requireTexture(target);
            texture.bind();
            GlStateManager._clearColor(((argb >> 16) & 0xFF) / 255f,
                    ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f,
                    ((argb >>> 24) & 0xFF) / 255f);
            GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT, false);
            texture.unbind();
        }
    }

    @Override
    public void release(SceneGpuTexture texture) {
        if (!(texture instanceof LegacySceneTexture legacy)) return;
        textures.remove(legacy);
        // destroyBuffers() unbinds to framebuffer 0 and texture unit 0, which would move the
        // rest of the GUI/HUD frame off Minecraft's main render target.
        try (LegacyGlStateScope ignored = LegacyGlStateScope.capture()) {
            legacy.close();
        }
        RenderStats.targetReleased(legacy.byteSize());
    }

    @Override
    public void release(SceneMesh mesh) {
        if (!(mesh instanceof LegacySceneMesh legacy)) return;
        meshes.remove(legacy);
        try (LegacyGlStateScope ignored = LegacyGlStateScope.capture()) {
            legacy.close();
        }
    }

    @Override
    public @NotNull MainColorSource mainColorSource() {
        assertOpen();
        Minecraft minecraft = Minecraft.getInstance();
        var target = minecraft.getMainRenderTarget();
        return new MainColorSource(new LegacyTextureRef(target.getColorTextureId()),
                target.width, target.height, (int) minecraft.getWindow().getGuiScale());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (RenderSystem.isOnRenderThread()) {
            try (LegacyGlStateScope ignored = LegacyGlStateScope.capture()) {
                destroyAll();
            }
        } else {
            destroyAll();
        }
        SceneInterop.install(null);
    }

    private void destroyAll() {
        for (LegacySceneTexture texture : new ArrayList<>(textures)) texture.close();
        textures.clear();
        for (LegacySceneMesh mesh : new ArrayList<>(meshes)) mesh.close();
        meshes.clear();
        for (HudShaderProgram.UniformBufferHandle handle : uniformHandles.values()) handle.delete();
        uniformHandles.clear();
        uniformBuffers.clear();
        pipelines.clear();
        GlStateManager._glDeleteVertexArrays(fullscreenVertexArray);
    }

    private void assertOpen() {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("Scene GPU context is closed");
    }

    private static LegacySceneTexture requireTexture(SceneGpuTexture texture) {
        if (texture instanceof LegacySceneTexture legacy) return legacy;
        throw new IllegalArgumentException("Unsupported scene texture");
    }

    private static LegacySceneMesh requireMesh(SceneMesh mesh) {
        if (mesh instanceof LegacySceneMesh legacy) return legacy;
        throw new IllegalArgumentException("Unsupported scene mesh");
    }

    private static HudShaderProgram requirePipeline(ScenePipeline pipeline) {
        if (pipeline instanceof HudShaderProgram program) {
            if (program.getProgramId() <= 0) {
                // Callers catch RuntimeException and degrade to the previous stage,
                // which is safer than drawing into an undefined program.
                throw new IllegalStateException("Scene pipeline '" + program.name() + "' is not linked");
            }
            return program;
        }
        throw new IllegalArgumentException("Unsupported scene pipeline");
    }
}
