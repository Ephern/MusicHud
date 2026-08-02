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
import indi.etern.musichud.utils.collections.ObservableSequencedSet;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
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
    private final Queue<MusicDetail> musicQueue = new ArrayDeque<>();
    @Getter
    private final Set<Consumer<Queue<MusicDetail>>> musicQueueRefreshListeners = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<Consumer<MusicDetail>> musicQueuePushListeners = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<BiConsumer<Integer, MusicDetail>> musicQueueRemoveListeners = ConcurrentHashMap.newKeySet();
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
                        playlistsCache.put(likeList.getId(), likeList);
                        userCategoryPlaylists.getCreatedPlaylist()
                                .forEach(playlist -> {
                                    if (playlist.getMusicDetails() != null && playlist.getMusicDetails().size() == playlist.getMusicTrackCount()) {
                                        playlistsCache.put(playlist.getId(), playlist);
                                    }
                                });
                        userCategoryPlaylists.getSubscribedPlaylist()
                                .forEach(playlist -> {
                                    if (playlist.getMusicDetails() != null && playlist.getMusicDetails().size() == playlist.getMusicTrackCount()) {
                                        playlistsCache.put(playlist.getId(), playlist);
                                    }
                                });
                    }
                    if (subscribedAlbums != null) {
                        subscribedAlbums.forEach(album -> {
                            if (album.getMusicDetails() != null && album.getMusicDetails().size() == album.getMusicTrackCount()) {
                                albumsCache.put(album.getId(), album);
                            }
                        });
                    }
                    if (subscribedArtists != null) {
                        subscribedArtists.forEach(artist -> {
                            if (artist.getMusicDetails() != null && !artist.getMusicDetails().isEmpty() && !artist.getDescription().isEmpty()) {
                                artistsCache.put(artist.getId(), artist);
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
                    && cachedPlaylist.getMusicDetails().size() == cachedPlaylist.getMusicTrackCount()) {
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
                    && cachedAlbum.getMusicDetails().size() == cachedAlbum.getMusicTrackCount()) {
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
    public synchronized void refreshQueue(Queue<MusicDetail> queue) {
        List<MusicDetail> local = new ArrayList<>(musicQueue);
        List<MusicDetail> fresh = new ArrayList<>(queue);
        boolean useUniqueId = fresh.stream().anyMatch(md -> !MusicDetail.QUEUE_UNIQUE_ID_ZERO.equals(md.getQueueUniqueID()));
        List<MusicDetail> toRemove = new ArrayList<>();
        int i = 0, j = 0;
        while (i < local.size() && j < fresh.size()) {
            if (sameTrack(local.get(i), fresh.get(j), useUniqueId)) {
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
            MusicDetail removed = toRemove.get(k);
            int index = 0;
            for (MusicDetail md : musicQueue) {
                if (md == removed) {
                    break;
                }
                index++;
            }
            musicQueue.remove(removed);
            int finalIndex = index;
            musicQueueRemoveListeners.forEach(l -> l.accept(finalIndex, removed));
        }
        for (; j < fresh.size(); j++) {
            MusicDetail added = fresh.get(j);
            musicQueue.add(added);
            musicQueuePushListeners.forEach(l -> l.accept(added));
        }
        musicQueueRefreshListeners.forEach(l -> l.accept(queue));
    }

    private static boolean sameTrack(MusicDetail a, MusicDetail b, boolean useUniqueId) {
        return useUniqueId
                ? a.getQueueUniqueID().equals(b.getQueueUniqueID())
                : a.getId() == b.getId();
    }

    @Override
    public void sendPushMusicToQueue(MusicDetail musicDetail) {
        clientNetworkService.sendToServer(new ClientPushMusicToQueueMessage(musicDetail.getId()));
    }

    @Override
    public void sendRemoveMusicFromQueue(int index, MusicDetail musicDetail) {
        clientNetworkService.sendToServer(new ClientRemoveMusicFromQueueMessage(index, musicDetail.getId(), musicDetail.getQueueUniqueID()));
    }

    @Override
    public synchronized void switchMusic(MusicDetail musicDetail, MusicDetail nextIdleMusicDetail, ZonedDateTime serverStartTime, String message) {
        if (clientConfig.getEnable()) {
            if (!musicQueue.isEmpty()) {// preload image
                MusicDetail peek = musicQueue.peek();
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
            NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
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
                    ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.voteForSkipConfirmed"), Toast.LENGTH_SHORT));
                });
            } else {
                lastPressTime = currentTimeMillis;
                MuiModApi.postToUiThread(() -> {
                    //noinspection UnstableApiUsage
                    Context context = UIManager.getInstance().getDecorView().getContext();
                    ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.confirmVoteForSkip"), Toast.LENGTH_SHORT));
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
        if (currentUserCollections != null) {
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
                    ).thenApply(v -> currentUserCollections);
        }
    }

    @RegisterMark
    public static class RegisterImpl implements ClientRegister {
        @Override
        public void register() {
            LoginService.getInstance().getLoginCompleteListeners().add((loginCookieInfo) -> {
                MusicService.getInstance().getIdlePlaySourceState().local().loadFromConfig();
            });
            IClientEventService.getInstance().registerClientPlayerQuit((player) -> {
                MusicHud.EXECUTOR.execute(MusicService::resetCurrentMusicStatus);
            });
        }
    }

}
