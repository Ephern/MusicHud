package indi.etern.musichud.client.ui.hud.pipelines;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.*;
import indi.etern.musichud.MusicHud;
import net.minecraft.resources.Identifier;

public class HudRenderPipelines {
    public static final RenderPipeline.Snippet MATRICES_PROJECTION_SNIPPET =
            RenderPipeline.builder()
                    .withBindGroupLayout(BindGroupLayout.builder().withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER).build())
                    .withBindGroupLayout(BindGroupLayout.builder().withUniform("Projection", UniformType.UNIFORM_BUFFER).build())
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .buildSnippet();

    public static final HudPipeline BACKGROUND;

    public static final HudPipeline ROUNDED_ALBUM;

    public static final HudPipeline PROGRESS_BAR;

    static {
        BACKGROUND = wrap("background", RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "pipeline/background"))
                .withVertexShader(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "core/background"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "core/background"))
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHPosition", UniformType.UNIFORM_BUFFER).build())
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHNowPlayingThemeColor", UniformType.UNIFORM_BUFFER).build())
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHDynamicStatus", UniformType.UNIFORM_BUFFER).build())
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .build());
        ROUNDED_ALBUM = wrap("album_image", RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "pipeline/album_image"))
                .withVertexShader(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "core/album_image"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "core/album_image"))
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHPosition", UniformType.UNIFORM_BUFFER).build())
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHDynamicStatus", UniformType.UNIFORM_BUFFER).build())
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER).build())
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("Sampler1", UniformType.COMBINED_IMAGE_SAMPLER).build())
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

    private static HudPipeline wrap(String name, RenderPipeline pipeline) {
        return new RenderPipelineHudPipeline(name, pipeline);
    }
}
