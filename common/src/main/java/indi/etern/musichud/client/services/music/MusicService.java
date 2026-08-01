package indi.etern.musichud.client.services.music;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.widget.Toast;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.state.IMusicTrackState;
import indi.etern.musichud.beans.state.ISubscribeState;
import indi.etern.musichud.beans.user.ProfileConfigData;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.interfaces.IClientEventService;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.services.music.states.AlbumSubscribeState;
import indi.etern.musichud.client.services.music.states.ArtistSubscribeState;
import indi.etern.musichud.client.services.music.states.MusicTrackState;
import indi.etern.musichud.client.services.music.states.PlaylistSubscribeState;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.ClientRegister;
import indi.etern.musichud.interfaces.IClientMusicService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.pushMessages.c2s.*;
import indi.etern.musichud.network.payloads.requestResponseCycle.*;
import lombok.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MusicService implements IClientMusicService {
    private static final Logger logger = MusicHud.getLogger(MusicService.class);
    private static final Cache<Long, Playlist> playlistCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();
    private static final Cache<Long, Album> albumCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();
    private static final Cache<Long, Artist> artistCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();
    private static final Cache<Long, UserCollections> userCollectionsCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(5)
            .build();
    private static final IClientNetworkService clientNetworkService = IClientNetworkService.getInstance();
    private static final ProfileConfigData profileConfigData = ProfileConfigData.getInstance();
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static volatile MusicService instance;

    @Getter
    private final Set<MusicCollection> localIdlePlaySources = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<Consumer<MusicCollection>> localIdlePlaySourceAddListeners = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<Consumer<MusicCollection>> localIdlePlaySourceRemoveListeners = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<Consumer<MusicCollection>> localIdlePlaySourceChangeListeners = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<MusicCollection> serverIdlePlaySources = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<Consumer<MusicCollection>> serverIdlePlaySourceAddListeners = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<Consumer<MusicCollection>> serverIdlePlaySourceRemoveListeners = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<Consumer<MusicCollection>> serverIdlePlaySourceChangeListeners = ConcurrentHashMap.newKeySet();
    @Getter
    private final Queue<MusicDetail> musicQueue = new ArrayDeque<>();
    @Getter
    private final Set<Consumer<Queue<MusicDetail>>> musicQueueRefreshListeners = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<Consumer<MusicDetail>> musicQueuePushListeners = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<BiConsumer<Integer, MusicDetail>> musicQueueRemoveListeners = ConcurrentHashMap.newKeySet();
    long lastPressTime = 0;
    @Getter
    private boolean idlePlaySourceLoaded = false;
    private UserCollections currentUserCollections;

    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PACKAGE)
    public static class UserCollections implements IUserCollections {
        @Setter
        private UserCategoryPlaylists userCategoryPlaylists;
        @Setter
        private LinkedHashSet<Album> subscribedAlbums;
        @Setter
        private LinkedHashSet<Artist> subscribedArtists;
        private volatile long lastReupdateCachesTimestamp = 0;

        public UserCategoryPlaylists getUserCategoryPlaylists() {
            reupdateCachesAsync();
            return userCategoryPlaylists;
        }

        public LinkedHashSet<Album> getSubscribedAlbums() {
            reupdateCachesAsync();
            return subscribedAlbums;
        }

        public LinkedHashSet<Artist> getSubscribedArtists() {
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
                        playlistCache.put(likeList.getId(), likeList);
                        userCategoryPlaylists.getCreatedPlaylist()
                                .forEach(playlist -> playlistCache.put(playlist.getId(), playlist));
                        userCategoryPlaylists.getSubscribedPlaylist()
                                .forEach(playlist -> playlistCache.put(playlist.getId(), playlist));
                    }
                    if (subscribedAlbums != null) {
                        subscribedAlbums.forEach(album -> albumCache.put(album.getId(), album));
                    }
                    if (subscribedArtists != null) {
                        subscribedArtists.forEach(artist -> artistCache.put(artist.getId(), artist));
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
            instance.idlePlaySourceLoaded = false;
            instance.musicQueue.clear();
        }
        if (HudRendererManager.isLoaded()) {
            HudRendererManager.getInstance().reset();
        }
    }

    private void loadIdlePlaySourceFromConfig() {
        if (!idlePlaySourceLoaded) {
            idlePlaySourceLoaded = true;
            Set<IdlePlaySource> idlePlaySources = profileConfigData.getIdlePlaySources();
            if (!idlePlaySources.isEmpty()) {
                MusicHud.EXECUTOR.execute(() -> {
                    for (IdlePlaySource idlePlaySource : idlePlaySources) {
                        try {
                            loadIdlePlaySource(idlePlaySource.getType(), idlePlaySource.getId()).thenAcceptAsync(musicCollection -> {
                                clientNetworkService.sendToServer(new AddToIdlePlaySourceMessage(idlePlaySource));
                                getInstance().addToIdlePlaySource(musicCollection);
                            }, MusicHud.EXECUTOR);
                        } catch (Exception e) {
                            logger.error("Failed to load idle play source playlist with idlePlaySource:{}", idlePlaySource, e);
                        }
                    }
                });
            }
        }
    }

    @Override
    public CompletableFuture<? extends MusicCollection> loadIdlePlaySource(Class<?> type, long id) {
        if (type.equals(Album.class)) {
            return loadAlbumDetail(id, false);
        } else if (type.equals(Playlist.class)) {
            return loadPlaylistDetail(id, false);
        }
        return null;
    }

    @Override
    public CompletableFuture<Playlist> loadPlaylistDetail(long id, boolean ignoreCache) {
        if (!ignoreCache) {
            Playlist cachedPlaylist = playlistCache.getIfPresent(id);
            if (cachedPlaylist != null) {
                return CompletableFuture.completedFuture(cachedPlaylist);
            }
        }
        return RequestResponseManager.send(
                        new GetPlaylistDetailRequest(id, ignoreCache),
                        GetPlaylistDetailResponse.class,
                        Duration.ofSeconds(5))
                .thenApply(response -> {
                    Playlist playlist = response.getPlaylist();
                    playlistCache.put(id, playlist);
                    return playlist;
                });
    }

    @Override
    public CompletableFuture<Album> loadAlbumDetail(long id, boolean ignoreCache) {
        if (!ignoreCache) {
            Album cachedAlbum = albumCache.getIfPresent(id);
            if (cachedAlbum != null) {
                return CompletableFuture.completedFuture(cachedAlbum);
            }
        }
        return RequestResponseManager.send(
                        new GetAlbumDetailRequest(id, ignoreCache),
                        GetAlbumDetailResponse.class,
                        Duration.ofSeconds(5))
                .thenApply(response -> {
                    Album album = response.getAlbum();
                    albumCache.put(id, album);
                    return album;
                });
    }

    @Override
    public void addToIdlePlaySource(MusicCollection idlePlaySourceCollection) {
        PusherInfo pusherInfo = idlePlaySourceCollection.getPusherInfo();
        MusicCollection collection;
        if (pusherInfo != null && pusherInfo != PusherInfo.EMPTY) {
            collection = idlePlaySourceCollection.copyWithPusherInfo(PusherInfo.EMPTY);
        } else {
            collection = idlePlaySourceCollection;
        }
        IdlePlaySource idlePlaySource = new IdlePlaySource(collection.getId(), collection.getClass());
        if (localIdlePlaySources.stream().noneMatch(s -> s.equalsLoose(collection))) {
            localIdlePlaySources.add(collection);
            localIdlePlaySourceAddListeners.forEach(l -> l.accept(collection));
            localIdlePlaySourceChangeListeners.forEach(l -> l.accept(collection));
            profileConfigData.getIdlePlaySources().add(idlePlaySource);
            profileConfigData.saveToConfig();
        }
        clientNetworkService.sendToServer(new AddToIdlePlaySourceMessage(idlePlaySource));
    }

    @Override
    public void removeFromIdlePlaySource(MusicCollection collection) {
        localIdlePlaySources.removeIf(c -> c.getId() == collection.getId());
        localIdlePlaySourceRemoveListeners.forEach(l -> l.accept(collection));
        localIdlePlaySourceChangeListeners.forEach(l -> l.accept(collection));
        IdlePlaySource idlePlaySource = new IdlePlaySource(collection.getId(), collection.getClass());
        profileConfigData.getIdlePlaySources().remove(idlePlaySource);
        profileConfigData.saveToConfig();
        clientNetworkService.sendToServer(new RemoveFromIdlePlaySourceMessage(idlePlaySource));
    }

    @Override
    public synchronized void refreshQueue(Queue<MusicDetail> queue) {
        Iterator<MusicDetail> originalIterator = musicQueue.iterator();
        Iterator<MusicDetail> newIterator = queue.iterator();
        int index = 0;
        while (originalIterator.hasNext() || newIterator.hasNext()) {
            if (originalIterator.hasNext() && newIterator.hasNext()) {
                MusicDetail original = originalIterator.next();
                MusicDetail news = newIterator.next();
                if (original.getId() != news.getId()) {
                    AtomicInteger atomicInt = new AtomicInteger(0);
                    int finalIndex = index;
                    musicQueue.removeIf((musicDetail) -> {
                        int i = atomicInt.getAndIncrement();
                        boolean remove = i == finalIndex && musicDetail.equals(original);
                        if (remove) {
                            musicQueueRemoveListeners.forEach(l -> {
                                l.accept(i, musicDetail);
                            });
                        }
                        return remove;
                    });
                }
            } else if (newIterator.hasNext()) {
                MusicDetail addedMusicDetail = newIterator.next();
                musicQueue.add(addedMusicDetail);
                musicQueuePushListeners.forEach(l -> {
                    l.accept(addedMusicDetail);
                });
            } else {
                AtomicInteger atomicInt = new AtomicInteger(0);
                int finalIndex = index;
                musicQueue.removeIf((removedMusicDetail) -> {
                    int i = atomicInt.getAndIncrement();
                    boolean remove = i >= finalIndex;
                    if (remove) {
                        musicQueueRemoveListeners.forEach(l -> {
                            l.accept(i, removedMusicDetail);
                        });
                    }
                    return remove;
                });
                break;
            }
            index++;
        }
        musicQueueRefreshListeners.forEach(l -> {
            l.accept(queue);
        });
    }

    @Override
    public void sendPushMusicToQueue(MusicDetail musicDetail) {
        clientNetworkService.sendToServer(new ClientPushMusicToQueueMessage(musicDetail.getId()));
    }

    @Override
    public void sendRemoveMusicFromQueue(int index, MusicDetail musicDetail) {
        clientNetworkService.sendToServer(new ClientRemoveMusicFromQueueMessage(index, musicDetail.getId()));
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
            Artist cachedArtist = artistCache.getIfPresent(id);
            if (cachedArtist != null) {
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
    public synchronized void updateAllIdlePlaySources(List<Playlist> playlistSources, List<Album> albumSources) {
        Set<MusicCollection> toRemove = new HashSet<>();
        Set<MusicCollection> toAdd = new HashSet<>();
        Set<MusicCollection> serverIdlePlaySources = Set.copyOf(this.serverIdlePlaySources);
        for (MusicCollection musicCollection : serverIdlePlaySources) {
            //noinspection SuspiciousMethodCalls
            if (!playlistSources.contains(musicCollection) && !albumSources.contains(musicCollection)) {
                toRemove.add(musicCollection);
            }
        }
        Player player = Minecraft.getInstance().player;
        for (MusicCollection musicCollection : playlistSources) {
            if (!serverIdlePlaySources.contains(musicCollection) && !(player != null && musicCollection.getPusherInfo().getPlayerUUID().equals(player.getUUID()))) {
                toAdd.add(musicCollection);
            }
        }
        for (MusicCollection musicCollection : albumSources) {
            if (!serverIdlePlaySources.contains(musicCollection) && !(player != null && musicCollection.getPusherInfo().getPlayerUUID().equals(player.getUUID()))) {
                toAdd.add(musicCollection);
            }
        }
        this.serverIdlePlaySources.removeAll(toRemove);
        this.serverIdlePlaySources.addAll(toAdd);
        toRemove.forEach(musicCollection -> {
            serverIdlePlaySourceChangeListeners.forEach((listener) -> listener.accept(musicCollection));
            serverIdlePlaySourceRemoveListeners.forEach((listener) -> listener.accept(musicCollection));
        });
        toAdd.forEach(musicCollection -> {
            serverIdlePlaySourceChangeListeners.forEach((listener) -> listener.accept(musicCollection));
            serverIdlePlaySourceAddListeners.forEach((listener) -> listener.accept(musicCollection));
        });
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
                            loadUserAlbums(ignoreCache).thenAccept(currentUserCollections::setSubscribedAlbums),
                            loadUserArtists(ignoreCache).thenAccept(currentUserCollections::setSubscribedArtists)
                    ).thenApply(v -> currentUserCollections);
        }
    }

    @RegisterMark
    public static class RegisterImpl implements ClientRegister {
        @Override
        public void register() {
            LoginService.getInstance().getLoginCompleteListeners().add((loginCookieInfo) -> {
                MusicService.getInstance().loadIdlePlaySourceFromConfig();
            });
            IClientEventService.getInstance().registerClientPlayerQuit((player) -> {
                MusicHud.EXECUTOR.execute(MusicService::resetCurrentMusicStatus);
            });
        }
    }

}
