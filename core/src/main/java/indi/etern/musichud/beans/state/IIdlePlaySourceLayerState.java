package indi.etern.musichud.beans.state;

import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.interfaces.Unregister;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface IIdlePlaySourceLayerState {
    Set<MusicCollection> getSources();

    void add(MusicCollection collection);

    void remove(MusicCollection collection);

    IIdlePlaySourceCollectionState collection(MusicCollection collection);

    Unregister onAdd(Consumer<MusicCollection> listener);

    Unregister onRemove(Consumer<MusicCollection> listener);

    Unregister onChange(Consumer<MusicCollection> listener);

    void loadFromConfig();

    CompletableFuture<? extends MusicCollection> load(Class<?> type, long id);

    void updateAll(List<Playlist> playlistSources, List<Album> albumSources);

    void reset();
}
