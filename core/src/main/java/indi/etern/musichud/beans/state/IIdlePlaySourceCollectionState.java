package indi.etern.musichud.beans.state;

import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.server.api.playmode.PlayMode;

import java.util.function.Consumer;

public interface IIdlePlaySourceCollectionState {
    long getCollectionId();

    boolean isContained();

    void add();

    void remove();

    /** Switches this collection to the given play mode; no-op when not contained or already effective. */
    void updateMode(PlayMode playMode);

    Unregister onOthersModify(Consumer<Boolean> listener);

    default void toggle() {
        if (isContained()) {
            remove();
        } else {
            add();
        }
    }
}
