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
                    .buildSnippet();

    public static final RenderPipeline BACKGROUND = RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
            .withLocation(MusicHud.location("pipeline/background"))
            .withVertexShader(MusicHud.location("core/background"))
            .withFragmentShader(MusicHud.location("core/background"))
            .withUniform("HudBackgroundParams", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .build();

    public static final RenderPipeline ROUNDED_ALBUM = RenderPipeline.builder()
            .withLocation(MusicHud.location("pipeline/album_image"))
            .withVertexShader(MusicHud.location("core/album_image"))
            .withFragmentShader(MusicHud.location("core/album_image"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("HudAlbumParams", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
            .build();

    public static final RenderPipeline PROGRESS_BAR = RenderPipeline.builder()
            .withLocation(MusicHud.location("pipeline/progress_bar"))
            .withVertexShader(MusicHud.location("core/progress_bar"))
            .withFragmentShader(MusicHud.location("core/progress_bar"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("HudProgressParams", UniformType.UNIFORM_BUFFER)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .build();
}