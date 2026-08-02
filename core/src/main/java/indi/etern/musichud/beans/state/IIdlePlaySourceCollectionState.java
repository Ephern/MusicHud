package indi.etern.musichud.beans.state;

import indi.etern.musichud.interfaces.Unregister;

import java.util.function.Consumer;

public interface IIdlePlaySourceCollectionState {
    long getCollectionId();

    boolean isContained();

    void add();

    void remove();

    Unregister onOthersModify(Consumer<Boolean> listener);

    default void toggle() {
        if (isContained()) {
            remove();
        } else {
            add();
        }
    }
}
