package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.state.IIdlePlaySourceCollectionState;
import indi.etern.musichud.beans.state.IIdlePlaySourceLayerState;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.server.api.playmode.PlayMode;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public abstract class AbstractIdlePlaySourceLayerState implements IIdlePlaySourceLayerState {
    protected final Set<IdlePlaySource> sources = ConcurrentHashMap.newKeySet();
    private final Set<Consumer<IdlePlaySource>> addListeners = ConcurrentHashMap.newKeySet();
    private final Set<Consumer<IdlePlaySource>> removeListeners = ConcurrentHashMap.newKeySet();
    private final Set<Consumer<IdlePlaySource>> changeListeners = ConcurrentHashMap.newKeySet();

    protected void notifyAdd(IdlePlaySource idlePlaySource) {
        addListeners.forEach(l -> l.accept(idlePlaySource));
    }

    protected void notifyRemove(IdlePlaySource idlePlaySource) {
        removeListeners.forEach(l -> l.accept(idlePlaySource));
    }

    protected void notifyChange(IdlePlaySource idlePlaySource) {
        changeListeners.forEach(l -> l.accept(idlePlaySource));
    }

    @Override
    public Set<IdlePlaySource> getSources() {
        return sources;
    }

    @Override
    public void add(IdlePlaySource collection) {
        if (sources.stream().noneMatch(s -> s.equals(collection))) {
            sources.add(collection);
            notifyAdd(collection);
            notifyChange(collection);
        }
    }

    @Override
    public void remove(IdlePlaySource idlePlaySource) {
        boolean removed = sources.remove(idlePlaySource);
        if (removed) {
            notifyRemove(idlePlaySource);
            notifyChange(idlePlaySource);
        }
    }

    @Override
    public IIdlePlaySourceCollectionState collection(MusicCollection collection, PlayMode playMode) {
        return new IdlePlaySourceCollectionState(this, collection, playMode);
    }

    @Override
    public Unregister onAdd(Consumer<IdlePlaySource> listener) {
        addListeners.add(listener);
        return () -> addListeners.remove(listener);
    }

    @Override
    public Unregister onRemove(Consumer<IdlePlaySource> listener) {
        removeListeners.add(listener);
        return () -> removeListeners.remove(listener);
    }

    @Override
    public Unregister onChange(Consumer<IdlePlaySource> listener) {
        changeListeners.add(listener);
        return () -> changeListeners.remove(listener);
    }

    @Override
    public void loadFromConfig() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<? extends MusicCollection> load(Class<?> type, long id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAll(List<IdlePlaySource> playlistSources) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeMissingFromServer(List<IdlePlaySource> serverSources) {
        throw new UnsupportedOperationException();
    }
}
