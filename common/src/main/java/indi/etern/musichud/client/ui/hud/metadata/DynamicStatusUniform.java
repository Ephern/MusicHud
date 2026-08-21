package indi.etern.musichud.client.ui.hud.metadata;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.ui.hud.pipelines.HudUniform;
import indi.etern.musichud.client.ui.hud.pipelines.Std140Sizes;
import indi.etern.musichud.client.ui.hud.pipelines.Std140Writer;
import indi.etern.musichud.client.utils.ui.Transitionable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@EqualsAndHashCode
public class DynamicStatusUniform implements HudUniform {
    private static volatile DynamicStatusUniform instance;
    @Getter
    @Setter
    Transitionable<?> transitionable;
    public static final int UBO_SIZE = Std140Sizes.calc().putVec4().align(16).get();
    private static final NowPlayingInfo NOW_PLAYING_INFO = NowPlayingInfo.getInstance();

    public static DynamicStatusUniform getInstance() {
        if (instance == null) {
            synchronized (DynamicStatusUniform.class) {
                if (instance == null) {
                    instance = new DynamicStatusUniform();
                }
            }
        }
        return instance;
    }
    private DynamicStatusUniform() {}

    @Override
    public String getUBOName() {
        return "MHDynamicStatus";
    }

    @Override
    public int getUBOSize() {
        return UBO_SIZE;
    }

    @Override
    public void write(Std140Writer builder) {
        builder.putVec4(
                (float) MusicHud.getRunningMillis() / 1000,
                NOW_PLAYING_INFO.getProgressRate(),
                transitionable == null ? 0 : transitionable.getProgress(),
                0
        );
    }

    @Override
    public boolean shouldUseBuffer(HudUniform lastBuffered) {
        return false;//always update
    }
}
