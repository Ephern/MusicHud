package indi.etern.musichud.client.ui.hud.presentation;

import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.utils.ui.Transitionable;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.function.Supplier;

@Getter
@Setter
public final class HudPresentationModel {
    private volatile String title = "";
    private volatile String artistsAndAlbum = "";
    private volatile String playTime = "";
    private volatile StreamAudioPlayer.Status audioStatus = StreamAudioPlayer.Status.IDLE;
    private volatile int statusRevision;
    private volatile Supplier<String> playerSkinSupplier;
    private volatile float hudAlpha = 1f;
    private volatile boolean idle = true;
    private volatile float progressRate;
    private volatile Duration playedDuration;
    private volatile Duration musicDuration;
    private volatile Transitionable<BackgroundData> background;
}
