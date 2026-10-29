package indi.etern.musichud.client.ui.render.pipeline;

import indi.etern.musichud.MusicHud;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class SceneRenderPipelines {
    public static final ScenePipeline FLUID_BACKGROUND = HudShaderManager.getOrCreate(
            "fluid_background",
            shader("background.vsh"),
            shader("fluid_background.fsh"),
            List.of("MHPosition", "MHBackgroundAdjust"));

    public static final ScenePipeline SOLID_BACKGROUND = HudShaderManager.getOrCreate(
            "solid_background",
            shader("background.vsh"),
            shader("solid_background.fsh"),
            List.of("MHPosition", "MHSolidColor"));

    public static final ScenePipeline ROUNDED_ALBUM = HudShaderManager.getOrCreate(
            "album_image",
            shader("album_image.vsh"),
            shader("album_image.fsh"),
            List.of("MHPosition", "MHDynamicStatus"));

    public static final ScenePipeline PROGRESS_BAR = HudShaderManager.getOrCreate(
            "progress_bar",
            shader("progress_bar.vsh"),
            shader("progress_bar.fsh"),
            List.of("MHPosition", "MHProgressStyle", "MHDynamicStatus"));

    private SceneRenderPipelines() {
    }

    private static ResourceLocation shader(String file) {
        return ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "shaders/core/" + file);
    }
}
