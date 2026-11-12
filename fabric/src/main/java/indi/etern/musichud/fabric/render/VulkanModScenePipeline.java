package indi.etern.musichud.fabric.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.gpu.ScenePipelineKey;
import indi.etern.musichud.client.ui.render.gpu.SceneSamplerBinding;
import indi.etern.musichud.client.ui.render.gpu.SceneUniformBinding;
import indi.etern.musichud.client.ui.render.pipeline.HudPipelineSpec;
import indi.etern.musichud.client.ui.render.pipeline.ScenePipeline;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.ManualUBO;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A scene/HUD pipeline compiled from the same GLSL sources the GL backend uses. */
final class VulkanModScenePipeline implements ScenePipeline {
    private final String name;
    private final GraphicsPipeline pipeline;
    private final Map<String, ManualUBO> ubos;
    private final Map<String, ByteBuffer> uniformBuffers = new HashMap<>();
    private final @Nullable VulkanShaderRewriter.PlainUbo plainUbo;
    private ByteBuffer plainBuffer;

    private VulkanModScenePipeline(String name, GraphicsPipeline pipeline,
                                   Map<String, ManualUBO> ubos,
                                   @Nullable VulkanShaderRewriter.PlainUbo plainUbo) {
        this.name = name;
        this.pipeline = pipeline;
        this.ubos = ubos;
        this.plainUbo = plainUbo;
    }

    static VulkanModScenePipeline create(ScenePipelineKey key) {
        String name = "scene_" + key.name().substring("SCENE_".length()).toLowerCase();
        boolean mesh = key == ScenePipelineKey.SCENE_COVER_WARP_PROCEDURAL;
        String vertexName = mesh ? name + ".vsh" : "fullscreen.vsh";
        String fragmentName = name + ".fsh";
        VertexFormat format = mesh ? DefaultVertexFormat.POSITION_TEX : DefaultVertexFormat.POSITION;
        Map<String, Integer> attributes = mesh
                ? Map.of("Position", 0, "UV0", 1)
                : Map.of();
        return create(name, vertexName, fragmentName, format, attributes);
    }

    static VulkanModScenePipeline create(HudPipelineSpec spec) {
        return create(spec.name(), path(spec.vertexShader()), path(spec.fragmentShader()),
                DefaultVertexFormat.POSITION_COLOR, Map.of("Position", 0, "Color", 1));
    }

