package indi.etern.musichud.client.services.music;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.widget.Toast;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.result.MessagedResult;
import indi.etern.musichud.beans.state.IIdlePlaySourceState;
import indi.etern.musichud.beans.state.IMusicTrackState;
import indi.etern.musichud.beans.state.ISubscribeState;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.cloud.CloudTracksPage;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.PlaybackTask;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.dto.UserCollections;
import indi.etern.musichud.client.interfaces.IClientEventService;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.services.music.states.*;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.connection.ConnectionStateMachine;
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
import indi.etern.musichud.utils.IClientDistUtil;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
    private volatile CompletableFuture<UserCollections> loadingCollectionsFuture;
    private final ConcurrentHashMap<Long, CompletableFuture<Playlist>> loadingPlaylists = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CompletableFuture<Album>> loadingAlbums = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CompletableFuture<Artist>> loadingArtists = new ConcurrentHashMap<>();

    private static boolean isPlaylistComplete(Playlist playlist) {
        return playlist.getMusicDetails() != null
                && (!playlist.getMusicDetails().isEmpty() || playlist.getMusicTrackCount() == 0);
    }

    private static boolean isAlbumComplete(Album album) {
        return album.getMusicDetails() != null
                && (!album.getMusicDetails().isEmpty() || album.getMusicTrackCount() == 0);
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
        MusicService inst = instance;
        if (inst != null) {
            // Hold the instance monitor so the queue clear cannot interleave with the
            // peek/iterate patterns in switchMusic/refreshQueue on network threads
            synchronized (inst) {
                inst.switchMusic(Traceable.of(MusicDetail.NONE), Traceable.of(MusicDetail.NONE), null, "");
                inst.getIdlePlaySourceState().local().reset();
                inst.musicQueue.clear();
            }
        }
        if (HudRendererManager.isLoaded()) {
            HudRendererManager.getInstance().reset();
        }
    }

    /**
     * Atomically peeks the head of the music queue (null when empty). The queue is a plain
     * ArrayDeque, so external readers must go through this instead of an unsynchronized
     * isEmpty+peek pair.
     */
    public synchronized QueueItem peekQueueItem() {
        return musicQueue.peek();
    }

    private static boolean sameItem(QueueItem a, QueueItem b) {
        return a.queueUniqueID().equals(b.queueUniqueID());
    }

    private void pushDownToUserCollections(Playlist full) {
        if (full.getMusicDetails() == null || full.getMusicDetails().isEmpty()) {
            return;
        }
        UserCollections userCollections = currentUserCollections;
        if (userCollections == null) {
            return;
        }
        UserCategoryPlaylists categoryPlaylists = userCollections.getUserCategoryPlaylists();
        if (categoryPlaylists == null) {
            return;
        }
        Playlist likeList = categoryPlaylists.getLikeList();
        if (likeList != null && likeList.equalsLoose(full)) {
            likeList.updateFrom(full, true);
        }
        categoryPlaylists.getCreatedPlaylist().stream()
                .filter(playlist -> playlist.equalsLoose(full))
                .forEach(playlist -> playlist.updateFrom(full, true));
        categoryPlaylists.getSubscribedPlaylist().stream()
                .filter(playlist -> playlist.equalsLoose(full))
                .forEach(playlist -> playlist.updateFrom(full, true));
    }

    private void pushDownToUserCollections(Album full) {
        if (full.getMusicDetails() == null || full.getMusicDetails().isEmpty()) {
            return;
        }
        UserCollections userCollections = currentUserCollections;
        if (userCollections == null || userCollections.getSubscribedAlbums() == null) {
            return;
        }
        userCollections.getSubscribedAlbums().stream()
                .filter(album -> album.equalsLoose(full))
                .forEach(album -> album.updateFrom(full, true));
    }

    private void syncUserCollectionsDownFromCaches() {
        UserCollections userCollections = currentUserCollections;
        if (userCollections == null) {
            return;
        }
        if (userCollections.getUserCategoryPlaylists() != null) {
            UserCategoryPlaylists categoryPlaylists = userCollections.getUserCategoryPlaylists();
            Playlist likeList = categoryPlaylists.getLikeList();
            if (likeList != null) {
                Playlist cached = playlistsCache.getIfPresent(PlaylistCacheKey.of(likeList, Profile.getCurrent().getUserId()));
                if (cached != null && !cached.getMusicDetails().isEmpty()) {
                    likeList.updateFrom(cached, true);
                }
            }
            categoryPlaylists.getCreatedPlaylist().forEach(playlist -> {
                Playlist cached = playlistsCache.getIfPresent(PlaylistCacheKey.of(playlist, Profile.getCurrent().getUserId()));
                if (cached != null && !cached.getMusicDetails().isEmpty()) {
                    playlist.updateFrom(cached, true);
                }
            });
            categoryPlaylists.getSubscribedPlaylist().forEach(playlist -> {
                Playlist cached = playlistsCache.getIfPresent(PlaylistCacheKey.of(playlist, Profile.getCurrent().getUserId()));
                if (cached != null && !cached.getMusicDetails().isEmpty()) {
                    playlist.updateFrom(cached, true);
                }
            });
        }
        if (userCollections.getSubscribedAlbums() != null) {
            userCollections.getSubscribedAlbums().forEach(album -> {
                Album cached = albumsCache.getIfPresent(album.getId());
                if (cached != null && !cached.getMusicDetails().isEmpty()) {
                    album.updateFrom(cached, true);
                }
            });
        }
    }

    @Override
    public CompletableFuture<Playlist> loadPlaylistDetail(long id, boolean ignoreCache) {
        CompletableFuture<Playlist> inProgress = loadingPlaylists.get(id);
        if (inProgress != null) {
            return inProgress;
        }
        Playlist cached = playlistsCache.getIfPresent(PlaylistCacheKey.of(id, -1));
        if (cached == null) {
            cached = playlistsCache.getIfPresent(PlaylistCacheKey.of(id, Profile.getCurrent().getUserId()));
        }
        Playlist finalCached = cached;
        if (!ignoreCache && finalCached != null && isPlaylistComplete(finalCached)) {
//            pushDownToUserCollections(cached);
            return CompletableFuture.completedFuture(finalCached);
        }
        CompletableFuture<Playlist> future = RequestResponseManager.send(
                        new GetPlaylistDetailRequest(id, ignoreCache),
                        GetPlaylistDetailResponse.class,
                        Duration.ofSeconds(10))
                .thenApply(response -> {
                    Playlist loaded = response.getPlaylist();
                    Playlist result;
                    if (finalCached != null) {
                        finalCached.updateFrom(loaded, true);
                        result = finalCached;
                    } else {
                        playlistsCache.put(PlaylistCacheKey.of(loaded, Profile.getCurrent().getUserId()), loaded);
                        result = loaded;
                    }
                    pushDownToUserCollections(result);
                    return result;
                }).exceptionally(e -> Playlist.EMPTY);
        loadingPlaylists.put(id, future);
        future.whenComplete((r, e) -> loadingPlaylists.remove(id, future));
        return future;
    }

    @Override
    public CompletableFuture<Album> loadAlbumDetail(long id, boolean ignoreCache) {
        CompletableFuture<Album> inProgress = loadingAlbums.get(id);
        if (inProgress != null) {
            return inProgress;
        }
        Album cached = albumsCache.getIfPresent(id);
        if (!ignoreCache && cached != null && isAlbumComplete(cached)) {
//                pushDownToUserCollections(cached);
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<Album> future = RequestResponseManager.send(
                        new GetAlbumDetailRequest(id, ignoreCache),
                        GetAlbumDetailResponse.class,
                        Duration.ofSeconds(10))
                .thenApply(response -> {
                    Album loaded = response.getAlbum();
                    Album result;
                    if (cached != null) {
                        cached.updateFrom(loaded, true);
                        result = cached;
                    } else {
                        albumsCache.put(id, loaded);
                        result = loaded;
                    }
                    pushDownToUserCollections(result);
                    return result;
                }).exceptionally(e -> Album.NONE);
        loadingAlbums.put(id, future);
        future.whenComplete((r, e) -> loadingAlbums.remove(id, future));
        return future;
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

    @Override
    public void sendPushMusicToQueue(Traceable<Long> music) {
        clientNetworkService.sendToServer(new ClientPushMusicToQueueMessage(music));
    }

    @Override
    public void sendRemoveMusicFromQueue(QueueItem item) {
        clientNetworkService.sendToServer(new ClientRemoveMusicFromQueueMessage(item.musicDetail().value().getId(), item.queueUniqueID()));
    }

    @Override
    public synchronized void switchMusic(Traceable<MusicDetail> musicDetail, Traceable<MusicDetail> nextIdleMusicDetail, ZonedDateTime serverStartTime, String message) {
        if (clientConfig.getEnable()) {
            NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
            QueueItem head = musicQueue.peek();
            if (head != null) {// preload image
                MusicDetail peek = head.musicDetail().value();
                Album album = peek.getAlbum();
                ImageUtils.downloadAsync(album.getImageThumbnailUrl(240));
                HudRendererManager.getInstance().preloadAlbumImage(peek.getAlbum());
            } else if (nextIdleMusicDetail != null && !nextIdleMusicDetail.value().equals(MusicDetail.NONE)) {
                Album album = nextIdleMusicDetail.value().getAlbum();
                ImageUtils.downloadAsync(album.getImageThumbnailUrl(240));
                HudRendererManager.getInstance().preloadAlbumImage(nextIdleMusicDetail.value().getAlbum());
            }
            if (!message.isEmpty()) {
                MuiModApi.postToUiThread(() -> {
                    //noinspection UnstableApiUsage
                    Context context = UIManager.getInstance().getDecorView().getContext();
                    ToastUtil.show(Toast.makeText(context, message, Toast.LENGTH_SHORT));
                });
            }
            if (!musicDetail.value().equals(MusicDetail.NONE)) {
                Album album = musicDetail.value().getAlbum();
                ImageUtils.downloadAsync(album.getImageThumbnailUrl(240));
                StreamAudioPlayer streamAudioPlayer = StreamAudioPlayer.getInstance();
                nowPlayingInfo.switchMusicInfo(musicDetail, nextIdleMusicDetail);
                PlaybackTask task = streamAudioPlayer.obtainTaskFor(musicDetail, serverStartTime);
                streamAudioPlayer.play(task)
                        .thenAccept(nowPlayingInfo::startAt)
                        .exceptionally(e -> null);
            } else {
                nowPlayingInfo.switchMusicInfo(musicDetail, nextIdleMusicDetail);
                nowPlayingInfo.startAt(null);
                StreamAudioPlayer streamAudioPlayer = StreamAudioPlayer.getInstance();
                streamAudioPlayer.stop();
            }
        }
    }

    @Override
    public CompletableFuture<MessagedResult<Void>> rotateNextToPlay() {
        return RequestResponseManager.send(
                        new RotateNextToPlayRequest(),
                        RotateNextToPlayResponse.class,
                        Duration.ofSeconds(20))
                .thenApply(RotateNextToPlayResponse::getResult)
                .exceptionally(e -> MessagedResult.fail(MusicHud.MOD_ID + ".text.rotateNextFailed", null));
    }

    @Override
    public void updateNextToPlay(Traceable<MusicDetail> nextIdleMusicDetail) {
        if (!clientConfig.getEnable()) {
            return;
        }
        Traceable<MusicDetail> next = Objects.requireNonNullElse(nextIdleMusicDetail, Traceable.of(MusicDetail.NONE));
        MusicDetail nextDetail = next.value();
        if (!nextDetail.equals(MusicDetail.NONE)) {
            ImageUtils.downloadAsync(nextDetail.getAlbum().getImageThumbnailUrl(240));
            HudRendererManager.getInstance().preloadAlbumImage(nextDetail.getAlbum());
        }
        NowPlayingInfo.getInstance().updateNextToPlayIdle(next);
    }

    @Override
    public CompletableFuture<Artist> loadArtist(long id, boolean ignoreCache) {
        CompletableFuture<Artist> inProgress = loadingArtists.get(id);
        if (inProgress != null) {
            return inProgress;
        }
        if (!ignoreCache) {
            Artist cachedArtist = artistsCache.getIfPresent(id);
            if (cachedArtist != null && cachedArtist.getMusicDetails() != null) {
                return CompletableFuture.completedFuture(cachedArtist);
            }
        }
        CompletableFuture<Artist> future = RequestResponseManager.send(
                        new GetArtistDetailRequest(id),
                        GetArtistDetailResponse.class,
                        Duration.ofSeconds(10))
                .thenApply(GetArtistDetailResponse::getArtist)
                .exceptionally(e -> Artist.UNKNOWN);
        loadingArtists.put(id, future);
        future.whenComplete((r, e) -> loadingArtists.remove(id, future));
        return future;
    }

    @Override
    public CompletableFuture<List<MusicDetail>> loadArtistMusic(long id, int offset) {
        return RequestResponseManager.send(
                        new GetArtistMoreMusicRequest(id, offset),
                        GetArtistMoreMusicResponse.class,
                        Duration.ofSeconds(10))
                .thenApply(GetArtistMoreMusicResponse::getMusicDetails)
                .exceptionally(e -> List.of());
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
                            || ConnectionStateMachine.getState() == ConnectionStateMachine.ConnectionState.ISOLATED
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
                            || ConnectionStateMachine.getState() == ConnectionStateMachine.ConnectionState.ISOLATED
                            ? I18n.get(MusicHud.MOD_ID + ".text.confirmSkip")
                            : I18n.get(MusicHud.MOD_ID + ".text.confirmVoteForSkip");
                    ToastUtil.show(Toast.makeText(context, s, Toast.LENGTH_SHORT));
                });
            }
        }
    }

    protected CompletableFuture<UserCategoryPlaylists> loadUserPlaylists(boolean ignoreCache) {
        if (!LoginService.getInstance().isLogined()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Cannot call AccountService.loadUserPlaylists when logined as anonymous"));
        }
        return RequestResponseManager.send(
                        new GetUserPlaylistRequest(ignoreCache),
                        GetUserPlaylistResponse.class,
                        Duration.ofSeconds(10))
                .thenApply(GetUserPlaylistResponse::getPlaylists)
                .thenApply(playlists -> {
                    UserCollections userCollections = currentUserCollections;
                    if (userCollections != null && userCollections.isLoaded()) {
                        userCollections.syncUserCategoryPlaylists(playlists);
                    }
                    return playlists;
                })
                .exceptionally(e -> UserCategoryPlaylists.EMPTY);
    }

    protected CompletableFuture<LinkedHashSet<Album>> loadUserAlbums(boolean ignoreCache) {
        if (!LoginService.getInstance().isLogined()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Cannot call AccountService.loadUserAlbums when logined as anonymous"));
        }
        return RequestResponseManager.send(
                        new GetUserAlbumsRequest(ignoreCache),
                        GetUserAlbumsResponse.class,
                        Duration.ofSeconds(10))
                .thenApply(GetUserAlbumsResponse::getAlbums)
                .thenApply(albums -> {
                    UserCollections userCollections = currentUserCollections;
                    if (userCollections != null && userCollections.isLoaded()) {
                        userCollections.syncSubscribedAlbums(albums);
                    }
                    return albums;
                })
                .exceptionally(e -> new LinkedHashSet<>(0));
    }

    protected CompletableFuture<LinkedHashSet<Artist>> loadUserArtists(boolean ignoreCache) {
        if (!LoginService.getInstance().isLogined()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Cannot call AccountService.loadUserArtists when logined as anonymous"));
        }
        return RequestResponseManager.send(
                        new GetUserArtistsRequest(ignoreCache),
                        GetUserArtistsResponse.class,
                        Duration.ofSeconds(10))
                .thenApply(GetUserArtistsResponse::getArtists)
                .thenApply(artists -> {
                    UserCollections userCollections = currentUserCollections;
                    if (userCollections != null && userCollections.isLoaded()) {
                        userCollections.syncSubscribedArtists(artists);
                    }
                    return artists;
                })
                .exceptionally(e -> new LinkedHashSet<>(0));
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
        CompletableFuture<UserCollections> inProgress = loadingCollectionsFuture;
        if (inProgress != null) {
            return inProgress;
        }
        UserCollections existing = currentUserCollections;
        if (existing != null && existing.isLoaded()) {
            if (ignoreCache) {
                return rememberCollectionsLoad(CompletableFuture.allOf(
                        loadUserPlaylists(true),
                        loadUserAlbums(true),
                        loadUserArtists(true)
                ).thenApply(v -> {
                    syncUserCollectionsDownFromCaches();
                    return existing;
                }));
            }
            return CompletableFuture.completedFuture(existing);
        } else {
            currentUserCollections = new UserCollections();
            return rememberCollectionsLoad(CompletableFuture.allOf(
                    loadUserPlaylists(ignoreCache).thenAccept(currentUserCollections::setUserCategoryPlaylists),
                    loadUserAlbums(ignoreCache)
                            .thenAccept(subscribedAlbums ->
                                    currentUserCollections.setSubscribedAlbums(new ObservableSequencedSet<>(subscribedAlbums))),
                    loadUserArtists(ignoreCache)
                            .thenAccept(subscribedArtists ->
                                    currentUserCollections.setSubscribedArtists(new ObservableSequencedSet<>(subscribedArtists)))
            ).thenApply(v -> {
                currentUserCollections.setLoaded(true);
                syncUserCollectionsDownFromCaches();
                return currentUserCollections;
            }));
        }
    }

    private CompletableFuture<UserCollections> rememberCollectionsLoad(CompletableFuture<UserCollections> future) {
        loadingCollectionsFuture = future;
        future.whenComplete((r, e) -> {
            if (loadingCollectionsFuture == future) {
                loadingCollectionsFuture = null;
            }
        });
        return future;
    }

    @SuppressWarnings("unchecked")
    public <T extends MusicCollection> CompletableFuture<T> loadMusicCollectionDetail(long id, Class<T> type) {
        if (type == Album.class) {
            return (CompletableFuture<T>) loadAlbumDetail(id, false);
        } else if (type == Playlist.class) {
            return (CompletableFuture<T>) loadPlaylistDetail(id, false);
        } else {
            throw new IllegalArgumentException("Unrecognizable type");
        }
    }

    public CompletableFuture<MessagedResult<CloudTracksPage>> loadCloudTracks(int offset, int limit) {
        return RequestResponseManager.send(
                        new GetCloudTracksRequest(offset, limit),
                        GetCloudTracksResponse.class,
                        Duration.ofSeconds(15))
                .thenApply(GetCloudTracksResponse::getResult)
                .exceptionally(e -> MessagedResult.fail(CloudTracksPage.EMPTY));
    }

    /** On-demand track detail fetch; {@code cloudSource} marks the user's own cloud-drive tracks. */
    public CompletableFuture<List<MusicDetail>> loadMusicDetails(List<Long> ids, boolean cloudSource) {
        if (ids == null || ids.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return RequestResponseManager.send(
                        new GetMusicDetailsRequest(ids, cloudSource),
                        GetMusicDetailsResponse.class,
                        Duration.ofSeconds(15))
                .thenApply(response -> response.getResult().extraData())
                .exceptionally(e -> List.of());
    }

    public CompletableFuture<MessagedResult<Void>> deleteCloudTrack(long id) {
        return RequestResponseManager.send(
                        new DeleteCloudTrackRequest(id),
                        DeleteCloudTrackResponse.class,
                        Duration.ofSeconds(15))
                .thenApply(DeleteCloudTrackResponse::getResult)
                .exceptionally(e -> MessagedResult.fail(
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), null));
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
