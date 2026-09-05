package indi.etern.musichud.server.api.playmode;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import indi.etern.musichud.throwable.ApiException;
import indi.etern.musichud.throwable.PlaylistTypeUnsupportedException;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class IntelligentPlayModeStrategy implements PlayModeStrategy {
    private static final Logger logger = MusicHud.getLogger(IntelligentPlayModeStrategy.class);
    private final Map<IntelligentKey, IntelligentState> states = new ConcurrentHashMap<>();

    private record IntelligentKey(long playlistId, UUID pusherUUID) {
    }

    /** Transient sampling state of one intelligent idle play source. */
    private static final class IntelligentState {
        final AtomicInteger position = new AtomicInteger(0);
        final int initialCount;
        final long seedId;
        volatile boolean available = true;
        volatile CompletableFuture<List<MusicDetail>> loading;

        IntelligentState(int initialCount, long seedId) {
            this.initialCount = initialCount;
            this.seedId = seedId;
        }
    }

    private IntelligentKey key(IdlePlaySource source) {
        return new IntelligentKey(source.getId(), source.getPusherInfo().getPlayerUUID());
    }

    @Override
    public boolean supports(MusicCollection collection) {
        return collection instanceof Playlist;
    }

    @Override
    public boolean isAvailable(IdlePlaySource source) {
        IntelligentState state = states.get(key(source));
        return state != null && state.available;
    }

    @Override
    public boolean isReady(IdlePlaySource source) {
        IntelligentState state = states.get(key(source));
        if (state == null) {
            return false;
        }
        return source.getMusicCollection() instanceof Playlist playlist
                && playlist.getIntelligentList() != null
                && state.position.get() < playlist.getIntelligentList().size();
    }

    @Override
    public boolean isBroken(IdlePlaySource source) {
        IntelligentState state = states.get(key(source));
        return state == null || !state.available;
    }

    @Override
    public void ensureLoading(IdlePlaySource source) {
        ensureRefillStarted(source, states.get(key(source)));
    }

    @Override
    public @Nullable CompletableFuture<?> loadingFuture(IdlePlaySource source) {
        IntelligentState state = states.get(key(source));
        return state == null ? null : state.loading;
    }

    @Override
    public @Nullable MusicDetail selectTrack(IdlePlaySource source) {
        if (!(source.getMusicCollection() instanceof Playlist playlist)) {
            return null;
        }
        IntelligentState state = states.get(key(source));
        if (state == null || !state.available) {
            return null;
        }
        ObservableSequencedSet<MusicDetail> intelligentList = playlist.getIntelligentList();
        if (intelligentList == null) {
            return null;
        }
        List<MusicDetail> list = intelligentList.snapshot();
        int position = state.position.get();
        if (position >= list.size()) {
            return null;
        }
        MusicDetail sampledTrack = list.get(position);
        state.position.incrementAndGet();
        // Refill (sid = last item id, dedup append) while enough tracks remain
        if (list.size() - state.position.get() < state.initialCount) {
            ensureRefillStarted(source, state);
        }
        return sampledTrack;
    }

    @Override
    public void onAdd(IdlePlaySource source, PusherInfo pusher) {
        IntelligentKey key = key(source);
        if (states.containsKey(key)) {
            return;
        }
        if (!(source.getMusicCollection() instanceof Playlist playlist)) {
            throw new PlaylistTypeUnsupportedException();
        }
        // Detach from the shared API cache: per-player intelligentList must not leak between players
        Playlist owned = new Playlist();
        owned.updateFrom(playlist, false);
        source.setMusicCollection(owned);
        List<MusicDetail> tracks = owned.getMusicDetails().snapshot();
        MusicDetail seed = tracks.get(MusicHud.RANDOM.nextInt(tracks.size()));
        List<MusicDetail> data = IMusicApiService.getInstance(ApiProvider.NCM)
                .getIntelligentList(seed.getId(), owned.getId(), null, pusher.getPlayerUUID());
        if (data.isEmpty()) {
            throw new ApiException("empty intelligent list for playlist " + owned.getId());
        }
        owned.setIntelligentList(new ObservableSequencedSet<>(new LinkedHashSet<>(data)));
        states.put(key, new IntelligentState(data.size(), seed.getId()));
    }

    @Override
    public void onRemoved(IdlePlaySource source, PusherInfo pusher) {
        states.remove(key(source));
    }

    @Override
    public void onAllRemoved(UUID playerUUID) {
        states.keySet().removeIf(key -> key.pusherUUID().equals(playerUUID));
    }

    @Override
    public void reset() {
        states.clear();
    }

    private void ensureRefillStarted(IdlePlaySource source, IntelligentState state) {
        if (state == null || state.loading != null) {
            return;
        }
        synchronized (state) {
            if (state.loading != null) {
                return;
            }
            if (!(source.getMusicCollection() instanceof Playlist playlist)) {
                state.available = false;
                return;
            }
            ObservableSequencedSet<MusicDetail> currentList = playlist.getIntelligentList();
            // Same sid yields the same list; sid+1 shifts the window by one → page from the last known item
            long sid = currentList != null && !currentList.isEmpty() ? currentList.getLast().getId() : state.seedId;
            long playlistId = source.getId();
            long seedId = state.seedId;
            UUID ownerUUID = source.getPusherInfo().getPlayerUUID();
            CompletableFuture<List<MusicDetail>> future = CompletableFuture.supplyAsync(
                    () -> IMusicApiService.getInstance(ApiProvider.NCM).getIntelligentList(seedId, playlistId, sid, ownerUUID),
                    MusicHud.EXECUTOR);
            state.loading = future;
            future.whenComplete((data, throwable) -> {
                if (throwable != null || data == null || data.isEmpty()) {
                    if (throwable != null) {
                        logger.warn("Failed to fetch next intelligent list page for playlist {} (sid: {})",
                                playlistId, sid, throwable);
                    }
                    state.available = false;
                } else {
                    ObservableSequencedSet<MusicDetail> list = playlist.getIntelligentList();
                    if (list == null) {
                        list = new ObservableSequencedSet<>();
                        playlist.setIntelligentList(list);
                    }
                    // SequencedSet dedups by music id (the new page repeats the sid item)
                    list.addAll(new LinkedHashSet<>(data));
                }
                state.loading = null;
            });
        }
    }
}
