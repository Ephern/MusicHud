package indi.etern.musichud.client.services.music.states;

import indi.etern.musichud.beans.state.IIdlePlaySourceLayerState;
import indi.etern.musichud.beans.state.IIdlePlaySourceState;

public class IdlePlaySourceState implements IIdlePlaySourceState {
    private final IIdlePlaySourceLayerState local = new LocalIdlePlaySourceState();
    private final IIdlePlaySourceLayerState external = new ExternalIdlePlaySourceState();

    @Override
    public IIdlePlaySourceLayerState local() {
        return local;
    }

    @Override
    public IIdlePlaySourceLayerState external() {
        return external;
    }
}
