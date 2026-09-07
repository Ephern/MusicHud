package indi.etern.musichud.client.services.music.states;

import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.beans.music.actions.ActionResult;
import indi.etern.musichud.beans.music.actions.MessagedResult;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.ProfileConfigData;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.pushMessages.c2s.RemoveFromIdlePlaySourceMessage;
import indi.etern.musichud.network.payloads.requestResponseCycle.AddToIdlePlaySourceRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.AddToIdlePlaySourceResponse;
import indi.etern.musichud.server.api.playmode.PlayMode;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static indi.etern.musichud.server.api.impl.ncm.CommonCaches.*;

public class LocalIdlePlaySourceState extends AbstractIdlePlaySourceLayerState {
    private static final Logger logger = MusicHud.getLogger(LocalIdlePlaySourceState.class);
    private static final IClientNetworkService clientNetworkService = IClientNetworkService.getInstance();
    private static final ProfileConfigData profileConfigData = ProfileConfigData.getInstance();
    private static final Duration ADD_REQUEST_TIMEOUT = Duration.ofSeconds(12);
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
    /** Load-errored entries keyed by collection; guarded by {@link #stateLock}. */
    private final Map<DebounceKey, LoadError> loadErrors = new HashMap<>();
    private final Set<Consumer<IdlePlaySource>> loadErrorListeners = ConcurrentHashMap.newKeySet();
    private final Set<Consumer<IdlePlaySource>> loadErrorClearedListeners = ConcurrentHashMap.newKeySet();
    /** True once the config reload batch (login re-sync) has fully settled. */
    @Getter
    private boolean loaded = false;
    /** True while the config reload batch is running. */
    private boolean loading = false;
    /** Incremented on every (re)load batch start / {@link #reset()}; guards markLoadSettled. */
    private int batchGen = 0;

    private record DebounceKey(Class<?> type, long id) {
        static DebounceKey of(IdlePlaySource source) {
            return new DebounceKey(source.getType(), source.getId());
        }
    }

    /**
     * A local entry whose add could not be confirmed by the server. The entry stays live,
     * persisted and in {@link #pendingAdds} (so server pushes cannot reconcile it away);
     * {@link #syncing} serializes manual recoveries across widget instances.
     */
    private static final class LoadError {
        final IdlePlaySource source;
        volatile boolean syncing = false;

        LoadError(IdlePlaySource source) {
            this.source = source;
        }
    }

    private record RecoveryResult(boolean success, PlayMode confirmedMode, String message) {
    }

    /** Debounce bookkeeping for one collection; every field is guarded by {@link #stateLock}. */
    private static final class DebounceState {
        final AtomicInteger version = new AtomicInteger(0);
        /** Entry present before the current batch began (may be null); restored on rollback. */
        IdlePlaySource base;
        /** True from the first add of a batch until it settles; keeps {@link #base} stable within the batch. */
        boolean active = false;
    }

