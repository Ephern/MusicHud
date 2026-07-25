package indi.etern.musichud.client.ui.hud.pipelines;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import indi.etern.musichud.MusicHud;
import net.minecraft.resources.ResourceLocation;

public class HudRenderPipelines {
    public static final RenderPipeline.Snippet MATRICES_PROJECTION_SNIPPET =
            RenderPipeline.builder()
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .buildSnippet();

    public static final RenderPipeline BACKGROUND;

    public static final RenderPipeline ROUNDED_ALBUM;

    public static final RenderPipeline PROGRESS_BAR;

    static {
        BACKGROUND = RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "pipeline/background"))
                .withVertexShader(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "core/background"))
                .withFragmentShader(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "core/background"))
                .withUniform("MHBasePosition", UniformType.UNIFORM_BUFFER)
                .withUniform("MHNowPlayingThemeColor", UniformType.UNIFORM_BUFFER)
                .withUniform("MHDynamicStatus", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .build();
        ROUNDED_ALBUM = RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "pipeline/album_image"))
                .withVertexShader(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "core/album_image"))
                .withFragmentShader(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "core/album_image"))
                .withUniform("MHAlbumPosition", UniformType.UNIFORM_BUFFER)
                .withUniform("MHDynamicStatus", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withSampler("Sampler1")
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .build();
        PROGRESS_BAR = RenderPipeline.builder(MATRICES_PROJECTION_SNIPPET)
                .withLocation(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "pipeline/progress_bar"))
                .withVertexShader(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "core/progress_bar"))
                .withFragmentShader(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "core/progress_bar"))
                .withUniform("MHProgressPosition", UniformType.UNIFORM_BUFFER)
                .withUniform("MHProgressStyle", UniformType.UNIFORM_BUFFER)
                .withUniform("MHDynamicStatus", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .build();
    }
}