    private static VulkanModScenePipeline create(String name, String vertexFile,
                                                 String fragmentFile, VertexFormat format,
                                                 Map<String, Integer> attributes) {
        String vertex = readShader(vertexFile);
        String fragment = readShader(fragmentFile);
        if (vertex.contains("texCoord = Position.xy;")) {
            // VulkanMod renders through an inverted viewport, which mirrors every offscreen pass.
            // Flip the sampled coordinate so a pass stays an identity copy, as it is in OpenGL.
            vertex = vertex.replace("texCoord = Position.xy;",
                    "texCoord = vec2(Position.x, 1.0 - Position.y);");
        } else {
            vertex = vertex.replace("texCoord = p;", "texCoord = vec2(p.x, 1.0 - p.y);");
        }
        if ("scene_capture".equals(name)) {
            // The capture shader mirrors the source because OpenGL render targets are stored
            // bottom-up; VulkanMod's main colour target is top-down, so the copy has to stay 1:1
            // for a sub-region as well, not just for the full screen.
            fragment = removeCaptureMirror(fragment);
        }
        VulkanShaderRewriter.Result rewritten = VulkanShaderRewriter.rewrite(vertex, fragment,
                attributes);

        Pipeline.Builder builder = new Pipeline.Builder(format, "musichud_" + name);
        builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, rewritten.vertexSource());
        builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, rewritten.fragmentSource());

        Map<String, ManualUBO> ubos = new HashMap<>();
        List<net.vulkanmod.vulkan.shader.descriptor.UBO> descriptors = new ArrayList<>();
        for (VulkanShaderRewriter.Ubo ubo : rewritten.ubos()) {
            ManualUBO descriptor = new ManualUBO(ubo.binding(),
                    VK10.VK_SHADER_STAGE_ALL_GRAPHICS, (ubo.size() + 3) / 4);
            descriptors.add(descriptor);
            ubos.put(ubo.name(), descriptor);
        }

        if (rewritten.plainUbo() != null) {
            VulkanShaderRewriter.PlainUbo plain = rewritten.plainUbo();
            ManualUBO descriptor = new ManualUBO(plain.binding(),
                    VK10.VK_SHADER_STAGE_ALL_GRAPHICS, (plain.size() + 3) / 4);
            descriptors.add(descriptor);
            ubos.put(plain.name(), descriptor);
        }

        List<ImageDescriptor> samplers = new ArrayList<>();
        for (VulkanShaderRewriter.Sampler sampler : rewritten.samplers()) {
            if (sampler.imageIdx() < 0) {
                throw new IllegalArgumentException("Unsupported sampler name: " + sampler.name());
            }
            samplers.add(new ImageDescriptor(sampler.binding(), "sampler2D", sampler.name(),
                    sampler.imageIdx(), VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER));
        }

        builder.setUniforms(descriptors, samplers);
        GraphicsPipeline graphicsPipeline = builder.createGraphicsPipeline();
        MusicHud.getLogger(VulkanModScenePipeline.class)
                .debug("Compiled Vulkan scene pipeline {}", name);
        return new VulkanModScenePipeline(name, graphicsPipeline, ubos, rewritten.plainUbo());
    }

    @Override
    public String name() {
        return name;
    }

    GraphicsPipeline graphicsPipeline() {
        return pipeline;
    }

    void bind(List<SceneSamplerBinding> samplers, List<SceneUniformBinding> uniforms,
              @Nullable MatrixSupplier plainMatrices) {
        bindSamplers(samplers);
        bindUniforms(uniforms);
        if (plainMatrices != null) {
            bindPlainUniforms(plainMatrices);
        }
    }

    void bindSamplers(List<SceneSamplerBinding> samplers) {
        for (SceneSamplerBinding binding : samplers) {
            int textureId = VulkanModSceneGpuContext.textureId(binding.texture());
            net.vulkanmod.gl.VkGlTexture texture =
                    net.vulkanmod.gl.VkGlTexture.getTexture(textureId);
            if (texture == null || texture.getVulkanImage() == null) {
                MusicHud.getLogger(VulkanModScenePipeline.class).warn(
                        "Scene sampler {} has no Vulkan image (texture id {})",
                        binding.name(), textureId);
                throw new IllegalStateException("Scene sampler has no Vulkan image: " + textureId);
            }
            int imageIdx = net.vulkanmod.vulkan.texture.VTextureSelector
                    .getTextureIdx(binding.name());
            if (imageIdx < 0) {
                throw new IllegalArgumentException("Unsupported sampler: " + binding.name());
            }
            net.vulkanmod.vulkan.texture.VTextureSelector.bindTexture(imageIdx,
                    texture.getVulkanImage());
        }
    }

    void bindUniforms(List<SceneUniformBinding> uniforms) {
        for (SceneUniformBinding binding : uniforms) {
            ManualUBO ubo = ubos.get(binding.name());
            if (ubo == null) {
                continue;
            }
            SceneUniform uniform = binding.uniform();
            int size = uniform.getUBOSize();
            ByteBuffer buffer = uniformBuffers.get(binding.name());
            if (buffer == null || buffer.capacity() < size) {
                buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
                uniformBuffers.put(binding.name(), buffer);
            }
            buffer.clear();
            uniform.write(buffer);
            buffer.flip();
            ubo.setSrc(MemoryUtil.memAddress(buffer), size);
            ubo.setUpdate(true);
        }
    }

    private void bindPlainUniforms(MatrixSupplier supplier) {
        if (plainUbo == null) {
            return;
        }
        int size = plainUbo.size();
        if (plainBuffer == null || plainBuffer.capacity() < size) {
            plainBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
        }
        plainBuffer.clear();
        float[] scratch = new float[16];
        for (VulkanShaderRewriter.Field field : plainUbo.fields()) {
            switch (field.name()) {
                case "ProjMat" -> writeMatrix(plainBuffer, supplier.projection(), scratch);
                case "ModelViewMat" -> writeMatrix(plainBuffer, supplier.modelView(), scratch);
                default -> throw new IllegalArgumentException(
                        "Unsupported generated plain uniform: " + field.name());
            }
        }
        plainBuffer.flip();
        ManualUBO ubo = ubos.get(plainUbo.name());
        if (ubo != null) {
            ubo.setSrc(MemoryUtil.memAddress(plainBuffer), size);
            ubo.setUpdate(true);
        }
    }

    private static void writeMatrix(ByteBuffer buffer, org.joml.Matrix4fc matrix, float[] scratch) {
        matrix.get(scratch);
        for (float value : scratch) {
            buffer.putFloat(value);
        }
    }

    void bindPipeline() {
        Renderer.getInstance().bindGraphicsPipeline(pipeline);
        pipeline.bindDescriptorSets(Renderer.getCommandBuffer(), Renderer.getCurrentFrame());
    }

    void drawFullscreen(net.vulkanmod.vulkan.memory.buffer.VertexBuffer vertices) {
        Renderer.getDrawer().draw(vertices, 3);
    }

    void drawIndexed(VulkanModSceneMesh mesh) {
        Renderer.getDrawer().drawIndexed(mesh.vertices(), mesh.indices(), mesh.indexCount());
    }

    void close() {
        pipeline.cleanUp();
    }

    private static String removeCaptureMirror(String fragment) {
        String patched = fragment
                .replace("clamp(size.y - 1 - int(floor(srcY)), 0, size.y - 1)",
                        "clamp(int(floor(srcY)), 0, size.y - 1)")
                .replace("vec2(center.x / size.x, 1.0 - center.y / size.y)",
                        "vec2(center.x / size.x, center.y / size.y)");
        if (patched.equals(fragment)) {
            MusicHud.getLogger(VulkanModScenePipeline.class)
                    .warn("scene_capture.fsh no longer mirrors the source; the patch is stale");
        }
        return patched;
    }

    private static String readShader(String file) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID,
                "shaders/core/" + file);
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager()
                .getResource(location);
        if (resource.isEmpty()) {
            throw new IllegalStateException("Shader resource missing: " + location);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.get().open(), StandardCharsets.UTF_8))) {
            StringBuilder source = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                source.append(line).append('\n');
            }
            return source.toString();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read shader resource: " + location, failure);
        }
    }

    private static String path(ResourceLocation location) {
        String path = location.getPath();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    interface MatrixSupplier {
        org.joml.Matrix4fc projection();

        org.joml.Matrix4fc modelView();
    }
}
