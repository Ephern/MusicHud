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

class SequentialPlayModeStrategy implements PlayModeStrategy {
    /**
     * Last played track per player+collection; survives re-adds, mode switches and
     * reconnects within the server uptime (only {@link #reset()} clears it). Keyed by
     * collection identity instead of the full source so a removed-then-re-added source
     * resumes from where sequential playback left off.
     */
    private final Map<SeqKey, Long> lastPlayedTrackIds = new ConcurrentHashMap<>();

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
        SeqKey key = SeqKey.of(source);
        long lastTrackId = lastPlayedTrackIds.getOrDefault(key, -1L);
        // Continue after the last played track; fall back to the first one when the
        // collection no longer contains it (e.g. tracks were removed from the playlist)
        int nextIndex = 0;
        if (lastTrackId >= 0) {
            for (int i = 0; i < tracks.size(); i++) {
                if (tracks.get(i).getId() == lastTrackId) {
                    nextIndex = i + 1;
                    break;
                }
            }
        }
        MusicDetail selected = tracks.get(Math.floorMod(nextIndex, tracks.size()));
        lastPlayedTrackIds.put(key, selected.getId());
        return selected;
    }

    @Override
    public void onAdd(IdlePlaySource source, PusherInfo pusher) {
    }

    @Override
    public void onRemoved(IdlePlaySource source, PusherInfo pusher) {
        // Position intentionally kept so a later re-add resumes from it
    }

    @Override
    public void onAllRemoved(UUID playerUUID) {
        // Position intentionally kept so a reconnecting player resumes from it
    }

    @Override
    public void reset() {
        lastPlayedTrackIds.clear();
    }

    private record SeqKey(UUID playerUUID, Class<?> collectionType, long collectionId) {
        static SeqKey of(IdlePlaySource source) {
            return new SeqKey(source.getPusherInfo().getPlayerUUID(), source.getType(), source.getId());
        }
    }
}
