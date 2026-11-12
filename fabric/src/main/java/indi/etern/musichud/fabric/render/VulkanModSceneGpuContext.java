package indi.etern.musichud.fabric.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.RenderStats;
import indi.etern.musichud.client.ui.render.gpu.SceneDraw;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuContext;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuTexture;
import indi.etern.musichud.client.ui.render.gpu.SceneIndexedDraw;
import indi.etern.musichud.client.ui.render.gpu.SceneMesh;
import indi.etern.musichud.client.ui.render.gpu.ScenePipelineKey;
import indi.etern.musichud.client.ui.render.gpu.SceneTextureFormat;
import indi.etern.musichud.client.ui.render.gpu.legacy.LegacyTextureIdRef;
import indi.etern.musichud.client.ui.render.gpu.legacy.LegacyTextureRef;
import indi.etern.musichud.client.ui.render.interop.SceneInterop;
import indi.etern.musichud.client.ui.render.pipeline.ScenePipeline;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.vulkanmod.render.texture.ImageUploadHelper;
import net.vulkanmod.vulkan.memory.MemoryTypes;
import net.vulkanmod.vulkan.memory.buffer.VertexBuffer;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Scene GPU backend for VulkanMod on 1.21.1. It keeps the project's UBO-based shader sources and
 * std140 packing, and only swaps the GL program/VAO/draw calls for VulkanMod pipelines.
 */
@SuppressWarnings("resource")
final class VulkanModSceneGpuContext implements SceneGpuContext {
    private final Map<ScenePipelineKey, VulkanModScenePipeline> pipelines =
            new EnumMap<>(ScenePipelineKey.class);
    private final List<VulkanModSceneTexture> textures = new ArrayList<>();
    private final List<VulkanModSceneMesh> meshes = new ArrayList<>();
    private VertexBuffer fullscreenVertices;
    private boolean closed;

    VulkanModSceneGpuContext() {
        RenderSystem.assertOnRenderThread();
        SceneInterop.install(VulkanInteropBackend.INSTANCE);
    }

    @Override
    public SceneGpuTexture createRenderTarget(String label, int width, int height,
                                              SceneTextureFormat format) {
        assertOpen();
        VulkanModSceneTexture texture = new VulkanModSceneTexture(width, height, format);
        textures.add(texture);
        RenderStats.targetCreated(label, (long) width * height * 4L);
        return texture;
    }

    @Override
    public SceneGpuTexture createNativeRenderTarget(String label, int width, int height,
                                                    SceneTextureFormat format) {
        return createRenderTarget(label, width, height, format);
    }

    @Override
    public ScenePipeline pipeline(ScenePipelineKey key, SceneTextureFormat targetFormat) {
        assertOpen();
        VulkanModScenePipeline existing = pipelines.get(key);
        if (existing != null) {
            return existing;
        }
        try {
            VulkanModScenePipeline created = VulkanModScenePipeline.create(key);
            pipelines.put(key, created);
            return created;
        } catch (RuntimeException | LinkageError failure) {
            MusicHud.getLogger(VulkanModSceneGpuContext.class)
                    .error("Failed to create Vulkan scene pipeline " + key, failure);
            throw failure;
        }
    }

    @Override
    public SceneMesh createMesh(String label, int vertexBytes, short[] indices) {
        assertOpen();
        VulkanModSceneMesh mesh = new VulkanModSceneMesh(vertexBytes, indices);
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
        VulkanModSceneTexture target = requireTexture(draw.target());
        VulkanModScenePipeline pipeline = requirePipeline(draw.pipeline());
        RenderStats.pass();
        // Dynamic textures upload asynchronously; submit the pending transfer buffer before the
        // draw samples it, otherwise the first frames (and layout transitions) see black.
        ImageUploadHelper.INSTANCE.submitCommands();
        target.bind();
        try {
            prepareState();
            pipeline.bind(draw.samplers(), draw.uniforms(), null);
            pipeline.bindPipeline();
            pipeline.drawFullscreen(fullscreenVertices());
        } finally {
            target.unbind();
        }
    }

    @Override
    public void drawIndexed(SceneIndexedDraw draw) {
        assertOpen();
        VulkanModSceneTexture target = requireTexture(draw.draw().target());
        VulkanModSceneMesh mesh = requireMesh(draw.mesh());
        VulkanModScenePipeline pipeline = requirePipeline(draw.draw().pipeline());
        RenderStats.pass();
        ImageUploadHelper.INSTANCE.submitCommands();
        target.bind();
        try {
            prepareState();
            pipeline.bind(draw.draw().samplers(), draw.draw().uniforms(), null);
            pipeline.bindPipeline();
            pipeline.drawIndexed(mesh);
        } finally {
            target.unbind();
        }
    }

