package indi.etern.musichud.client.ui.render.pipeline;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.VanillaScenePipeline;
import net.minecraft.resources.ResourceLocation;

public class SceneRenderPipelines {
    public static final RenderPipeline.Snippet MATRICES_PROJECTION_SNIPPET =
            RenderPipeline.builder()
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .buildSnippet();

    public static final ScenePipeline SOLID_BACKGROUND;
    public static final ScenePipeline FLUID_BACKGROUND;

    public static final ScenePipeline ROUNDED_ALBUM;

    public static final ScenePipeline PROGRESS_BAR;

    public static final RenderPipeline GRADIENT_TEXT = RenderPipeline.builder()
            .withLocation(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "pipeline/gradient_text"))
            .withVertexShader(ResourceLocation.withDefaultNamespace("core/rendertype_text_intensity"))
            .withFragmentShader(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "core/gradient_text"))
            .withSampler("Sampler0")
            .withSampler("Sampler2")
            .withUniform("Fog", UniformType.UNIFORM_BUFFER)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS)
            .build();

    static {
        FLUID_BACKGROUND = wrap("fluid_background", RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "pipeline/fluid_background"))
                .withVertexShader(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "core/background"))
                .withFragmentShader(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "core/fluid_background"))
                .withUniform("MHPosition", UniformType.UNIFORM_BUFFER)
                .withUniform("MHBackgroundAdjust", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS).build());
        SOLID_BACKGROUND = wrap("solid_background", RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "pipeline/solid_background"))
                .withVertexShader(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "core/background"))
                .withFragmentShader(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "core/solid_background"))
                .withUniform("MHPosition", UniformType.UNIFORM_BUFFER)
                .withUniform("MHSolidColor", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .build());
        ROUNDED_ALBUM = wrap("album_image", RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "pipeline/album_image"))
                .withVertexShader(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "core/album_image"))
                .withFragmentShader(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "core/album_image"))
                .withUniform("MHPosition", UniformType.UNIFORM_BUFFER)
                .withUniform("MHDynamicStatus", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withSampler("Sampler1")
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .build());
        PROGRESS_BAR = wrap("progress_bar", RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "pipeline/progress_bar"))
                .withVertexShader(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "core/progress_bar"))
                .withFragmentShader(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "core/progress_bar"))
                .withUniform("MHPosition", UniformType.UNIFORM_BUFFER)
                .withUniform("MHProgressStyle", UniformType.UNIFORM_BUFFER)
                .withUniform("MHDynamicStatus", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .build());
    }

    private static ScenePipeline wrap(String name, RenderPipeline pipeline) {
        return new VanillaScenePipeline(name, pipeline);
    }
}
