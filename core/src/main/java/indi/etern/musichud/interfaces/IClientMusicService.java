package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.state.IMusicTrackState;
import indi.etern.musichud.beans.state.ISubscribeState;
import indi.etern.musichud.platform.Environment;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface IClientMusicService {
    static IClientMusicService getInstance() {
        Environment currentEnvironment = MusicHud.getCurrentEnvironment();
        if (currentEnvironment.getSide() == Environment.Side.CLIENT) {
            Environment.Platform platform = currentEnvironment.getPlatform();
            Supplier<IClientMusicService> supplier = platform.getClientMusicServiceSupplier();
            if (supplier != null) {
                IClientMusicService iClientMusicService = supplier.get();
                if (iClientMusicService != null) {
                    return iClientMusicService;
                }
            }
        }
        throw new UnsupportedOperationException();
    }

    interface IUserCollections {
        UserCategoryPlaylists getUserCategoryPlaylists();
        SequencedCollection<Album> getSubscribedAlbums();
        SequencedCollection<Artist> getSubscribedArtists();
    }

    CompletableFuture<? extends MusicCollection> loadIdlePlaySource(Class<?> type, long id);

    CompletableFuture<Playlist> loadPlaylistDetail(long id, boolean ignoreCache);

    CompletableFuture<Album> loadAlbumDetail(long id, boolean ignoreCache);

    void addToIdlePlaySource(MusicCollection idlePlaySourceCollection);

    void removeFromIdlePlaySource(MusicCollection collection);

    void refreshQueue(Queue<MusicDetail> queue);

    void sendPushMusicToQueue(MusicDetail musicDetail);

    void sendRemoveMusicFromQueue(int index, MusicDetail musicDetail);

    void switchMusic(MusicDetail musicDetail, MusicDetail nextIdleMusicDetail, ZonedDateTime serverStartTime, String message);

    CompletableFuture<Artist> loadArtist(long id, boolean ignoreCache);

    CompletableFuture<List<MusicDetail>> loadArtistMusic(long id, int offset);

    void voteForSkipCurrent();

    void keyBindsVoteSkipCurrent();

    void updateAllIdlePlaySources(List<Playlist> playlistSources, List<Album> albumSources);

    CompletableFuture<UserCategoryPlaylists> loadUserPlaylists(boolean ignoreCache);

    CompletableFuture<LinkedHashSet<Album>> loadUserAlbums(boolean ignoreCache);

    CompletableFuture<LinkedHashSet<Artist>> loadUserArtists(boolean ignoreCache);

    CompletableFuture<Artist> loadArtistDetailAsync(Artist artist);

    CompletableFuture<Collection<MusicDetail>> loadMoreMusicOfArtist(Artist artist);

    CompletableFuture<loadMusicCollectionMoreDataResult> loadMoreMusicOfCollection(MusicCollection musicCollection, boolean ignoreCache);

    Set<MusicCollection> getLocalIdlePlaySources();

    Set<Consumer<MusicCollection>> getLocalIdlePlaySourceAddListeners();

    Set<Consumer<MusicCollection>> getLocalIdlePlaySourceRemoveListeners();

    Set<Consumer<MusicCollection>> getLocalIdlePlaySourceChangeListeners();

    Set<MusicCollection> getServerIdlePlaySources();

    Set<Consumer<MusicCollection>> getServerIdlePlaySourceAddListeners();

    Set<Consumer<MusicCollection>> getServerIdlePlaySourceRemoveListeners();

    Set<Consumer<MusicCollection>> getServerIdlePlaySourceChangeListeners();

    Queue<MusicDetail> getMusicQueue();

    Set<Consumer<Queue<MusicDetail>>> getMusicQueueRefreshListeners();

    Set<Consumer<MusicDetail>> getMusicQueuePushListeners();

    Set<BiConsumer<Integer, MusicDetail>> getMusicQueueRemoveListeners();

    boolean isIdlePlaySourceLoaded();

    IMusicTrackState getMusicTrackState(MusicDetail musicDetail);

    ISubscribeState<Playlist> getPlaylistSubscribeState(Playlist musicDetail);

    ISubscribeState<Album> getAlbumSubscribeState(Album musicDetail);

    ISubscribeState<Artist> getArtistSubscribedState(Artist musicDetail);

    CompletableFuture<? extends IUserCollections> loadUserCollections(boolean ignoreCache);

    record loadMusicCollectionMoreDataResult(MusicCollection musicCollection, Collection<MusicDetail> musicDetails) {}
}
