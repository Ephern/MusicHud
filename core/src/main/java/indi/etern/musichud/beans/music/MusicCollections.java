package indi.etern.musichud.beans.music;

public final class MusicCollections {
    private MusicCollections() {
    }

    /**
     * Distinguishes a loaded collection from the failure sentinels of the same type. Ids are tested
     * because the sentinels are serialized as fresh instances: {@link Playlist#EMPTY} (-1) and
     * {@link Album#NONE} (0) are rejected while a legitimately empty collection keeps a positive id.
     */
    public static boolean isUsable(MusicCollection collection) {
        return collection != null && collection.getId() > 0;
    }
}