    @Override
    public void loadFromConfig() {
        List<IdlePlaySource> snapshot;
        int generation;
        synchronized (stateLock) {
            if (loaded || loading) {
                return;
            }
            loading = true;
            batchGen++;
            generation = batchGen;
            snapshot = List.copyOf(profileConfigData.getIdlePlaySources());
        }
        if (snapshot.isEmpty()) {
            synchronized (stateLock) {
                // Only settle if no reset superseded this batch meanwhile
                if (batchGen == generation) {
                    loading = false;
                    loaded = true;
                }
            }
            return;
        }
        // Remaining sources not yet settled (added or retries exhausted); loaded flips true at zero
        AtomicInteger remaining = new AtomicInteger(snapshot.size());
        MusicHud.EXECUTOR.execute(() -> {
            for (IdlePlaySource idlePlaySource : snapshot) {
                loadWithRetry(idlePlaySource, 3, remaining, generation);
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

    private void loadWithRetry(IdlePlaySource idlePlaySource, int attemptsLeft, AtomicInteger remaining, int generation) {
        CompletableFuture<? extends MusicCollection> future;
        try {
            future = load(idlePlaySource.getType(), idlePlaySource.getId());
        } catch (Exception e) {
            future = null;
        }
        if (future == null) {
            scheduleRetry(idlePlaySource, attemptsLeft, remaining, generation, "load returned null");
            return;
        }
        future.whenComplete((musicCollection, throwable) -> {
            if (throwable != null) {
                scheduleRetry(idlePlaySource, attemptsLeft, remaining, generation, throwable);
            } else if (musicCollection != null) {
                repushOrAdd(idlePlaySource);
                markLoadSettled(remaining, generation);
            } else {
                scheduleRetry(idlePlaySource, attemptsLeft, remaining, generation, "load returned empty collection");
            }
        });
    }

    private void scheduleRetry(IdlePlaySource idlePlaySource, int attemptsLeft, AtomicInteger remaining, int generation, Object reason) {
        if (attemptsLeft > 0) {
            MusicHud.EXECUTOR.execute(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    markLoadSettled(remaining, generation);
                    return;
                }
                loadWithRetry(idlePlaySource, attemptsLeft - 1, remaining, generation);
            });
        } else {
            logger.error("Failed to load idle play source {} ({}) after retries: {}",
                    idlePlaySource.getType().getSimpleName(), idlePlaySource.getId(), reason);
            markLoadSettled(remaining, generation);
        }
    }

    private void markLoadSettled(AtomicInteger remaining, int generation) {
        if (remaining.decrementAndGet() == 0) {
            synchronized (stateLock) {
                // A batch superseded by reset() must not re-arm the guard: loaded is only
                // meaningful for the current batch's generation
                if (batchGen == generation) {
                    loading = false;
                    loaded = true;
                }
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
                settleAdd(idlePlaySource, key, state, version, result.message());
            } else {
                completeAdd(idlePlaySource, key, state, version);
            }
        } catch (Exception e) {
            logger.warn("Add request for idle play source {} timed out or failed",
                    idlePlaySource.getId(), e);
            settleAdd(idlePlaySource, key, state, version, MusicHud.MOD_ID + ".text.idleSourceLoadFailed");
        }
    }

    private void completeAdd(IdlePlaySource idlePlaySource, DebounceKey key, DebounceState state, int version) {
        IdlePlaySource clearedError = null;
        synchronized (stateLock) {
            if (state.version.get() != version) {
                // Superseded; the newer batch owns pendingAdds bookkeeping
                return;
            }
            pendingAdds.remove(idlePlaySource);
            LoadError error = loadErrors.remove(key);
            if (error != null) {
                clearedError = error.source;
            }
            finishBatch(key, state);
        }
        if (clearedError != null) {
            fireLoadErrorCleared(clearedError);
        }
    }

    /**
     * Settles a failed add. With a pre-batch entry ({@code base != null}, mode switch) the
     * previous entry is restored (silently first, so removing the new one never leaves the
     * local layer empty mid-notification) and the failed variant is dropped from live state
     * and config. Without a base (first add / login re-push) the entry is kept live,
     * persisted and marked unconfirmed: it stays in {@link #pendingAdds} so server pushes
     * cannot reconcile it away, the CycleButton warning offers a manual re-sync and the
     * next login re-pushes automatically. Never runs when the batch was superseded by a
     * newer switch.
     */
    private void settleAdd(IdlePlaySource idlePlaySource, DebounceKey key, DebounceState state, int version,
                           String rawMessage) {
        List<Runnable> notifications = new ArrayList<>();
        boolean configChanged = false;
        boolean keptUnconfirmed = false;
        synchronized (stateLock) {
            if (state.version.get() != version) {
                return;
            }
            IdlePlaySource base = state.base;
            if (base != null) {
                boolean restored = sources.add(base);
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
                pendingAdds.remove(idlePlaySource);
            } else if (sources.contains(idlePlaySource)) {
                keptUnconfirmed = true;
                loadErrors.put(key, new LoadError(idlePlaySource));
            }
            finishBatch(key, state);
        }
        notifications.forEach(Runnable::run);
        if (configChanged) {
            profileConfigData.saveToConfig();
        }
        if (keptUnconfirmed) {
            fireLoadError(idlePlaySource);
        }
        String message = resolveFailMessage(rawMessage, keptUnconfirmed);
        if (message != null) {
            showSourceToast(message, idlePlaySource);
        }
    }

    /** Maps the generic server-side failure key to a named client-side variant; specific keys pass through. */
    private static String resolveFailMessage(String rawMessage, boolean keptUnconfirmed) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return null;
        }
        if (rawMessage.equals(MusicHud.MOD_ID + ".text.idleSourceLoadFailed")) {
            return keptUnconfirmed
                    ? MusicHud.MOD_ID + ".text.idleSourceLoadFailedNamed"
                    : MusicHud.MOD_ID + ".text.idleSourceRollbackNamed";
        }
        return rawMessage;
    }

