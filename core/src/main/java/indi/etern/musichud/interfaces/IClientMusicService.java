package indi.etern.musichud.interfaces;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.music.actions.MessagedResult;
import indi.etern.musichud.beans.state.IMusicTrackState;
import indi.etern.musichud.beans.state.IIdlePlaySourceState;
import indi.etern.musichud.beans.state.ISubscribeState;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;

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
        ObservableSequencedSet<Album> getSubscribedAlbums();
        ObservableSequencedSet<Artist> getSubscribedArtists();
    }

    IIdlePlaySourceState getIdlePlaySourceState();

    CompletableFuture<Playlist> loadPlaylistDetail(long id, boolean ignoreCache);

    CompletableFuture<Album> loadAlbumDetail(long id, boolean ignoreCache);

    void refreshQueue(Queue<QueueItem> queue);

    void sendPushMusicToQueue(Traceable<Long> music);

    void sendRemoveMusicFromQueue(QueueItem item);

    void switchMusic(Traceable<MusicDetail> musicDetail, Traceable<MusicDetail> nextIdleMusicDetail, ZonedDateTime serverStartTime, String message);

    /**
     * Asks the server to re-randomize the local player's idle "next to play".
     * The result carries a {@code music_hud.} i18n key on failure.
     */
    CompletableFuture<MessagedResult<Void>> rotateNextToPlay();

    /** Replaces only the displayed idle "next to play" (e.g. after a reroll), keeping playback. */
    void updateNextToPlay(Traceable<MusicDetail> nextIdleMusicDetail);

    CompletableFuture<Artist> loadArtist(long id, boolean ignoreCache);

    CompletableFuture<List<MusicDetail>> loadArtistMusic(long id, int offset);

    void voteForSkipCurrent();

    void keyBindsVoteSkipCurrent();

    CompletableFuture<Artist> loadArtistDetailAsync(Artist artist);

    CompletableFuture<Collection<MusicDetail>> loadMoreMusicOfArtist(Artist artist);

    CompletableFuture<loadMusicCollectionMoreDataResult> loadMoreMusicOfCollection(MusicCollection musicCollection, boolean ignoreCache);

    Queue<QueueItem> getMusicQueue();

    Set<Consumer<Queue<QueueItem>>> getMusicQueueRefreshListeners();

    Set<Consumer<QueueItem>> getMusicQueuePushListeners();

    Set<BiConsumer<Integer, QueueItem>> getMusicQueueRemoveListeners();

    IMusicTrackState getMusicTrackState(MusicDetail musicDetail);

    ISubscribeState<Playlist> getPlaylistSubscribeState(Playlist musicDetail);

    ISubscribeState<Album> getAlbumSubscribeState(Album musicDetail);

    ISubscribeState<Artist> getArtistSubscribedState(Artist musicDetail);

    CompletableFuture<? extends IUserCollections> loadUserCollections(boolean ignoreCache);

    record loadMusicCollectionMoreDataResult(MusicCollection musicCollection, Collection<MusicDetail> musicDetails) {}
}
