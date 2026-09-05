package indi.etern.musichud.beans.state;

import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.server.api.playmode.PlayMode;

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
}
