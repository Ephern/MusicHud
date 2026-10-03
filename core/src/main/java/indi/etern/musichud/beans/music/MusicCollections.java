package indi.etern.musichud.beans.music;

public final class MusicCollections {
    private MusicCollections() {
    }

    /**
     * Distinguishes a genuinely loaded collection from the generic failure sentinels.
     * <p>
     * The id-based test is required because the sentinel singletons are serialized over the
     * network as fresh instances, so reference comparison would not survive the round trip:
     * {@link Playlist#EMPTY} (id -1) and {@link Album#NONE} (id 0) are rejected, while a
     * legitimately empty collection (e.g. {@link Playlist#empty(long)}) keeps a positive id
     * and is considered usable.
     */
    public static boolean isUsable(MusicCollection collection) {
        return collection != null && collection.getId() > 0;
    }
}
