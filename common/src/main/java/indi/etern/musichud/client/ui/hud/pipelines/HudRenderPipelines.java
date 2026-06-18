package indi.etern.musichud.client.ui.hud.pipelines;

import indi.etern.musichud.MusicHud;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class HudRenderPipelines {
    // Note: ProjMat and ModelViewMat are plain uniforms from moj_import (not UBOs in 1.21.1)
    public static final HudShaderProgram BACKGROUND = HudShaderManager.getOrCreate(
            MusicHud.location("shaders/core/background.vsh"),
            MusicHud.location("shaders/core/background.fsh"),
            List.of("MHBasePosition", "MHNowPlayingThemeColor", "MHDynamicStatus")
    );

    public static final HudShaderProgram ROUNDED_ALBUM = HudShaderManager.getOrCreate(
            MusicHud.location("shaders/core/album_image.vsh"),
            MusicHud.location("shaders/core/album_image.fsh"),
            List.of("MHAlbumPosition", "MHDynamicStatus")
    );

    public static final HudShaderProgram PROGRESS_BAR = HudShaderManager.getOrCreate(
            MusicHud.location("shaders/core/progress_bar.vsh"),
            MusicHud.location("shaders/core/progress_bar.fsh"),
            List.of("MHProgressPosition", "MHProgressStyle", "MHDynamicStatus")
    );
}
