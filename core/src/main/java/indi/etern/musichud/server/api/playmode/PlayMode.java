package indi.etern.musichud.server.api.playmode;

import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.PusherInfo;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Sampling strategy of an idle play source. All mode-specific behavior
 * (availability, track selection, per-source state, lifecycle) lives behind
 * this enum; callers never branch on the mode themselves.
 */
public enum PlayMode {
    SEQUENTIAL(new SequentialPlayModeStrategy()),
    RANDOM(new RandomPlayModeStrategy()),
    INTELLIGENT(new IntelligentPlayModeStrategy());

    private final PlayModeStrategy strategy;

    PlayMode(PlayModeStrategy strategy) {
        this.strategy = strategy;
    }

    /** INTELLIGENT supports playlists only. */
    public boolean supports(MusicCollection collection) {
        return strategy.supports(collection);
    }

    public boolean isAvailable(IdlePlaySource source) {
        return strategy.isAvailable(source);
    }

    public boolean isReady(IdlePlaySource source) {
        return strategy.isReady(source);
    }

    /** True when the source can never produce tracks again (must be removed). */
    public boolean isBroken(IdlePlaySource source) {
        return strategy.isBroken(source);
    }

    public void ensureLoading(IdlePlaySource source) {
        strategy.ensureLoading(source);
    }

    /** Future of an in-flight load, or null when this mode loads nothing. */
    public @Nullable CompletableFuture<?> loadingFuture(IdlePlaySource source) {
        return strategy.loadingFuture(source);
    }

    public @Nullable MusicDetail selectTrack(IdlePlaySource source) {
        return strategy.selectTrack(source);
    }

    public void onAdd(IdlePlaySource source, PusherInfo pusher) {
        strategy.onAdd(source, pusher);
    }

    public void onRemoved(IdlePlaySource source, PusherInfo pusher) {
        strategy.onRemoved(source, pusher);
    }

    public static void onAllRemoved(UUID playerUUID) {
        for (PlayMode mode : values()) {
            mode.strategy.onAllRemoved(playerUUID);
        }
    }

    public static void resetAll() {
        for (PlayMode mode : values()) {
            mode.strategy.reset();
        }
    }
}
