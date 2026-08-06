package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.user.ProfileConfigData;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.payloads.pushMessages.c2s.AddToIdlePlaySourceMessage;
import indi.etern.musichud.network.payloads.pushMessages.c2s.RemoveFromIdlePlaySourceMessage;
import lombok.Getter;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class LocalIdlePlaySourceState extends AbstractIdlePlaySourceLayerState {
    private static final Logger logger = MusicHud.getLogger(LocalIdlePlaySourceState.class);
    private static final IClientNetworkService clientNetworkService = IClientNetworkService.getInstance();
    private static final ProfileConfigData profileConfigData = ProfileConfigData.getInstance();
    private final MusicService musicService = MusicService.getInstance();
    @Getter
    private boolean loaded = false;

    @Override
    public void loadFromConfig() {
        if (!loaded) {
            loaded = true;
            Set<IdlePlaySource> idlePlaySources = profileConfigData.getIdlePlaySources();
            if (!idlePlaySources.isEmpty()) {
                MusicHud.EXECUTOR.execute(() -> {
                    for (IdlePlaySource idlePlaySource : idlePlaySources) {
                        loadWithRetry(idlePlaySource, 3);
                    }
                });
            }
        }
    }

    private void loadWithRetry(IdlePlaySource idlePlaySource, int attemptsLeft) {
        CompletableFuture<? extends MusicCollection> future;
        try {
            future = load(idlePlaySource.getType(), idlePlaySource.getId());
        } catch (Exception e) {
            future = null;
        }
        if (future == null) {
            scheduleRetry(idlePlaySource, attemptsLeft, "load returned null");
            return;
        }
        future.whenComplete((musicCollection, throwable) -> {
            if (throwable != null) {
                scheduleRetry(idlePlaySource, attemptsLeft, throwable);
            } else if (musicCollection != null) {
                add(musicCollection);
            }
        });
    }

    private void scheduleRetry(IdlePlaySource idlePlaySource, int attemptsLeft, Object reason) {
        if (attemptsLeft > 0) {
            MusicHud.EXECUTOR.execute(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                loadWithRetry(idlePlaySource, attemptsLeft - 1);
            });
        } else {
            logger.error("Failed to load idle play source {} ({}) after retries: {}",
                    idlePlaySource.getType().getSimpleName(), idlePlaySource.getId(), reason);
        }
    }

    @Override
    public CompletableFuture<? extends MusicCollection> load(Class<?> type, long id) {
        if (type.equals(Album.class)) {
            return musicService.loadAlbumDetail(id, false);
        } else if (type.equals(Playlist.class)) {
            return musicService.loadPlaylistDetail(id, false);
        }
        return null;
    }

    @Override
    public void add(MusicCollection idlePlaySourceCollection) {
        MusicCollection collection = normalize(idlePlaySourceCollection);
        IdlePlaySource idlePlaySource = new IdlePlaySource(collection.getId(), collection.getClass());
        if (sources.stream().noneMatch(s -> s.equalsLoose(collection))) {
            sources.add(collection);
            notifyAdd(collection);
            notifyChange(collection);
            profileConfigData.getIdlePlaySources().add(idlePlaySource);
            profileConfigData.saveToConfig();
        }
        clientNetworkService.sendToServer(new AddToIdlePlaySourceMessage(idlePlaySource));
    }

    @Override
    public void remove(MusicCollection collection) {
        sources.removeIf(c -> c.getId() == collection.getId());
        notifyRemove(collection);
        notifyChange(collection);
        IdlePlaySource idlePlaySource = new IdlePlaySource(collection.getId(), collection.getClass());
        profileConfigData.getIdlePlaySources().remove(idlePlaySource);
        profileConfigData.saveToConfig();
        clientNetworkService.sendToServer(new RemoveFromIdlePlaySourceMessage(idlePlaySource));
    }

    @Override
    public void reset() {
        loaded = false;
    }
}
