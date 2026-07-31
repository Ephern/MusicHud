package indi.etern.musichud.beans.music;

import java.util.Collection;

public interface MusicCollection {
    long getId();
    String getName();
    String getNameI18nKey();
    String getImageThumbnailUrl(int size);
    int getMusicTrackCount();
    PusherInfo getPusherInfo();
    Collection<MusicDetail> getMusicDetails();
    MusicCollection copyWithPusherInfo(PusherInfo pusherInfo);

    boolean equalsLoose(Object obj);
}