    @Override
    public void clear(SceneGpuTexture target, int argb) {
        assertOpen();
        VulkanModSceneTexture texture = requireTexture(target);
        texture.bind();
        try {
            GlStateManager._clearColor(((argb >> 16) & 0xFF) / 255f,
                    ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f,
                    ((argb >>> 24) & 0xFF) / 255f);
            GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT, false);
        } finally {
            texture.unbind();
        }
    }

    @Override
    public PrecompileResult precompilePipelines() {
        assertOpen();
        Minecraft minecraft = Minecraft.getInstance();
        //noinspection ConstantValue
        if (minecraft == null || minecraft.getResourceManager() == null) {
            return PrecompileResult.NOT_READY;
        }
        ResourceLocation fullscreen = ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID,
                "shaders/core/fullscreen.vsh");
        if (minecraft.getResourceManager().getResource(fullscreen).isEmpty()) {
            return PrecompileResult.NOT_READY;
        }
        for (ScenePipelineKey key : ScenePipelineKey.values()) {
            pipeline(key, SceneTextureFormat.RGBA8);
        }
        return PrecompileResult.DONE;
    }

    @Override
    public void release(SceneGpuTexture texture) {
        if (!(texture instanceof VulkanModSceneTexture legacy)) return;
        textures.remove(legacy);
        RenderStats.targetReleased((long) legacy.width() * legacy.height() * 4L);
        legacy.close();
    }

    @Override
    public void release(SceneMesh mesh) {
        if (!(mesh instanceof VulkanModSceneMesh legacy)) return;
        meshes.remove(legacy);
        legacy.close();
    }

    @Override
    public @NotNull MainColorSource mainColorSource() {
        assertOpen();
        Minecraft minecraft = Minecraft.getInstance();
        var target = minecraft.getMainRenderTarget();
        return new MainColorSource(new VulkanTextureRef(target.getColorTextureId()),
                target.width, target.height, (int) minecraft.getWindow().getGuiScale());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (VulkanModSceneTexture texture : new ArrayList<>(textures)) texture.close();
        textures.clear();
        for (VulkanModSceneMesh mesh : new ArrayList<>(meshes)) mesh.close();
        meshes.clear();
        if (fullscreenVertices != null) {
            fullscreenVertices.scheduleFree();
            fullscreenVertices = null;
        }
        for (VulkanModScenePipeline pipeline : pipelines.values()) pipeline.close();
        pipelines.clear();
        SceneInterop.install(null);
    }

    private VertexBuffer fullscreenVertices() {
        if (fullscreenVertices == null) {
            ByteBuffer zeros = ByteBuffer.allocateDirect(3 * 12).order(ByteOrder.nativeOrder());
            // The triangle comes from gl_VertexIndex; the buffer only needs to exist.
            fullscreenVertices = new VertexBuffer(zeros.capacity(), MemoryTypes.GPU_MEM);
            fullscreenVertices.copyBuffer(zeros, zeros.capacity(), 0);
        }
        return fullscreenVertices;
    }

    private void prepareState() {
        GlStateManager._disableScissorTest();
        GlStateManager._disableBlend();
        GlStateManager._disableDepthTest();
        GlStateManager._disableCull();
    }

    private void assertOpen() {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("Scene GPU context is closed");
    }

    private static VulkanModSceneTexture requireTexture(SceneGpuTexture texture) {
        if (texture instanceof VulkanModSceneTexture legacy) return legacy;
        throw new IllegalArgumentException("Unsupported scene texture");
    }

    private static VulkanModSceneMesh requireMesh(SceneMesh mesh) {
        if (mesh instanceof VulkanModSceneMesh legacy) return legacy;
        throw new IllegalArgumentException("Unsupported scene mesh");
    }

    private static VulkanModScenePipeline requirePipeline(ScenePipeline pipeline) {
        if (pipeline instanceof VulkanModScenePipeline vulkan) return vulkan;
        throw new IllegalArgumentException("Unsupported scene pipeline");
    }

    static int textureId(SceneTextureRef ref) {
        if (ref instanceof VulkanModSceneTexture texture) return texture.textureId();
        if (ref instanceof VulkanTextureRef(int id1)) return id1;
        if (ref instanceof LegacyTextureRef(int id)) return id;
        if (ref instanceof LegacyTextureIdRef texture) return texture.textureId();
        throw new IllegalArgumentException("Unsupported scene texture reference");
    }
}
