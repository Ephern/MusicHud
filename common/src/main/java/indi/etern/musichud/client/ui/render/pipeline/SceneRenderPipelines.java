package indi.etern.musichud.client.ui.render.pipeline;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.VanillaScenePipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

public class SceneRenderPipelines {
    public static final RenderPipeline.Snippet MATRICES_PROJECTION_SNIPPET =
            RenderPipeline.builder()
                    .withBindGroupLayout(BindGroupLayout.builder().withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER).build())
                    .withBindGroupLayout(BindGroupLayout.builder().withUniform("Projection", UniformType.UNIFORM_BUFFER).build())
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .buildSnippet();

    public static final ScenePipeline SOLID_BACKGROUND;
    public static final ScenePipeline FLUID_BACKGROUND;

    public static final ScenePipeline ROUNDED_ALBUM;

    public static final ScenePipeline PROGRESS_BAR;

    /**
     * Direct-mask gradient text: vanilla's text vertex shader with our fragment shader. The bind
     * groups mirror vanilla's {@code WORLD_TEXT_SNIPPET} (globals, matrices, font atlas, fog,
     * lightmap) because that vertex shader samples the lightmap and writes the fog distances our
     * fragment shader consumes.
     */
    public static final RenderPipeline GRADIENT_TEXT = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "pipeline/gradient_text"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
            .withVertexShader(Identifier.withDefaultNamespace("core/text"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "core/gradient_text"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    static {
        FLUID_BACKGROUND = wrap("fluid_background", RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "pipeline/fluid_background"))
                .withVertexShader(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "core/background"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "core/fluid_background"))
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHPosition", UniformType.UNIFORM_BUFFER).build())
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHBackgroundAdjust", UniformType.UNIFORM_BUFFER).build())
                .withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler0").build())
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .build());
        SOLID_BACKGROUND = wrap("solid_background", RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "pipeline/solid_background"))
                .withVertexShader(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "core/background"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "core/solid_background"))
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHPosition", UniformType.UNIFORM_BUFFER).build())
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHSolidColor", UniformType.UNIFORM_BUFFER).build())
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .build());
        ROUNDED_ALBUM = wrap("album_image", RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "pipeline/album_image"))
                .withVertexShader(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "core/album_image"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "core/album_image"))
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHPosition", UniformType.UNIFORM_BUFFER).build())
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHDynamicStatus", UniformType.UNIFORM_BUFFER).build())
                .withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler0").build())
                .withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler1").build())
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .build());
        PROGRESS_BAR = wrap("progress_bar", RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "pipeline/progress_bar"))
                .withVertexShader(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "core/progress_bar"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "core/progress_bar"))
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHPosition", UniformType.UNIFORM_BUFFER).build())
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHProgressStyle", UniformType.UNIFORM_BUFFER).build())
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHDynamicStatus", UniformType.UNIFORM_BUFFER).build())
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .build());
    }

    private static ScenePipeline wrap(String name, RenderPipeline pipeline) {
        return new VanillaScenePipeline(name, pipeline);
    }
}