    /** Resolves the display name of a source from the collection caches, falling back to type + id. */
    private static String resolveSourceName(IdlePlaySource source) {
        MusicCollection cached = null;
        if (source.getType() == Playlist.class) {
            cached = playlistsCache.getIfPresent(PlaylistCacheKey.of(source.getId(), -1));
            if (cached == null) {
                cached = playlistsCache.getIfPresent(PlaylistCacheKey.of(source.getId(), Profile.getCurrent().getUserId()));
            }
        } else if (source.getType() == Album.class) {
            cached = albumsCache.getIfPresent(source.getId());
        }
        if (cached != null) {
            return cached.getName();
        }
        return source.getType().getSimpleName() + " #" + source.getId();
    }

    /** Shows a localized toast; {@code {}} placeholders in the key's text are filled with the source name. */
    private static void showSourceToast(String key, IdlePlaySource source) {
        IClientDistUtil clientDistUtil = IClientDistUtil.getInstance();
        String message = key.startsWith(MusicHud.MOD_ID + ".")
                ? clientDistUtil.getI18n(key).replace("{}", resolveSourceName(source))
                : key;
        clientDistUtil.showToast(message);
    }

    /** Ends the batch; caller holds {@link #stateLock} and has verified the debounce version. */
    private void finishBatch(DebounceKey key, DebounceState state) {
        debounceStates.remove(key, state);
    }

