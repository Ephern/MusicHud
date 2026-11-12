package indi.etern.musichud.fabric.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.RenderLayer;
import indi.etern.musichud.client.ui.render.SceneElementRenderState;
import indi.etern.musichud.client.ui.render.ScenePipelineRenderer;
import indi.etern.musichud.client.ui.render.gpu.SceneSamplerBinding;
import indi.etern.musichud.client.ui.render.gpu.SceneUniformBinding;
import indi.etern.musichud.client.ui.render.pipeline.HudPipelineSpec;
import indi.etern.musichud.client.ui.render.pipeline.ScenePipeline;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureSetup;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;

/**
 * VulkanMod HUD renderer. It reuses the checked-in UBO shaders and uploads the same std140
 * uniforms as the GL path; only the final draw goes through a Vulkan pipeline.
 */
final class VulkanModHudRenderer implements ScenePipelineRenderer {
    private static final Matrix4f IDENTITY = new Matrix4f();
    private final Map<String, VulkanModScenePipeline> pipelines = new HashMap<>();

    @Override
    public void submit(RenderLayer owner, SceneElementRenderState state) {
        VulkanModScenePipeline pipeline = resolve(state.pipeline());
        if (pipeline == null) {
            return;
        }
        try {
            GlStateManager._disableDepthTest();
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA,
                    GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager._disableCull();

            SceneTextureSetup textures = state.textureSetup();
            List<SceneSamplerBinding> samplers = new ArrayList<>(2);
            if (textures.primary() != null) {
                samplers.add(new SceneSamplerBinding("Sampler0", textures.primary()));
            }
            if (textures.secondary() != null) {
                samplers.add(new SceneSamplerBinding("Sampler1", textures.secondary()));
            }

            List<SceneUniformBinding> uniforms = new ArrayList<>();
            SceneUniform[] source = state.uniforms();
            if (source != null) {
                for (SceneUniform uniform : source) {
                    if (uniform != null) {
                        uniforms.add(new SceneUniformBinding(uniform.getUBOName(), uniform));
                    }
                }
            }

            Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
            // Minecraft's GUI renders at -11000, the middle of the ortho depth range, which is
            // also the only place inside Vulkan's [0, 1] clip depth. -1000 (the OpenGL near plane)
            // would be clipped away.
            Matrix4f modelView = new Matrix4f().translate(0, 0, -11000);
            net.vulkanmod.render.texture.ImageUploadHelper.INSTANCE.submitCommands();
            pipeline.bind(samplers, uniforms, new VulkanModScenePipeline.MatrixSupplier() {
                @Override
                public Matrix4fc projection() {
                    return projection;
                }

                @Override
                public Matrix4fc modelView() {
                    return modelView;
                }
            });
            pipeline.bindPipeline();

            try (MeshData mesh = buildQuad(state.pose(), state.width(), state.height())) {
                RendererDraw.draw(mesh);
            }
        } catch (Throwable failure) {
            MusicHud.getLogger(VulkanModHudRenderer.class)
                    .error("While drawing a Vulkan HUD element", failure);
        }
    }

    private VulkanModScenePipeline resolve(ScenePipeline pipeline) {
        if (!(pipeline instanceof HudPipelineSpec spec)) {
            return null;
        }
        VulkanModScenePipeline existing = pipelines.get(spec.name());
        if (existing != null) {
            return existing;
        }
        VulkanModScenePipeline created = VulkanModScenePipeline.create(spec);
        pipelines.put(spec.name(), created);
        return created;
    }

    private static MeshData buildQuad(Matrix3x2f pose, float width, float height) {
        float left = -width / 2f;
        float right = width / 2f;
        float top = -height / 2f;
        float bottom = height / 2f;

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
                DefaultVertexFormat.POSITION_COLOR);

        float x1 = pose.m00 * left + pose.m10 * bottom;
        float y1 = pose.m01 * left + pose.m11 * bottom;
        float x2 = pose.m00 * right + pose.m10 * bottom;
        float y2 = pose.m01 * right + pose.m11 * bottom;
        float x3 = pose.m00 * right + pose.m10 * top;
        float y3 = pose.m01 * right + pose.m11 * top;
        float x4 = pose.m00 * left + pose.m10 * top;
        float y4 = pose.m01 * left + pose.m11 * top;

        builder.addVertex(IDENTITY, x1, y1, 0).setColor(-1);
        builder.addVertex(IDENTITY, x2, y2, 0).setColor(-1);
        builder.addVertex(IDENTITY, x3, y3, 0).setColor(-1);
        builder.addVertex(IDENTITY, x1, y1, 0).setColor(-1);
        builder.addVertex(IDENTITY, x3, y3, 0).setColor(-1);
        builder.addVertex(IDENTITY, x4, y4, 0).setColor(-1);

        return builder.buildOrThrow();
    }

    @Override
    public void close() {
        for (VulkanModScenePipeline pipeline : pipelines.values()) {
            pipeline.close();
        }
        pipelines.clear();
    }

    /** Isolated so the renderer can be loaded without pulling in the rest of the Vulkan bridge. */
    private static final class RendererDraw {
        private static void draw(MeshData mesh) {
            com.mojang.blaze3d.vertex.VertexFormat format = DefaultVertexFormat.POSITION_COLOR;
            net.vulkanmod.vulkan.Renderer.getDrawer().draw(mesh.vertexBuffer(),
                    VertexFormat.Mode.TRIANGLES, format, 6);
        }
    }
}
