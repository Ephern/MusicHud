package indi.etern.musichud.beans.music;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface MusicCollection {
    long getId();
    String getName();
    String getNameI18nKey();
    String getImageThumbnailUrl(int size);
    PusherInfo getPusherInfo();
    Collection<MusicDetail> getMusicDetails();
    CompletableFuture<Collection<MusicDetail>> loadMusicDetails(boolean ignoreCache);
    MusicCollection copyWithPusherInfo(PusherInfo pusherInfo);
}