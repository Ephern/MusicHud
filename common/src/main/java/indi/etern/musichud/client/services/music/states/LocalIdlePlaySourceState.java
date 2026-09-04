package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.actions.ActionResult;
import indi.etern.musichud.beans.music.actions.MessagedResult;
import indi.etern.musichud.beans.user.ProfileConfigData;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.pushMessages.c2s.RemoveFromIdlePlaySourceMessage;
import indi.etern.musichud.network.payloads.requestResponseCycle.AddToIdlePlaySourceRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.AddToIdlePlaySourceResponse;
import indi.etern.musichud.utils.IClientDistUtil;
import lombok.Getter;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class LocalIdlePlaySourceState extends AbstractIdlePlaySourceLayerState {
    private static final Logger logger = MusicHud.getLogger(LocalIdlePlaySourceState.class);
    private static final IClientNetworkService clientNetworkService = IClientNetworkService.getInstance();
    private static final ProfileConfigData profileConfigData = ProfileConfigData.getInstance();
    private static final Duration ADD_REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private final MusicService musicService = MusicService.getInstance();
    /** Add requests in flight; skipped by {@link #removeMissingFromServer} to avoid racing the optimistic update. */
    private final Set<IdlePlaySource> pendingAdds = ConcurrentHashMap.newKeySet();
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
                add(idlePlaySource);
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

    /** Optimistic add: applied locally first; FAIL/timeout rolls back (restoring a replaced mode) and toasts. */
    @Override
    public void add(IdlePlaySource idlePlaySource) {
        boolean addedLocally = sources.stream().noneMatch(s -> s.equals(idlePlaySource));
        // Mode switch: silently replace the previous entry of the same collection first, so
        // the local layer holds a single entry at any moment (listeners never see both modes)
        IdlePlaySource previous = sources.stream()
                .filter(s -> s.getId() == idlePlaySource.getId()
                        && s.getType() == idlePlaySource.getType()
                        && s.getPlayMode() != idlePlaySource.getPlayMode())
                .findFirst().orElse(null);
        if (previous != null) {
            sources.remove(previous);
            profileConfigData.getIdlePlaySources().remove(previous);
        }
        if (addedLocally) {
            sources.add(idlePlaySource);
            notifyAdd(idlePlaySource);
            notifyChange(idlePlaySource);
            if (previous != null) {
                notifyRemove(previous);
                notifyChange(previous);
            }
            profileConfigData.getIdlePlaySources().add(idlePlaySource);
            profileConfigData.saveToConfig();
        }
        pendingAdds.add(idlePlaySource);
        MusicHud.EXECUTOR.execute(() -> {
            try {
                AddToIdlePlaySourceResponse response = RequestResponseManager.send(
                                new AddToIdlePlaySourceRequest(idlePlaySource),
                                AddToIdlePlaySourceResponse.class,
                                ADD_REQUEST_TIMEOUT)
                        .join();
                pendingAdds.remove(idlePlaySource);
                MessagedResult<Void> result = response.getResult();
                if (result.actionResult() == ActionResult.FAIL) {
                    rollbackAdd(idlePlaySource, previous, result.message());
                }
            } catch (Exception e) {
                pendingAdds.remove(idlePlaySource);
                if (addedLocally || previous != null) {
                    rollbackAdd(idlePlaySource, previous, MusicHud.MOD_ID + ".text.idleSourceLoadFailed");
                } else {
                    logger.warn("Add request for idle play source {} timed out or failed",
                            idlePlaySource.getId(), e);
                }
            }
        });
    }

    private void rollbackAdd(IdlePlaySource idlePlaySource, IdlePlaySource previous, String rawMessage) {
        // Restore the replaced entry silently first, so removing the new one never
        // leaves the local layer empty mid-notification
        boolean restored = previous != null && sources.add(previous);
        if (sources.remove(idlePlaySource)) {
            notifyRemove(idlePlaySource);
            notifyChange(idlePlaySource);
        }
        if (restored) {
            notifyAdd(previous);
            notifyChange(previous);
        }
        profileConfigData.getIdlePlaySources().remove(idlePlaySource);
        if (restored) {
            profileConfigData.getIdlePlaySources().add(previous);
        }
        profileConfigData.saveToConfig();
        if (rawMessage != null && !rawMessage.isEmpty()) {
            showToast(rawMessage);
        }
    }

    private static void showToast(String rawMessage) {
        String message = rawMessage;
        if (message.startsWith(MusicHud.MOD_ID + ".")) {
            message = IClientDistUtil.getInstance().getI18n(message);
        }
        IClientDistUtil.getInstance().showToast(message);
    }

    @Override
    public void remove(IdlePlaySource idlePlaySource) {
        boolean removed = sources.remove(idlePlaySource);
        if (removed) {
            notifyRemove(idlePlaySource);
            notifyChange(idlePlaySource);
            profileConfigData.getIdlePlaySources().remove(idlePlaySource);
            profileConfigData.saveToConfig();
            clientNetworkService.sendToServer(new RemoveFromIdlePlaySourceMessage(idlePlaySource));
        }
    }

    @Override
    public void removeMissingFromServer(List<IdlePlaySource> serverSources) {
        List<IdlePlaySource> toRemove = null;
        for (IdlePlaySource source : sources) {
            if (!pendingAdds.contains(source) && !serverSources.contains(source)) {
                if (toRemove == null) {
                    toRemove = new ArrayList<>();
                }
                toRemove.add(source);
            }
        }
        if (toRemove != null) {
            for (IdlePlaySource source : toRemove) {
                if (sources.remove(source)) {
                    notifyRemove(source);
                    notifyChange(source);
                    profileConfigData.getIdlePlaySources().remove(source);
                    profileConfigData.saveToConfig();
                }
            }
        }
    }

    @Override
    public void reset() {
        loaded = false;
        pendingAdds.clear();
    }
}
