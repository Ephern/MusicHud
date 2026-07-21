package indi.etern.musichud.client.ui.hud.pipelines;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import indi.etern.musichud.MusicHud;

public class HudRenderPipelines {
    public static final RenderPipeline.Snippet MATRICES_PROJECTION_SNIPPET =
            RenderPipeline.builder()
                    .withBindGroupLayout(BindGroupLayout.builder().withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER).build())
                    .withBindGroupLayout(BindGroupLayout.builder().withUniform("Projection", UniformType.UNIFORM_BUFFER).build())
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .buildSnippet();

    public static final RenderPipeline BACKGROUND = RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
            .withLocation(MusicHud.location("pipeline/background"))
            .withVertexShader(MusicHud.location("core/background"))
            .withFragmentShader(MusicHud.location("core/background"))
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHBasePosition", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHNowPlayingThemeColor", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHDynamicStatus", UniformType.UNIFORM_BUFFER).build())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    public static final RenderPipeline ROUNDED_ALBUM = RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
            .withLocation(MusicHud.location("pipeline/album_image"))
            .withVertexShader(MusicHud.location("core/album_image"))
            .withFragmentShader(MusicHud.location("core/album_image"))
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHAlbumPosition", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHDynamicStatus", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler0").build())
            .withBindGroupLayout(BindGroupLayout.builder().withSampler("Sampler1").build())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    public static final RenderPipeline PROGRESS_BAR = RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
            .withLocation(MusicHud.location("pipeline/progress_bar"))
            .withVertexShader(MusicHud.location("core/progress_bar"))
            .withFragmentShader(MusicHud.location("core/progress_bar"))
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHProgressPosition", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHProgressStyle", UniformType.UNIFORM_BUFFER).build())
            .withBindGroupLayout(BindGroupLayout.builder().withUniform("MHDynamicStatus", UniformType.UNIFORM_BUFFER).build())
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();
}
