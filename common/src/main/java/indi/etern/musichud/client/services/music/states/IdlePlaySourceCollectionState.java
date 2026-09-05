package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.state.IIdlePlaySourceCollectionState;
import indi.etern.musichud.beans.state.IIdlePlaySourceLayerState;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.server.api.playmode.PlayMode;

import java.util.function.Consumer;

public class IdlePlaySourceCollectionState implements IIdlePlaySourceCollectionState {
    private final IIdlePlaySourceLayerState layer;
    private final MusicCollection collection;
    private final PlayMode playMode;

    public IdlePlaySourceCollectionState(IIdlePlaySourceLayerState layer, MusicCollection collection, PlayMode playMode) {
        this.layer = layer;
        this.collection = collection;
        this.playMode = playMode;
    }

    @Override
    public long getCollectionId() {
        return collection.getId();
    }

    @Override
    public boolean isContained() {
        return layer.getSources().stream().anyMatch(c -> c.getId() == collection.getId() && c.getType().isInstance(collection) && c.getPlayMode() == playMode);
    }

    @Override
    public void add() {
        layer.add(IdlePlaySource.of(collection, playMode));
    }

    @Override
    public void remove() {
        layer.remove(IdlePlaySource.of(collection, playMode));
    }

    @Override
    public void updateMode(PlayMode playMode) {
        if (layer.getSources().stream().noneMatch(c -> c.getId() == collection.getId() && c.getType().isInstance(collection))) {
            return;
        }
        if (layer.getSources().stream().anyMatch(c -> c.getId() == collection.getId()
                && c.getType().isInstance(collection) && c.getPlayMode() == playMode)) {
            return;
        }
        // The mode-update semantics (server-side switch + sync to all clients) live in the add path
        layer.add(IdlePlaySource.of(collection, playMode));
    }

    @Override
    public Unregister onOthersModify(Consumer<Boolean> listener) {
        return layer.onChange(c -> {
            if (c.getId() == collection.getId()) {
                listener.accept(isContained());
            }
        });
    }
}
