package indi.etern.musichud.beans.state;

import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.server.api.playmode.PlayMode;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface IIdlePlaySourceLayerState {
    Set<IdlePlaySource> getSources();

    void add(IdlePlaySource IdlePlaySource);

    void remove(IdlePlaySource idlePlaySource);

    IIdlePlaySourceCollectionState collection(MusicCollection collection, PlayMode playMode);

    Unregister onAdd(Consumer<IdlePlaySource> listener);

    Unregister onRemove(Consumer<IdlePlaySource> listener);

    Unregister onChange(Consumer<IdlePlaySource> listener);

    void loadFromConfig();

    CompletableFuture<? extends MusicCollection> load(Class<?> type, long id);

    void updateAll(List<IdlePlaySource> playlistSources);

    /**
     * Drops local sources that are no longer present on the server side
     * (e.g. removed after an intelligent load failure). Sources pending a
     * request/response confirmation must be skipped.
     */
    void removeMissingFromServer(List<IdlePlaySource> serverSources);

    void reset();

    /**
     * UI callbacks for a manual load-error recovery. All methods are invoked on the UI
     * thread; between {@link #onStart} and {@link #onFinished} the caller must keep the
     * affected controls disabled.
     */
    interface RecoveryUi {
        void onStart();

        /** @param confirmedMode the mode the server confirmed, {@code null} when the recovery failed. */
        void onFinished(boolean success, @Nullable PlayMode confirmedMode);
    }

    /** @return {@code true} if the local entry of this collection is currently load-errored. */
    boolean isInLoadError(Class<?> type, long id);

    /** Fired when an entry enters the load-error state (on the state worker thread). */
    Unregister onLoadError(Consumer<IdlePlaySource> listener);

    /** Fired when an entry leaves the load-error state (on the state worker thread). */
    Unregister onLoadErrorCleared(Consumer<IdlePlaySource> listener);

    /**
     * Attempts to re-sync a load-errored local entry: pushes the pre-error mode first,
     * then falls back to {@link PlayMode#RANDOM}. Atomic: fails when another recovery
     * for the same collection is already running.
     *
     * @return {@code false} when the collection is not load-errored or a recovery is
     *         already in flight ({@code ui} is not invoked then).
     */
    boolean requestRecovery(Class<?> type, long id, RecoveryUi ui);
}
