package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.beans.state.IIdlePlaySourceCollectionState;
import indi.etern.musichud.beans.state.IIdlePlaySourceLayerState;
import indi.etern.musichud.interfaces.Unregister;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public abstract class AbstractIdlePlaySourceLayerState implements IIdlePlaySourceLayerState {
    protected final Set<MusicCollection> sources = ConcurrentHashMap.newKeySet();
    private final Set<Consumer<MusicCollection>> addListeners = ConcurrentHashMap.newKeySet();
    private final Set<Consumer<MusicCollection>> removeListeners = ConcurrentHashMap.newKeySet();
    private final Set<Consumer<MusicCollection>> changeListeners = ConcurrentHashMap.newKeySet();

    protected static MusicCollection normalize(MusicCollection collection) {
        PusherInfo pusherInfo = collection.getPusherInfo();
        if (pusherInfo != null && pusherInfo != PusherInfo.EMPTY) {
            return collection.copyWithPusherInfo(PusherInfo.EMPTY);
        }
        return collection;
    }

    protected void notifyAdd(MusicCollection collection) {
        addListeners.forEach(l -> l.accept(collection));
    }

    protected void notifyRemove(MusicCollection collection) {
        removeListeners.forEach(l -> l.accept(collection));
    }

    protected void notifyChange(MusicCollection collection) {
        changeListeners.forEach(l -> l.accept(collection));
    }

    @Override
    public Set<MusicCollection> getSources() {
        return sources;
    }

    @Override
    public void add(MusicCollection idlePlaySourceCollection) {
        MusicCollection collection = normalize(idlePlaySourceCollection);
        if (sources.stream().noneMatch(s -> s.equalsLoose(collection))) {
            sources.add(collection);
            notifyAdd(collection);
            notifyChange(collection);
        }
    }

    @Override
    public void remove(MusicCollection collection) {
        boolean removed = sources.removeIf(c -> c.getId() == collection.getId());
        if (removed) {
            notifyRemove(collection);
            notifyChange(collection);
        }
    }

    @Override
    public IIdlePlaySourceCollectionState collection(MusicCollection collection) {
        return new IdlePlaySourceCollectionState(this, collection);
    }

    @Override
    public Unregister onAdd(Consumer<MusicCollection> listener) {
        addListeners.add(listener);
        return () -> addListeners.remove(listener);
    }

    @Override
    public Unregister onRemove(Consumer<MusicCollection> listener) {
        removeListeners.add(listener);
        return () -> removeListeners.remove(listener);
    }

    @Override
    public Unregister onChange(Consumer<MusicCollection> listener) {
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
    public void updateAll(java.util.List<Playlist> playlistSources, java.util.List<Album> albumSources) {
        throw new UnsupportedOperationException();
    }
}
