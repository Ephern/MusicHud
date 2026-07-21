package indi.etern.musichud.client.services;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.widget.Toast;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.user.ProfileConfigData;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.interfaces.IClientEventService;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.ClientRegister;
import indi.etern.musichud.interfaces.IClientMusicService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.payloads.pushMessages.c2s.*;
import indi.etern.musichud.network.payloads.requestResponseCycle.*;
import indi.etern.musichud.throwable.ApiException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
            .maximumSize(20)
            .build();
    private static final Cache<Long, Album> albumCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(20)
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
    private boolean initialSyncReceived = false;
    long lastPressTime = 0;
    @Getter
    private boolean idlePlaySourceLoaded = false;

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
        CompletableFuture<Playlist> completableFuture = new CompletableFuture<>();
        MusicHud.EXECUTOR.execute(() -> {
            GetPlaylistDetailResponse.setReceiver(id, playlist -> {
                if (playlist != null) {
                    playlistCache.put(id, playlist);
                    completableFuture.complete(playlist);
                }
            });
            clientNetworkService.sendToServer(new GetPlaylistDetailRequest(id));
        });
        return completableFuture.orTimeout(5, TimeUnit.SECONDS);
    }

    @Override
    public CompletableFuture<Album> loadAlbumDetail(long id, boolean ignoreCache) {
        if (!ignoreCache) {
            Album cachedPlaylist = albumCache.getIfPresent(id);
            if (cachedPlaylist != null) {
                return CompletableFuture.completedFuture(cachedPlaylist);
            }
        }
        CompletableFuture<Album> completableFuture = new CompletableFuture<>();
        MusicHud.EXECUTOR.execute(() -> {
            GetAlbumDetailResponse.setReceiver(id, albumInfo -> {
                albumCache.put(id, albumInfo);
                completableFuture.complete(albumInfo);
            });
            clientNetworkService.sendToServer(new GetAlbumDetailRequest(id));
        });
        return completableFuture.orTimeout(5, TimeUnit.SECONDS);
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
    public synchronized boolean checkAndResetInitialSync() {
        if (initialSyncReceived) return false;
        initialSyncReceived = true;
        switchMusic(MusicDetail.NONE, MusicDetail.NONE, null, "");
        idlePlaySourceLoaded = false;
        musicQueue.clear();
        if (HudRendererManager.isLoaded()) {
            HudRendererManager.getInstance().reset();
        }
        return true;
    }

    @Override
    public synchronized void switchMusic(MusicDetail musicDetail, MusicDetail nextIdleMusicDetail, ZonedDateTime serverStartTime, String message) {
        initialSyncReceived = true;
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
            } else {
                nowPlayingInfo.switchMusicInfo(musicDetail, nextIdleMusicDetail);
                nowPlayingInfo.startAt(null);
//                nowPlayingInfo.switchMusic(MusicDetail.NONE,MusicDetail.NONE,null);
                StreamAudioPlayer streamAudioPlayer = StreamAudioPlayer.getInstance();
                streamAudioPlayer.stop();
            }
        }
    }

    @Override
    public CompletableFuture<Artist> loadArtist(long id) {
        CompletableFuture<Artist> future = new CompletableFuture<>();
        GetArtistDetailResponse.setReceiver(id, future::complete);
        clientNetworkService.sendToServer(new indi.etern.musichud.network.payloads.requestResponseCycle.GetArtistDetailRequest(id));
        return future;
    }

    @Override
    public CompletableFuture<List<MusicDetail>> loadArtistMusic(long id, int offset) {
        CompletableFuture<List<MusicDetail>> future = new CompletableFuture<>();
        GetArtistMoreMusicResponse.RequestData requestData = new GetArtistMoreMusicResponse.RequestData(id, offset);
        GetArtistMoreMusicResponse.setReceiver(requestData, future::complete);
        clientNetworkService.sendToServer(new GetArtistMoreMusicRequest(id, offset));
        return future;
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
    public CompletableFuture<List<Playlist>> loadUserPlaylists() {
        CompletableFuture<List<Playlist>> completableFuture = new CompletableFuture<>();
        if (LoginService.getInstance().isLogined()) {
            MusicHud.EXECUTOR.execute(() -> {
                Thread pendingThread = Thread.currentThread();
                GetUserPlaylistResponse.setConsumer(value -> {
                    completableFuture.complete(value);
                    pendingThread.interrupt();
                });
                clientNetworkService.sendToServer(GetUserPlaylistRequest.REQUEST);
                try {
                    Thread.sleep(Duration.of(5, ChronoUnit.SECONDS));
                    completableFuture.completeExceptionally(new ApiException());
                } catch (InterruptedException ignored) {
                }
            });
        } else {
            completableFuture.completeExceptionally(new IllegalStateException("Cannot call AccountService.loadUserPlaylists when logined as anonymous"));
        }
        return completableFuture;
    }

    @Override
    public CompletableFuture<List<Album>> loadUserAlbums() {
        CompletableFuture<List<Album>> completableFuture = new CompletableFuture<>();
        if (LoginService.getInstance().isLogined()) {
            MusicHud.EXECUTOR.execute(() -> {
                Thread pendingThread = Thread.currentThread();
                GetUserAlbumsResponse.setConsumer(value -> {
                    completableFuture.complete(value);
                    pendingThread.interrupt();
                });
                clientNetworkService.sendToServer(GetUserAlbumsRequest.REQUEST);
                try {
                    Thread.sleep(Duration.of(5, ChronoUnit.SECONDS));
                    completableFuture.completeExceptionally(new ApiException());
                } catch (InterruptedException ignored) {
                }
            });
        } else {
            completableFuture.completeExceptionally(new IllegalStateException("Cannot call AccountService.loadUserAlbums when logined as anonymous"));
        }
        return completableFuture;
    }

    @Override
    public CompletableFuture<List<Artist>> loadUserArtists() {
        CompletableFuture<List<Artist>> completableFuture = new CompletableFuture<>();
        if (LoginService.getInstance().isLogined()) {
            MusicHud.EXECUTOR.execute(() -> {
                Thread pendingThread = Thread.currentThread();
                GetUserArtistsResponse.setConsumer(value -> {
                    completableFuture.complete(value);
                    pendingThread.interrupt();
                });
                clientNetworkService.sendToServer(GetUserArtistsRequest.REQUEST);
                try {
                    Thread.sleep(Duration.of(5, ChronoUnit.SECONDS));
                    completableFuture.completeExceptionally(new ApiException());
                } catch (InterruptedException ignored) {
                }
            });
        } else {
            completableFuture.completeExceptionally(new IllegalStateException("Cannot call AccountService.loadUserArtists when logined as anonymous"));
        }
        return completableFuture;
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

    @Override
    public CompletableFuture<Artist> loadArtistDetailAsync(Artist artist) {
        List<MusicDetail> musicDetails = artist.getMusicDetails();
        if (musicDetails == null || musicDetails.isEmpty()) {
            return loadArtist(artist.getId());
        } else return CompletableFuture.completedFuture(artist);
    }

    @Override
    public CompletableFuture<List<MusicDetail>> loadMoreMusicOfArtist(Artist artist) {
        CompletableFuture<List<MusicDetail>> future = new CompletableFuture<>();
        MusicService.getInstance().loadArtistMusic(artist.getId(), artist.getMusicDetails().size()).thenAccept(musicDetails1 -> {
            artist.getMusicDetails().addAll(musicDetails1);
            future.complete(musicDetails1);
        });
        return future;
    }

    @Override
    public CompletionStage<Collection<MusicDetail>> loadMoreMusicOfCollection(MusicCollection musicCollection, boolean ignoreCache) {
        CompletableFuture<Collection<MusicDetail>> future = new CompletableFuture<>();
        if (musicCollection instanceof Album album) {
            loadAlbumDetail(album.getId(), ignoreCache).thenAccept(albumInfo -> future.complete(albumInfo.getMusicDetails()));
        } else if (musicCollection instanceof Playlist playlist) {
            loadPlaylistDetail(playlist.getId(), ignoreCache).thenAccept(playlist1 -> future.complete(playlist1.getTracks()));
        }
        return future;
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