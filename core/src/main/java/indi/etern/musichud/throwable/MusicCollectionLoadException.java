package indi.etern.musichud.throwable;

import indi.etern.musichud.beans.music.MusicCollection;
import lombok.Getter;

/**
 * Thrown when a playlist/album detail could not be loaded, so callers can retry or surface
 * an error instead of silently substituting an empty placeholder collection.
 */
@Getter
public class MusicCollectionLoadException extends RuntimeException {
    private final long id;
    private final Class<? extends MusicCollection> type;

    public MusicCollectionLoadException(long id, Class<? extends MusicCollection> type) {
        super("Failed to load music collection " + type.getSimpleName() + " (ID: " + id + ")");
        this.id = id;
        this.type = type;
    }
}
