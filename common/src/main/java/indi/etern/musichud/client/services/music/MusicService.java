package indi.etern.musichud.client.services.music;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.widget.Toast;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.state.IIdlePlaySourceState;
import indi.etern.musichud.beans.state.IMusicTrackState;
import indi.etern.musichud.beans.state.ISubscribeState;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.interfaces.IClientEventService;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.services.music.states.*;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.ClientRegister;
import indi.etern.musichud.interfaces.IClientMusicService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.pushMessages.c2s.ClientPushMusicToQueueMessage;
import indi.etern.musichud.network.payloads.pushMessages.c2s.ClientRemoveMusicFromQueueMessage;
import indi.etern.musichud.network.payloads.pushMessages.c2s.VoteSkipCurrentMusicMessage;
import indi.etern.musichud.network.payloads.requestResponseCycle.*;
import indi.etern.musichud.utils.CollectionUpdateNotifier;
import indi.etern.musichud.utils.IClientDistUtil;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;
import lombok.*;
import net.minecraft.client.resources.language.I18n;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static indi.etern.musichud.server.api.impl.ncm.CommonCaches.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MusicService implements IClientMusicService {
    private static final IClientNetworkService clientNetworkService = IClientNetworkService.getInstance();
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static volatile MusicService instance;

    @Getter(lazy = true)
    private final IIdlePlaySourceState idlePlaySourceState = new IdlePlaySourceState();
    @Getter
    private final Queue<QueueItem> musicQueue = new ArrayDeque<>();
    @Getter
    private final Set<Consumer<Queue<QueueItem>>> musicQueueRefreshListeners = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<Consumer<QueueItem>> musicQueuePushListeners = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<BiConsumer<Integer, QueueItem>> musicQueueRemoveListeners = ConcurrentHashMap.newKeySet();
    long lastPressTime = 0;
    private UserCollections currentUserCollections;

    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PACKAGE)
    public static class UserCollections implements IUserCollections {
        @Setter
        private UserCategoryPlaylists userCategoryPlaylists;
        @Setter
        private ObservableSequencedSet<Album> subscribedAlbums;
        @Setter
        private ObservableSequencedSet<Artist> subscribedArtists;
        private boolean loaded = false;
        private volatile long lastReupdateCachesTimestamp = 0;

        public UserCategoryPlaylists getUserCategoryPlaylists() {
            reupdateCachesAsync();
            return userCategoryPlaylists;
        }

        public ObservableSequencedSet<Album> getSubscribedAlbums() {
            reupdateCachesAsync();
            return subscribedAlbums;
        }

        public ObservableSequencedSet<Artist> getSubscribedArtists() {
            reupdateCachesAsync();
            return subscribedArtists;
        }

        private void reupdateCachesAsync() {
            long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis - lastReupdateCachesTimestamp >= 60000) {
                lastReupdateCachesTimestamp = currentTimeMillis;
                MusicHud.EXECUTOR.submit(() -> {
                    if (userCategoryPlaylists != null) {
                        Playlist likeList = userCategoryPlaylists.getLikeList();
                        if (playlistsCache.asMap().putIfAbsent(likeList.getId(), likeList) == null) {
                            CollectionUpdateNotifier.notifyPlaylistUpdated(likeList.getId());
                        }
                        userCategoryPlaylists.getCreatedPlaylist()
                                .forEach(playlist -> {
                                    if (playlist.getMusicDetails() != null && playlist.getMusicDetails().size() == playlist.getMusicTrackCount()
                                            && playlistsCache.asMap().putIfAbsent(playlist.getId(), playlist) == null) {
                                        CollectionUpdateNotifier.notifyPlaylistUpdated(playlist.getId());
                                    }
                                });
                        userCategoryPlaylists.getSubscribedPlaylist()
                                .forEach(playlist -> {
                                    if (playlist.getMusicDetails() != null && playlist.getMusicDetails().size() == playlist.getMusicTrackCount()
                                            && playlistsCache.asMap().putIfAbsent(playlist.getId(), playlist) == null) {
                                        CollectionUpdateNotifier.notifyPlaylistUpdated(playlist.getId());
                                    }
                                });
                    }
                    if (subscribedAlbums != null) {
                        subscribedAlbums.forEach(album -> {
                            if (album.getMusicDetails() != null && album.getMusicDetails().size() == album.getMusicTrackCount()
                                    && albumsCache.asMap().putIfAbsent(album.getId(), album) == null) {
                                CollectionUpdateNotifier.notifyAlbumUpdated(album.getId());
                            }
                        });
                    }
                    if (subscribedArtists != null) {
                        subscribedArtists.forEach(artist -> {
                            if (artist.getMusicDetails() != null && !artist.getMusicDetails().isEmpty() && !artist.getDescription().isEmpty()) {
                                artistsCache.asMap().putIfAbsent(artist.getId(), artist);
                            }
                        });
                    }
                });
            }
        }
    }


    public static MusicService getInstance() {
        if (instance == null) {
            synchronized (MusicService.class) {
                if (instance == null) {
                    instance = new MusicService();
                }
            }
        }
        return instance;
    }

    public static void resetCurrentMusicStatus() {
        if (instance != null) {
            instance.switchMusic(MusicDetail.NONE, MusicDetail.NONE, null, "");
            instance.getIdlePlaySourceState().local().reset();
            instance.musicQueue.clear();
        }
        if (HudRendererManager.isLoaded()) {
            HudRendererManager.getInstance().reset();
        }
    }

    @Override
    public CompletableFuture<Playlist> loadPlaylistDetail(long id, boolean ignoreCache) {
        if (!ignoreCache) {
            Playlist cachedPlaylist = playlistsCache.getIfPresent(id);
            if (cachedPlaylist != null && cachedPlaylist.getMusicDetails() != null
                    && (!cachedPlaylist.getMusicDetails().isEmpty() || cachedPlaylist.getMusicTrackCount() == 0)) {
                return CompletableFuture.completedFuture(cachedPlaylist);
            }
        }
        return RequestResponseManager.send(
                        new GetPlaylistDetailRequest(id, ignoreCache),
                        GetPlaylistDetailResponse.class,
                        Duration.ofSeconds(5))
                .thenApply(response -> {
                    Playlist playlist = response.getPlaylist();
                    playlistsCache.put(id, playlist);
                    return playlist;
                });
    }

    @Override
    public CompletableFuture<Album> loadAlbumDetail(long id, boolean ignoreCache) {
        if (!ignoreCache) {
            Album cachedAlbum = albumsCache.getIfPresent(id);
            if (cachedAlbum != null && cachedAlbum.getMusicDetails() != null
                    && (!cachedAlbum.getMusicDetails().isEmpty() || cachedAlbum.getMusicTrackCount() == 0)) {
                return CompletableFuture.completedFuture(cachedAlbum);
            }
        }
        return RequestResponseManager.send(
                        new GetAlbumDetailRequest(id, ignoreCache),
                        GetAlbumDetailResponse.class,
                        Duration.ofSeconds(5))
                .thenApply(response -> {
                    Album album = response.getAlbum();
                    albumsCache.put(id, album);
                    return album;
                });
    }

    @Override
    public synchronized void refreshQueue(Queue<QueueItem> queue) {
        List<QueueItem> local = new ArrayList<>(musicQueue);
        List<QueueItem> fresh = new ArrayList<>(queue);
        List<QueueItem> toRemove = new ArrayList<>();
        int i = 0, j = 0;
        while (i < local.size() && j < fresh.size()) {
            if (sameItem(local.get(i), fresh.get(j))) {
                i++;
                j++;
            } else {
                // relative order is preserved, so local[i] must have been removed
                toRemove.add(local.get(i));
                i++;
            }
        }
        while (i < local.size()) {
            toRemove.add(local.get(i++));
        }
        for (int k = toRemove.size() - 1; k >= 0; k--) {
            QueueItem removed = toRemove.get(k);
            int index = 0;
            for (QueueItem item : musicQueue) {
                if (item == removed) {
                    break;
                }
                index++;
            }
            musicQueue.remove(removed);
            int finalIndex = index;
            musicQueueRemoveListeners.forEach(l -> l.accept(finalIndex, removed));
        }
        for (; j < fresh.size(); j++) {
            QueueItem added = fresh.get(j);
            musicQueue.add(added);
            musicQueuePushListeners.forEach(l -> l.accept(added));
        }
        musicQueueRefreshListeners.forEach(l -> l.accept(queue));
    }

    private static boolean sameItem(QueueItem a, QueueItem b) {
        return a.queueUniqueID().equals(b.queueUniqueID());
    }

    @Override
    public void sendPushMusicToQueue(MusicDetail musicDetail) {
        clientNetworkService.sendToServer(new ClientPushMusicToQueueMessage(musicDetail.getId()));
    }

    @Override
    public void sendRemoveMusicFromQueue(int index, QueueItem item) {
        clientNetworkService.sendToServer(new ClientRemoveMusicFromQueueMessage(index, item.musicDetail().getId(), item.queueUniqueID()));
    }

    @Override
    public synchronized void switchMusic(MusicDetail musicDetail, MusicDetail nextIdleMusicDetail, ZonedDateTime serverStartTime, String message) {
        if (clientConfig.getEnable()) {
            NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
            MusicDetail current = nowPlayingInfo.getCurrentlyPlayingMusicDetail();
            boolean sameTrackStillPlaying = !musicDetail.equals(MusicDetail.NONE)
                    && musicDetail.equals(current)
                    && nowPlayingInfo.getMusicStartTime() != null;
            if (sameTrackStillPlaying) {
                // The same track is still playing after a state re-sync (e.g. connection mode
                // switch): keep the audio stream and progress untouched, only sync the next
                // track and refresh the UI instead of restarting the stream.
                nowPlayingInfo.syncSameTrack(musicDetail, nextIdleMusicDetail);
                return;
            }
            if (!musicQueue.isEmpty()) {// preload image
                MusicDetail peek = musicQueue.peek().musicDetail();
                ImageUtils.downloadAsync(peek.getAlbum().getThumbnailPicUrl(240));
                HudRendererManager.getInstance().preloadAlbumImage(peek.getAlbum());
            } else if (nextIdleMusicDetail != null && !nextIdleMusicDetail.equals(MusicDetail.NONE)) {
                ImageUtils.downloadAsync(nextIdleMusicDetail.getAlbum().getThumbnailPicUrl(240));
                HudRendererManager.getInstance().preloadAlbumImage(nextIdleMusicDetail.getAlbum());
            }
            if (!message.isEmpty()) {
                MuiModApi.postToUiThread(() -> {
                    //noinspection UnstableApiUsage
                    Context context = UIManager.getInstance().getDecorView().getContext();
                    ToastUtil.show(Toast.makeText(context, message, Toast.LENGTH_SHORT));
                });
            }
            if (!musicDetail.equals(MusicDetail.NONE)) {
                ImageUtils.downloadAsync(musicDetail.getAlbum().getThumbnailPicUrl(240));
                StreamAudioPlayer streamAudioPlayer = StreamAudioPlayer.getInstance();
                nowPlayingInfo.switchMusicInfo(musicDetail, nextIdleMusicDetail);
                streamAudioPlayer.playAsync(musicDetail, serverStartTime)
                        .thenAccept(nowPlayingInfo::startAt)
                        .exceptionally(e -> null);
            } else {//TODO optional account sync
                nowPlayingInfo.switchMusicInfo(musicDetail, nextIdleMusicDetail);
                nowPlayingInfo.startAt(null);
//                nowPlayingInfo.switchMusic(MusicDetail.NONE,MusicDetail.NONE,null);
                StreamAudioPlayer streamAudioPlayer = StreamAudioPlayer.getInstance();
                streamAudioPlayer.stop();
            }
        }
    }

    @Override
    public CompletableFuture<Artist> loadArtist(long id, boolean ignoreCache) {
        if (!ignoreCache) {
            Artist cachedArtist = artistsCache.getIfPresent(id);
            if (cachedArtist != null && cachedArtist.getMusicDetails() != null) {
                return CompletableFuture.completedFuture(cachedArtist);
            }
        }
        return RequestResponseManager.send(
                        new GetArtistDetailRequest(id),
                        GetArtistDetailResponse.class,
                        Duration.ofSeconds(5))
                .thenApply(GetArtistDetailResponse::getArtist);
    }

    @Override
    public CompletableFuture<List<MusicDetail>> loadArtistMusic(long id, int offset) {
        return RequestResponseManager.send(
                        new GetArtistMoreMusicRequest(id, offset),
                        GetArtistMoreMusicResponse.class,
                        Duration.ofSeconds(5))
                .thenApply(GetArtistMoreMusicResponse::getMusicDetails);
    }

    @Override
    public void voteForSkipCurrent() {
        if (NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail() != null) {
            clientNetworkService.sendToServer(new VoteSkipCurrentMusicMessage(NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail().getId()));
        }
    }

    @Override
    public void keyBindsVoteSkipCurrent() {
        MusicDetail currentlyPlayingMusicDetail = NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail();
        if (currentlyPlayingMusicDetail != null && currentlyPlayingMusicDetail != MusicDetail.NONE) {
            long currentTimeMillis = System.currentTimeMillis();
            if (currentTimeMillis - lastPressTime <= 3000) {
                lastPressTime = 0;
                voteForSkipCurrent();
                MuiModApi.postToUiThread(() -> {
                    //noinspection UnstableApiUsage
                    Context context = UIManager.getInstance().getDecorView().getContext();
                    String s = IClientDistUtil.getInstance().inSinglePlayer()
                            ? I18n.get(MusicHud.MOD_ID + ".text.skipConfirmed")
                            : I18n.get(MusicHud.MOD_ID + ".text.voteForSkipConfirmed");
                    ToastUtil.show(Toast.makeText(context, s, Toast.LENGTH_SHORT));
                });
            } else {
                lastPressTime = currentTimeMillis;
                MuiModApi.postToUiThread(() -> {
                    //noinspection UnstableApiUsage
                    Context context = UIManager.getInstance().getDecorView().getContext();
                    String s = IClientDistUtil.getInstance().inSinglePlayer()
                            ? I18n.get(MusicHud.MOD_ID + ".text.confirmSkip")
                            : I18n.get(MusicHud.MOD_ID + ".text.confirmVoteForSkip");
                    ToastUtil.show(Toast.makeText(context, s, Toast.LENGTH_SHORT));
                });
            }
        }
    }

    @Override
    public CompletableFuture<UserCategoryPlaylists> loadUserPlaylists(boolean ignoreCache) {
        if (!LoginService.getInstance().isLogined()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Cannot call AccountService.loadUserPlaylists when logined as anonymous"));
        }
        return RequestResponseManager.send(
                        new GetUserPlaylistRequest(ignoreCache),
                        GetUserPlaylistResponse.class,
                        Duration.ofSeconds(5))
                .thenApply(GetUserPlaylistResponse::getPlaylists);
    }

    @Override
    public CompletableFuture<LinkedHashSet<Album>> loadUserAlbums(boolean ignoreCache) {
        if (!LoginService.getInstance().isLogined()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Cannot call AccountService.loadUserAlbums when logined as anonymous"));
        }
        return RequestResponseManager.send(
                        new GetUserAlbumsRequest(ignoreCache),
                        GetUserAlbumsResponse.class,
                        Duration.ofSeconds(5))
                .thenApply(GetUserAlbumsResponse::getAlbums);
    }

    @Override
    public CompletableFuture<LinkedHashSet<Artist>> loadUserArtists(boolean ignoreCache) {
        if (!LoginService.getInstance().isLogined()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Cannot call AccountService.loadUserArtists when logined as anonymous"));
        }
        return RequestResponseManager.send(
                        new GetUserArtistsRequest(ignoreCache),
                        GetUserArtistsResponse.class,
                        Duration.ofSeconds(5))
                .thenApply(GetUserArtistsResponse::getArtists);
    }

    @Override
    public CompletableFuture<Artist> loadArtistDetailAsync(Artist artist) {
        List<MusicDetail> musicDetails = artist.getMusicDetails();
        if (musicDetails == null || musicDetails.isEmpty()) {
            return loadArtist(artist.getId(), false);
        } else return CompletableFuture.completedFuture(artist);
    }

    @Override
    public CompletableFuture<Collection<MusicDetail>> loadMoreMusicOfArtist(Artist artist) {
        return MusicService.getInstance().loadArtistMusic(artist.getId(), artist.getMusicDetails().size())
                .thenApply(musicDetails1 -> {
                    artist.getMusicDetails().addAll(musicDetails1);
                    return musicDetails1;
                });
    }

    @Override
    public CompletableFuture<loadMusicCollectionMoreDataResult> loadMoreMusicOfCollection(MusicCollection musicCollection, boolean ignoreCache) {
        if (musicCollection instanceof Album album) {
            return loadAlbumDetail(album.getId(), ignoreCache)
                    .thenApply(albumInfo -> new loadMusicCollectionMoreDataResult(albumInfo, albumInfo.getMusicDetails()));
        } else if (musicCollection instanceof Playlist playlist) {
            return loadPlaylistDetail(playlist.getId(), ignoreCache)
                    .thenApply(playlist1 -> new loadMusicCollectionMoreDataResult(playlist1, playlist1.getTracks()));
        } else {
            return CompletableFuture.failedFuture(new IllegalStateException());
        }
    }

    @Override
    public IMusicTrackState getMusicTrackState(MusicDetail musicDetail) {
        if (musicDetail == null || musicDetail.equals(MusicDetail.NONE)) {
            return MusicTrackState.NONE;
        }
        return new MusicTrackState(musicDetail);
    }

    @Override
    public ISubscribeState<Playlist> getPlaylistSubscribeState(Playlist playlist) {
        return new PlaylistSubscribeState(playlist.getId());
    }

    @Override
    public ISubscribeState<Album> getAlbumSubscribeState(Album album) {
        return new AlbumSubscribeState(album.getId());
    }

    @Override
    public ISubscribeState<Artist> getArtistSubscribedState(Artist artist) {
        return new ArtistSubscribeState(artist.getId());
    }

    @Override
    public CompletableFuture<UserCollections> loadUserCollections(boolean ignoreCache) {
        if (currentUserCollections != null && currentUserCollections.loaded) {
            return CompletableFuture.completedFuture(currentUserCollections);
        } else {
            currentUserCollections = new UserCollections();
            return CompletableFuture.allOf(
                            loadUserPlaylists(ignoreCache).thenAccept(currentUserCollections::setUserCategoryPlaylists),
                            loadUserAlbums(ignoreCache)
                                    .thenAccept(subscribedAlbums ->
                                            currentUserCollections.setSubscribedAlbums(new ObservableSequencedSet<>(subscribedAlbums))),
                            loadUserArtists(ignoreCache)
                                    .thenAccept(subscribedArtists ->
                                            currentUserCollections.setSubscribedArtists(new ObservableSequencedSet<>(subscribedArtists)))
                    ).thenApply(v -> {
                        currentUserCollections.loaded = true;
                        return currentUserCollections;
                    });
        }
    }

    @RegisterMark
    public static class RegisterImpl implements ClientRegister {
        @Override
        public void register() {
            IClientEventService.getInstance().registerClientPlayerQuit((player) -> {
                MusicHud.EXECUTOR.execute(MusicService::resetCurrentMusicStatus);
            });
        }
    }

}
