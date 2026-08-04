package indi.etern.musichud.beans.music;

import indi.etern.musichud.utils.collections.ObservableSequencedSet;

public interface MusicCollection extends IdentifiedBeans {
    long getId();
    String getName();
    String getNameI18nKey();
    String getImageThumbnailUrl(int size);
    int getMusicTrackCount();
    PusherInfo getPusherInfo();
    ObservableSequencedSet<MusicDetail> getMusicDetails();
    MusicCollection copyWithPusherInfo(PusherInfo pusherInfo);

    boolean equalsLoose(Object obj);
}