    @Override
    public void remove(IdlePlaySource idlePlaySource) {
        List<Runnable> notifications = new ArrayList<>();
        boolean removed;
        IdlePlaySource clearedError = null;
        synchronized (stateLock) {
            DebounceKey key = DebounceKey.of(idlePlaySource);
            DebounceState state = debounceStates.remove(key);
            if (state != null) {
                // Cancel any pending/in-flight debounced add for this collection so it
                // cannot resurrect the removed entry
                state.version.incrementAndGet();
            }
            LoadError error = loadErrors.remove(key);
            if (error != null) {
                clearedError = error.source;
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
        if (clearedError != null) {
            fireLoadErrorCleared(clearedError);
        }
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
        List<IdlePlaySource> removed = null;
        synchronized (stateLock) {
            if (!loaded) {
                return;
            }
            for (IdlePlaySource source : sources) {
                if (!pendingAdds.contains(source) && !serverSources.contains(source)
                        && sources.remove(source)) {
                    if (notifications == null) {
                        notifications = new ArrayList<>();
                        removed = new ArrayList<>();
                    }
                    notifications.add(() -> {
                        notifyRemove(source);
                        notifyChange(source);
                    });
                    removed.add(source);
                }
            }
        }
        if (notifications != null) {
            notifications.forEach(Runnable::run);
            // Server-initiated removals (e.g. broken source cleanup): name the removed
            // sources so the user can tell which ones died; the keyless server toast for
            // this case is suppressed in CommonNotificationMessage.
            String names = removed.stream()
                    .map(LocalIdlePlaySourceState::resolveSourceName)
                    .collect(Collectors.joining("」「", "「", "」"));
            IClientDistUtil clientDistUtil = IClientDistUtil.getInstance();
            clientDistUtil.showToast(clientDistUtil
                    .getI18n(MusicHud.MOD_ID + ".text.idleSourceRemoved")
                    .replace("{}", names));
        }
    }

    @Override
    public void reset() {
        List<IdlePlaySource> clearedErrors = List.of();
        synchronized (stateLock) {
            // Invalidate in-flight debounced batches so their completions cannot touch the
            // fresh session state
            debounceStates.values().forEach(s -> s.version.incrementAndGet());
            debounceStates.clear();
            batchGen++;
            loaded = false;
            loading = false;
            pendingAdds.clear();
            if (!loadErrors.isEmpty()) {
                clearedErrors = loadErrors.values().stream().map(e -> e.source).toList();
                loadErrors.clear();
            }
        }
        clearedErrors.forEach(this::fireLoadErrorCleared);
    }

    private void fireLoadError(IdlePlaySource source) {
        loadErrorListeners.forEach(l -> l.accept(source));
    }

    private void fireLoadErrorCleared(IdlePlaySource source) {
        loadErrorClearedListeners.forEach(l -> l.accept(source));
    }

    @Override
    public boolean isInLoadError(Class<?> type, long id) {
        synchronized (stateLock) {
            return loadErrors.containsKey(new DebounceKey(type, id));
        }
    }

    @Override
    public Unregister onLoadError(Consumer<IdlePlaySource> listener) {
        loadErrorListeners.add(listener);
        return () -> loadErrorListeners.remove(listener);
    }

    @Override
    public Unregister onLoadErrorCleared(Consumer<IdlePlaySource> listener) {
        loadErrorClearedListeners.add(listener);
        return () -> loadErrorClearedListeners.remove(listener);
    }

    @Override
    public boolean requestRecovery(Class<?> type, long id, RecoveryUi ui) {
        IdlePlaySource live;
        synchronized (stateLock) {
            LoadError error = loadErrors.get(new DebounceKey(type, id));
            if (error == null || error.syncing) {
                // Not errored, or another widget instance already runs a recovery
                return false;
            }
            error.syncing = true;
            live = error.source;
        }
        ui.onStart();
        MusicHud.EXECUTOR.execute(() -> {
            // Attempt 1: the mode that was active before the warning; attempt 2 falls
            // back to the default RANDOM mode (skipped when it is the same attempt)
            RecoveryResult result = attemptRecoveryPush(live, live.getPlayMode());
            if (!result.success() && live.getPlayMode() != PlayMode.RANDOM) {
                result = attemptRecoveryPush(live, PlayMode.RANDOM);
            }
            PlayMode confirmed = result.success() ? result.confirmedMode() : null;
            if (confirmed != null) {
                confirmRecovery(type, id, confirmed);
            } else {
                synchronized (stateLock) {
                    LoadError error = loadErrors.get(new DebounceKey(type, id));
                    if (error != null) {
                        error.syncing = false;
                    }
                }
                showSourceToast(MusicHud.MOD_ID + ".text.idleSourceRecoveryFailed", live);
            }
            MuiModApi.postToUiThread(() -> ui.onFinished(confirmed != null, confirmed));
        });
        return true;
    }

    private RecoveryResult attemptRecoveryPush(IdlePlaySource live, PlayMode mode) {
        try {
            // A fresh variant object: IdlePlaySource is hash-keyed by playMode, mutating
            // the live entry in place would corrupt the set buckets
            IdlePlaySource variant = new IdlePlaySource(live.getId(), live.getType(), mode, PusherInfo.EMPTY);
            AddToIdlePlaySourceResponse response = RequestResponseManager.send(
                            new AddToIdlePlaySourceRequest(variant),
                            AddToIdlePlaySourceResponse.class,
                            ADD_REQUEST_TIMEOUT)
                    .join();
            MessagedResult<Void> result = response.getResult();
            if (result.actionResult() == ActionResult.FAIL) {
                return new RecoveryResult(false, null, result.message());
            }
            return new RecoveryResult(true, mode, null);
        } catch (Exception e) {
            logger.warn("Recovery push for idle play source {} ({}) failed",
                    live.getId(), live.getType().getSimpleName(), e);
            return new RecoveryResult(false, null, MusicHud.MOD_ID + ".text.idleSourceLoadFailed");
        }
    }

    private void confirmRecovery(Class<?> type, long id, PlayMode confirmedMode) {
        List<Runnable> notifications = new ArrayList<>();
        IdlePlaySource cleared = null;
        boolean configChanged = false;
        synchronized (stateLock) {
            DebounceKey key = new DebounceKey(type, id);
            LoadError error = loadErrors.remove(key);
            if (error != null) {
                cleared = error.source;
            }
            IdlePlaySource previous = sources.stream()
                    .filter(s -> s.getId() == id && s.getType() == type)
                    .findFirst().orElse(null);
            if (previous != null) {
                if (previous.getPlayMode() != confirmedMode) {
                    IdlePlaySource replacement = new IdlePlaySource(id, type, confirmedMode, previous.getPusherInfo());
                    sources.remove(previous);
                    sources.add(replacement);
                    pendingAdds.remove(previous);
                    profileConfigData.getIdlePlaySources().remove(previous);
                    profileConfigData.getIdlePlaySources().add(replacement);
                    configChanged = true;
                    notifications.add(() -> {
                        notifyRemove(previous);
                        notifyChange(previous);
                    });
                    notifications.add(() -> {
                        notifyAdd(replacement);
                        notifyChange(replacement);
                    });
                } else {
                    // Same mode confirmed: just release it from the unconfirmed set so
                    // server pushes can reconcile it again
                    pendingAdds.remove(previous);
                }
            } else {
                // The entry was removed while the recovery was in flight; undo the
                // server-side add so the server does not keep a source the client dropped
                IdlePlaySource variant = new IdlePlaySource(id, type, confirmedMode, PusherInfo.EMPTY);
                clientNetworkService.sendToServer(new RemoveFromIdlePlaySourceMessage(variant));
            }
        }
        notifications.forEach(Runnable::run);
        if (configChanged) {
            profileConfigData.saveToConfig();
        }
        if (cleared != null) {
            fireLoadErrorCleared(cleared);
        }
    }
}
