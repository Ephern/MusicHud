package indi.etern.musichud.client.ui.hud.pipelines;

import indi.etern.musichud.MusicHud;

import java.util.Map;

public class HudRenderPipelines {
    // Uniform Buffer Object binding points for our custom uniforms
    // binding 2: MH position uniforms (MHBasePosition/MHAlbumPosition/MHProgressPosition)
    // binding 3: MHColor/Theme/ProgressStyle uniforms
    // binding 4: MHDynamicStatus
    // Note: ProjMat and ModelViewMat are plain uniforms from moj_import (not UBOs in 1.21.1)

    public static final HudShaderProgram BACKGROUND = HudShaderManager.getOrCreate(
            MusicHud.location("shaders/core/background.vsh"),
            MusicHud.location("shaders/core/background.fsh"),
            Map.of("MHBasePosition", 2, "MHNowPlayingThemeColor", 3, "MHDynamicStatus", 4)
    );

    public static final HudShaderProgram ROUNDED_ALBUM = HudShaderManager.getOrCreate(
            MusicHud.location("shaders/core/album_image.vsh"),
            MusicHud.location("shaders/core/album_image.fsh"),
            Map.of("MHAlbumPosition", 2, "MHDynamicStatus", 4)
    );

    public static final HudShaderProgram PROGRESS_BAR = HudShaderManager.getOrCreate(
            MusicHud.location("shaders/core/progress_bar.vsh"),
            MusicHud.location("shaders/core/progress_bar.fsh"),
            Map.of("MHProgressPosition", 2, "MHProgressStyle", 3, "MHDynamicStatus", 4)
    );
}
