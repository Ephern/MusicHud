package indi.etern.musichud.beans.music;

import indi.etern.musichud.utils.collections.ObservableSequencedSet;

public interface MusicCollection extends IdentifiedBeans {
    long getId();
    String getName();
    String getNameI18nKey();
    String getImageThumbnailUrl(int size);
    int getMusicTrackCount();
    ObservableSequencedSet<MusicDetail> getMusicDetails();

    boolean equalsLoose(Object obj);
}