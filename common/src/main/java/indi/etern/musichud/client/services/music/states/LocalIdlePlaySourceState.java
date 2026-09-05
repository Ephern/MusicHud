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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalIdlePlaySourceState extends AbstractIdlePlaySourceLayerState {
    private static final Logger logger = MusicHud.getLogger(LocalIdlePlaySourceState.class);
    private static final IClientNetworkService clientNetworkService = IClientNetworkService.getInstance();
    private static final ProfileConfigData profileConfigData = ProfileConfigData.getInstance();
    private static final Duration ADD_REQUEST_TIMEOUT = Duration.ofSeconds(8);
    /** The server push fires only after the local entry has been stable for this long. */
    private static final long ADD_DEBOUNCE_MILLIS = 500;

    private final MusicService musicService = MusicService.getInstance();
    /**
     * Guards every mutation of {@link #sources}, the persisted config set,
     * {@link #pendingAdds} and the debounce bookkeeping. Mutations come from the UI
     * thread, executor threads (config load callbacks, debounce fires, rollbacks) and
     * network receiver threads; the network wait itself always happens outside the lock.
     */
    private final Object stateLock = new Object();
    private final Map<DebounceKey, DebounceState> debounceStates = new HashMap<>();
    /** Add requests in flight; skipped by {@link #removeMissingFromServer} to avoid racing the optimistic update. */
    private final Set<IdlePlaySource> pendingAdds = ConcurrentHashMap.newKeySet();
    /** True once the config reload batch (login re-sync) has fully settled. */
    @Getter
    private boolean loaded = false;
    /** True while the config reload batch is running. */
    private boolean loading = false;

    private record DebounceKey(Class<?> type, long id) {
        static DebounceKey of(IdlePlaySource source) {
            return new DebounceKey(source.getType(), source.getId());
        }
    }

    /** Debounce bookkeeping for one collection; every field is guarded by {@link #stateLock}. */
    private static final class DebounceState {
        final AtomicInteger version = new AtomicInteger(0);
        /** Entry present before the current batch began (may be null); restored on rollback. */
        IdlePlaySource base;
        /** True when the first add of the batch actually changed the local state. */
        boolean changedLocalState = false;
        /** True from the first add of a batch until it settles; keeps {@link #base} stable within the batch. */
        boolean active = false;
    }

    @Override
    public void loadFromConfig() {
        List<IdlePlaySource> snapshot;
        synchronized (stateLock) {
            if (loaded || loading) {
                return;
            }
            loading = true;
            snapshot = List.copyOf(profileConfigData.getIdlePlaySources());
        }
        if (snapshot.isEmpty()) {
            synchronized (stateLock) {
                loading = false;
                loaded = true;
            }
            return;
        }
        // Remaining sources not yet settled (added or retries exhausted); loaded flips true at zero
        AtomicInteger remaining = new AtomicInteger(snapshot.size());
        MusicHud.EXECUTOR.execute(() -> {
            for (IdlePlaySource idlePlaySource : snapshot) {
                loadWithRetry(idlePlaySource, 3, remaining);
            }
        });
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

    private void loadWithRetry(IdlePlaySource idlePlaySource, int attemptsLeft, AtomicInteger remaining) {
        CompletableFuture<? extends MusicCollection> future;
        try {
            future = load(idlePlaySource.getType(), idlePlaySource.getId());
        } catch (Exception e) {
            future = null;
        }
        if (future == null) {
            scheduleRetry(idlePlaySource, attemptsLeft, remaining, "load returned null");
            return;
        }
        future.whenComplete((musicCollection, throwable) -> {
            if (throwable != null) {
                scheduleRetry(idlePlaySource, attemptsLeft, remaining, throwable);
            } else if (musicCollection != null) {
                repushOrAdd(idlePlaySource);
                markLoadSettled(remaining);
            } else {
                scheduleRetry(idlePlaySource, attemptsLeft, remaining, "load returned empty collection");
            }
        });
    }

    private void scheduleRetry(IdlePlaySource idlePlaySource, int attemptsLeft, AtomicInteger remaining, Object reason) {
        if (attemptsLeft > 0) {
            MusicHud.EXECUTOR.execute(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    markLoadSettled(remaining);
                    return;
                }
                loadWithRetry(idlePlaySource, attemptsLeft - 1, remaining);
            });
        } else {
            logger.error("Failed to load idle play source {} ({}) after retries: {}",
                    idlePlaySource.getType().getSimpleName(), idlePlaySource.getId(), reason);
            markLoadSettled(remaining);
        }
    }

    private void markLoadSettled(AtomicInteger remaining) {
        if (remaining.decrementAndGet() == 0) {
            synchronized (stateLock) {
                loading = false;
                loaded = true;
            }
        }
    }

    /**
     * Pushes a loaded config entry to the server. If a newer entry for the same collection
     * is already live (e.g. the user switched modes while the load was in flight), the live
     * one wins and is re-pushed instead; the stale config variant never overwrites local state.
     */
    private void repushOrAdd(IdlePlaySource configSource) {
        IdlePlaySource live;
        synchronized (stateLock) {
            live = sources.stream()
                    .filter(s -> s.getId() == configSource.getId() && s.getType() == configSource.getType())
                    .findFirst().orElse(null);
        }
        if (live == null) {
            add(configSource);
        } else {
            schedulePush(live);
        }
    }

    /** Optimistic add: applied locally first (instant UI feedback); the server push is debounced
     *  per collection so a rapid mode burst sends only the latest mode. FAIL rolls back to the
     *  pre-batch entry and toasts; a timeout only rolls back when the batch changed local state. */
    @Override
    public void add(IdlePlaySource idlePlaySource) {
        DebounceKey key = DebounceKey.of(idlePlaySource);
        List<Runnable> notifications = new ArrayList<>();
        boolean needSave = false;
        synchronized (stateLock) {
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
                needSave = true;
                notifications.add(() -> {
                    notifyRemove(previous);
                    notifyChange(previous);
                });
            }
            if (addedLocally) {
                sources.add(idlePlaySource);
                profileConfigData.getIdlePlaySources().add(idlePlaySource);
                needSave = true;
                notifications.add(() -> {
                    notifyAdd(idlePlaySource);
                    notifyChange(idlePlaySource);
                });
            }
            DebounceState state = debounceStates.computeIfAbsent(key, k -> new DebounceState());
            if (!state.active) {
                state.active = true;
                state.base = previous;
                state.changedLocalState = previous != null || addedLocally;
            }
        }
        notifications.forEach(Runnable::run);
        if (needSave) {
            profileConfigData.saveToConfig();
        }
        schedulePush(idlePlaySource);
    }

    /** Registers the entry as pending and schedules the debounced server push; only the task
     *  whose debounce version is still current at fire time actually sends the request. */
    private void schedulePush(IdlePlaySource toPush) {
        DebounceKey key = DebounceKey.of(toPush);
        DebounceState state;
        int version;
        synchronized (stateLock) {
            // Drop stale pending entries of the same collection left by earlier burst steps
            pendingAdds.removeIf(s -> s.getId() == toPush.getId()
                    && s.getType() == toPush.getType()
                    && !s.equals(toPush));
            pendingAdds.add(toPush);
            state = debounceStates.computeIfAbsent(key, k -> new DebounceState());
            version = state.version.incrementAndGet();
        }
        MusicHud.EXECUTOR.execute(() -> fireDebouncedAdd(toPush, key, state, version));
    }

    /** Debounce fire: push the add to the server only if this is still the latest switch of the batch. */
    private void fireDebouncedAdd(IdlePlaySource idlePlaySource, DebounceKey key, DebounceState state, int version) {
        try {
            Thread.sleep(ADD_DEBOUNCE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        synchronized (stateLock) {
            if (state.version.get() != version) {
                // Superseded by a newer switch of the same collection; that batch owns the state
                return;
            }
            if (!sources.contains(idlePlaySource) || !pendingAdds.contains(idlePlaySource)) {
                // Removed during the debounce window; cancel the batch
                pendingAdds.remove(idlePlaySource);
                finishBatch(key, state);
                return;
            }
        }
        try {
            AddToIdlePlaySourceResponse response = RequestResponseManager.send(
                            new AddToIdlePlaySourceRequest(idlePlaySource),
                            AddToIdlePlaySourceResponse.class,
                            ADD_REQUEST_TIMEOUT)
                    .join();
            MessagedResult<Void> result = response.getResult();
            if (result.actionResult() == ActionResult.FAIL) {
                settleAdd(idlePlaySource, key, state, version, true, result.message());
            } else {
                completeAdd(idlePlaySource, key, state, version);
            }
        } catch (Exception e) {
            logger.warn("Add request for idle play source {} timed out or failed",
                    idlePlaySource.getId(), e);
            boolean changedLocalState;
            synchronized (stateLock) {
                changedLocalState = state.changedLocalState;
            }
            settleAdd(idlePlaySource, key, state, version, changedLocalState,
                    changedLocalState ? MusicHud.MOD_ID + ".text.idleSourceLoadFailed" : null);
        }
    }

    private void completeAdd(IdlePlaySource idlePlaySource, DebounceKey key, DebounceState state, int version) {
        synchronized (stateLock) {
            if (state.version.get() != version) {
                // Superseded; the newer batch owns pendingAdds bookkeeping
                return;
            }
            pendingAdds.remove(idlePlaySource);
            finishBatch(key, state);
        }
    }

    /**
     * Settles a failed add. When {@code removeLocal} is set, restores the pre-batch entry
     * (silently first, so removing the new one never leaves the local layer empty
     * mid-notification), removes the failed entry and toasts. Never runs when the batch
     * was superseded by a newer switch.
     */
    private void settleAdd(IdlePlaySource idlePlaySource, DebounceKey key, DebounceState state, int version,
                           boolean removeLocal, String rawMessage) {
        List<Runnable> notifications = new ArrayList<>();
        boolean configChanged = false;
        synchronized (stateLock) {
            if (state.version.get() != version) {
                return;
            }
            if (removeLocal) {
                IdlePlaySource base = state.base;
                boolean restored = base != null && sources.add(base);
                if (restored) {
                    profileConfigData.getIdlePlaySources().add(base);
                    notifications.add(() -> {
                        notifyAdd(base);
                        notifyChange(base);
                    });
                }
                if (sources.remove(idlePlaySource)) {
                    notifications.add(() -> {
                        notifyRemove(idlePlaySource);
                        notifyChange(idlePlaySource);
                    });
                    profileConfigData.getIdlePlaySources().remove(idlePlaySource);
                    configChanged = true;
                }
            }
            pendingAdds.remove(idlePlaySource);
            finishBatch(key, state);
        }
        notifications.forEach(Runnable::run);
        if (configChanged) {
            profileConfigData.saveToConfig();
        }
        if (rawMessage != null && !rawMessage.isEmpty()) {
            showToast(rawMessage);
        }
    }

    /** Ends the batch; caller holds {@link #stateLock} and has verified the debounce version. */
    private void finishBatch(DebounceKey key, DebounceState state) {
        debounceStates.remove(key, state);
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
        List<Runnable> notifications = new ArrayList<>();
        boolean removed;
        synchronized (stateLock) {
            DebounceState state = debounceStates.remove(DebounceKey.of(idlePlaySource));
            if (state != null) {
                // Cancel any pending/in-flight debounced add for this collection so it
                // cannot resurrect the removed entry
                state.version.incrementAndGet();
            }
            removed = sources.remove(idlePlaySource);
            if (removed) {
                pendingAdds.remove(idlePlaySource);
                notifications.add(() -> {
                    notifyRemove(idlePlaySource);
                    notifyChange(idlePlaySource);
                });
                profileConfigData.getIdlePlaySources().remove(idlePlaySource);
            }
        }
        notifications.forEach(Runnable::run);
        if (removed) {
            profileConfigData.saveToConfig();
            clientNetworkService.sendToServer(new RemoveFromIdlePlaySourceMessage(idlePlaySource));
        }
    }

    /**
     * Server-driven reconciliation of locally held sources. Skipped entirely while the
     * login re-sync batch is running (the server does not know the local sources yet and
     * an early reconciliation would wipe them), and it never touches the persisted config:
     * the config is only modified by explicit user actions.
     */
    @Override
    public void removeMissingFromServer(List<IdlePlaySource> serverSources) {
        List<Runnable> notifications = null;
        synchronized (stateLock) {
            if (!loaded) {
                return;
            }
            for (IdlePlaySource source : sources) {
                if (!pendingAdds.contains(source) && !serverSources.contains(source)
                        && sources.remove(source)) {
                    if (notifications == null) {
                        notifications = new ArrayList<>();
                    }
                    notifications.add(() -> {
                        notifyRemove(source);
                        notifyChange(source);
                    });
                }
            }
        }
        if (notifications != null) {
            notifications.forEach(Runnable::run);
        }
    }

    @Override
    public void reset() {
        synchronized (stateLock) {
            // Invalidate in-flight debounced batches so their completions cannot touch the
            // fresh session state
            debounceStates.values().forEach(s -> s.version.incrementAndGet());
            debounceStates.clear();
            loaded = false;
            loading = false;
            pendingAdds.clear();
        }
    }
}
