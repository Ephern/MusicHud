package indi.etern.musichud.beans.state;

public interface LazyLoadable {
    enum LoadState {
        BRIEF, BASIC, FULL
    }

    LoadState getCurrentLoadState();

    default void load(LoadState loadState) {
        throw new UnsupportedOperationException();
    }
}