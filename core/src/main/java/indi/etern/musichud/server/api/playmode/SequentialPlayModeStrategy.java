package indi.etern.musichud.server.api.playmode;

import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.PusherInfo;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class SequentialPlayModeStrategy implements PlayModeStrategy {
    /** Keyed by source (equals = id+type+mode); counter shared across equal sources. */
    private final Map<IdlePlaySource, AtomicInteger> positions = new ConcurrentHashMap<>();

    @Override
    public boolean supports(MusicCollection collection) {
        return true;
    }

    @Override
    public boolean isAvailable(IdlePlaySource source) {
        return source.getMusicCollection() != null && !source.getMusicCollection().getMusicDetails().isEmpty();
    }

    @Override
    public boolean isReady(IdlePlaySource source) {
        return isAvailable(source);
    }

    @Override
    public boolean isBroken(IdlePlaySource source) {
        return false;
    }

    @Override
    public void ensureLoading(IdlePlaySource source) {
    }

    @Override
    public @Nullable CompletableFuture<?> loadingFuture(IdlePlaySource source) {
        return null;
    }

    @Override
    public @Nullable MusicDetail selectTrack(IdlePlaySource source) {
        if (source.getMusicCollection() == null) {
            return null;
        }
        List<MusicDetail> tracks = source.getMusicCollection().getMusicDetails().snapshot();
        if (tracks.isEmpty()) {
            return null;
        }
        AtomicInteger position = positions.computeIfAbsent(source, k -> new AtomicInteger());
        return tracks.get(Math.floorMod(position.getAndIncrement(), tracks.size()));
    }

    @Override
    public void onAdd(IdlePlaySource source, PusherInfo pusher) {
    }

    @Override
    public void onRemoved(IdlePlaySource source, PusherInfo pusher) {
        positions.keySet().removeIf(s -> s.equals(source) && s.getPusherInfo().equals(pusher));
    }

    @Override
    public void onAllRemoved(UUID playerUUID) {
        positions.keySet().removeIf(source -> source.getPusherInfo().getPlayerUUID().equals(playerUUID));
    }

    @Override
    public void reset() {
        positions.clear();
    }
}
