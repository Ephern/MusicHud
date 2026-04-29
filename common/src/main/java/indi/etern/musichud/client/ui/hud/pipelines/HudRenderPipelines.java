package indi.etern.musichud.client.ui.hud.pipelines;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import indi.etern.musichud.MusicHud;

public class HudRenderPipelines {
    public static final RenderPipeline.Snippet MATRICES_PROJECTION_SNIPPET =
            RenderPipeline.builder()
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .buildSnippet();

    public static final RenderPipeline BACKGROUND = RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
            .withLocation(MusicHud.location("pipeline/background"))
            .withVertexShader(MusicHud.location("core/background"))
            .withFragmentShader(MusicHud.location("core/background"))
            .withUniform("MHBasePosition", UniformType.UNIFORM_BUFFER)
            .withUniform("MHNowPlayingThemeColor", UniformType.UNIFORM_BUFFER)
            .withUniform("MHDynamicStatus", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .build();

    public static final RenderPipeline ROUNDED_ALBUM = RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
            .withLocation(MusicHud.location("pipeline/album_image"))
            .withVertexShader(MusicHud.location("core/album_image"))
            .withFragmentShader(MusicHud.location("core/album_image"))
            .withUniform("MHAlbumPosition", UniformType.UNIFORM_BUFFER)
            .withUniform("MHDynamicStatus", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .build();

    public static final RenderPipeline PROGRESS_BAR = RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
            .withLocation(MusicHud.location("pipeline/progress_bar"))
            .withVertexShader(MusicHud.location("core/progress_bar"))
            .withFragmentShader(MusicHud.location("core/progress_bar"))
            .withUniform("MHProgressPosition", UniformType.UNIFORM_BUFFER)
            .withUniform("MHProgressStyle", UniformType.UNIFORM_BUFFER)
            .withUniform("MHDynamicStatus", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .build();
}
