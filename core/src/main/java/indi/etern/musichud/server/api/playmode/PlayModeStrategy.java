package indi.etern.musichud.server.api.playmode;

import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.PusherInfo;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SPI of {@link PlayMode}. Intentionally package-private and default-free:
 * every mode strategy must implement the full contract explicitly, and all
 * callers go through {@link PlayMode}'s public API only.
 */
interface PlayModeStrategy {
    boolean supports(MusicCollection collection);

    boolean isAvailable(IdlePlaySource source);

    boolean isReady(IdlePlaySource source);

    boolean isBroken(IdlePlaySource source);

    void ensureLoading(IdlePlaySource source);

    @Nullable CompletableFuture<?> loadingFuture(IdlePlaySource source);

    @Nullable MusicDetail selectTrack(IdlePlaySource source);

    void onAdd(IdlePlaySource source, PusherInfo pusher);

    void onRemoved(IdlePlaySource source, PusherInfo pusher);

    void onAllRemoved(UUID playerUUID);

    void reset();
}
