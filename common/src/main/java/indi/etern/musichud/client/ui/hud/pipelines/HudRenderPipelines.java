package indi.etern.musichud.client.ui.hud.pipelines;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import indi.etern.musichud.MusicHud;
import net.minecraft.resources.Identifier;

public class HudRenderPipelines {
    public static final RenderPipeline.Snippet MATRICES_PROJECTION_SNIPPET =
            RenderPipeline.builder()
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
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
                .withUniform("MHPosition", UniformType.UNIFORM_BUFFER)
                .withUniform("MHNowPlayingThemeColor", UniformType.UNIFORM_BUFFER)
                .withUniform("MHDynamicStatus", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .build());
        ROUNDED_ALBUM = wrap("album_image", RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "pipeline/album_image"))
                .withVertexShader(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "core/album_image"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "core/album_image"))
                .withUniform("MHPosition", UniformType.UNIFORM_BUFFER)
                .withUniform("MHDynamicStatus", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withSampler("Sampler1")
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .build());
        PROGRESS_BAR = wrap("progress_bar", RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "pipeline/progress_bar"))
                .withVertexShader(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "core/progress_bar"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "core/progress_bar"))
                .withUniform("MHPosition", UniformType.UNIFORM_BUFFER)
                .withUniform("MHProgressStyle", UniformType.UNIFORM_BUFFER)
                .withUniform("MHDynamicStatus", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .build());
    }

    private static HudPipeline wrap(String name, RenderPipeline pipeline) {
        return new RenderPipelineHudPipeline(name, pipeline);
    }
}
