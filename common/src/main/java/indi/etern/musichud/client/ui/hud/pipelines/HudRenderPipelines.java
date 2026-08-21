package indi.etern.musichud.client.ui.hud.pipelines;

import indi.etern.musichud.MusicHud;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public class HudRenderPipelines {

    // Note: ProjMat and ModelViewMat are plain uniforms from moj_import (not UBOs in 1.21.1)
    public static final HudShaderProgram BACKGROUND = HudShaderManager.getOrCreate(
            "background",
            ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "shaders/core/background.vsh"),
            ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "shaders/core/background.fsh"),
            List.of("MHPosition", "MHNowPlayingThemeColor", "MHDynamicStatus")
    );

    public static final HudShaderProgram ROUNDED_ALBUM = HudShaderManager.getOrCreate(
            "album",
            ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "shaders/core/album_image.vsh"),
            ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "shaders/core/album_image.fsh"),
            List.of("MHPosition", "MHDynamicStatus")
    );

    public static final HudShaderProgram PROGRESS_BAR = HudShaderManager.getOrCreate(
            "progress",
            ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "shaders/core/progress_bar.vsh"),
            ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, "shaders/core/progress_bar.fsh"),
            List.of("MHPosition", "MHProgressStyle", "MHDynamicStatus")
    );
}
