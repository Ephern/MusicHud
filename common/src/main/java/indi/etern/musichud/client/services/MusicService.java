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
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.config.ProfileConfigData;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.ClientRegister;
import indi.etern.musichud.interfaces.IClientEventService;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.payloads.pushMessages.c2s.*;
import indi.etern.musichud.network.payloads.requestResponseCycle.*;
import indi.etern.musichud.throwable.ApiException;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class MusicService {
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
    private final Set<MusicCollection> localIdlePlaySources = new HashSet<>();
    @Getter
    private final List<Consumer<MusicCollection>> localIdlePlaySourceAddListeners = new ArrayList<>();
    @Getter
    private final List<Consumer<MusicCollection>> localIdlePlaySourceRemoveListeners = new ArrayList<>();
    @Getter
    private final List<Consumer<MusicCollection>> localIdlePlaySourceChangeListeners = new ArrayList<>();
    @Getter
    private final Set<MusicCollection> serverIdlePlaySources = new HashSet<>();
    @Getter
    private final List<Consumer<MusicCollection>> serverIdlePlaySourceAddListeners = new ArrayList<>();
    @Getter
    private final List<Consumer<MusicCollection>> serverIdlePlaySourceRemoveListeners = new ArrayList<>();
    @Getter
    private final List<Consumer<MusicCollection>> serverIdlePlaySourceChangeListeners = new ArrayList<>();
    @Getter
    private final Queue<MusicDetail> musicQueue = new ArrayDeque<>();
    @Getter
    private final List<Consumer<Queue<MusicDetail>>> musicQueueRefreshListeners = new ArrayList<>();
    @Getter
    private final List<Consumer<MusicDetail>> musicQueuePushListeners = new ArrayList<>();
    @Getter
    private final List<BiConsumer<Integer, MusicDetail>> musicQueueRemoveListeners = new ArrayList<>();
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

    public CompletableFuture<? extends MusicCollection> loadIdlePlaySource(Class<?> type, long id) {
        if (type.equals(Album.class)) {
            return loadAlbumDetail(id, false);
        } else if (type.equals(Playlist.class)) {
            return loadPlaylistDetail(id, false);
        }
        return null;
    }

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

    public void addToIdlePlaySource(MusicCollection idlePlaySourceCollection) {
        PusherInfo pusherInfo = idlePlaySourceCollection.getPusherInfo();
        MusicCollection collection;
        if (pusherInfo != null && pusherInfo != PusherInfo.EMPTY) {
            collection = idlePlaySourceCollection.copyWithPusherInfo(PusherInfo.EMPTY);
        } else {
            collection = idlePlaySourceCollection;
        }
        localIdlePlaySources.add(collection);
        localIdlePlaySourceAddListeners.forEach(l -> l.accept(collection));
        localIdlePlaySourceChangeListeners.forEach(l -> l.accept(collection));
        IdlePlaySource idlePlaySource = new IdlePlaySource(collection.getId(), collection.getClass());
        profileConfigData.getIdlePlaySources().add(idlePlaySource);
        profileConfigData.saveToConfig();
        clientNetworkService.sendToServer(new AddToIdlePlaySourceMessage(idlePlaySource));
    }

    public void removeFromIdlePlaySource(MusicCollection collection) {
        localIdlePlaySources.removeIf(c -> c.getId() == collection.getId());
        localIdlePlaySourceRemoveListeners.forEach(l -> l.accept(collection));
        localIdlePlaySourceChangeListeners.forEach(l -> l.accept(collection));
        IdlePlaySource idlePlaySource = new IdlePlaySource(collection.getId(), collection.getClass());
        profileConfigData.getIdlePlaySources().remove(idlePlaySource);
        profileConfigData.saveToConfig();
        clientNetworkService.sendToServer(new RemoveFromIdlePlaySourceMessage(idlePlaySource));
    }

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

    public void sendPushMusicToQueue(MusicDetail musicDetail) {
        clientNetworkService.sendToServer(new ClientPushMusicToQueueMessage(musicDetail.getId()));
    }

    public void sendRemoveMusicFromQueue(int index, MusicDetail musicDetail) {
        clientNetworkService.sendToServer(new ClientRemoveMusicFromQueueMessage(index, musicDetail.getId()));
    }

    public synchronized void switchMusic(MusicDetail musicDetail, MusicDetail nextIdleMusicDetail, ZonedDateTime serverStartTime, String message) {
        if (clientConfig.getEnable()) {
            if (!message.isEmpty()) {
                MuiModApi.postToUiThread(() -> {
                    //noinspection UnstableApiUsage
                    Context context = UIManager.getInstance().getDecorView().getContext();
                    ToastUtil.show(Toast.makeText(context, message, Toast.LENGTH_SHORT));
                });
            }
            NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
            if (!musicDetail.equals(MusicDetail.NONE)) {
                ImageUtils.downloadAsync(musicDetail.getAlbum().getThumbnailPicUrl(200));
                StreamAudioPlayer streamAudioPlayer = StreamAudioPlayer.getInstance();
                nowPlayingInfo.switchMusicInfo(musicDetail, nextIdleMusicDetail);
                streamAudioPlayer.playAsync(musicDetail, serverStartTime)
                        .thenAccept(nowPlayingInfo::startAt)
                        .exceptionally(e -> {
                            return null;//TODO display error in hud
                        });
            } else {
                nowPlayingInfo.switchMusicInfo(musicDetail, nextIdleMusicDetail);
                nowPlayingInfo.startAt(null);
//                nowPlayingInfo.switchMusic(MusicDetail.NONE,MusicDetail.NONE,null);
                StreamAudioPlayer streamAudioPlayer = StreamAudioPlayer.getInstance();
                streamAudioPlayer.stop();
            }
        }
    }

    public CompletableFuture<Artist> loadArtist(long id) {
        CompletableFuture<Artist> future = new CompletableFuture<>();
        GetArtistDetailResponse.setReceiver(id, future::complete);
        clientNetworkService.sendToServer(new GetArtistDetailRequest(id));
        return future;
    }

    public CompletableFuture<List<MusicDetail>> loadArtistMusic(long id, int offset) {
        CompletableFuture<List<MusicDetail>> future = new CompletableFuture<>();
        GetArtistMoreMusicResponse.RequestData requestData = new GetArtistMoreMusicResponse.RequestData(id, offset);
        GetArtistMoreMusicResponse.setReceiver(requestData, future::complete);
        clientNetworkService.sendToServer(new GetArtistMoreMusicRequest(id, offset));
        return future;
    }

    public void voteForSkipCurrent() {
        if (NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail() != null) {
            clientNetworkService.sendToServer(new VoteSkipCurrentMusicMessage(NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail().getId()));
        }
    }

    public void keyBindsVoteSkipCurrent() {
        long currentTimeMillis = System.currentTimeMillis();
        MusicDetail currentlyPlayingMusicDetail = NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail();
        if (currentlyPlayingMusicDetail != null && currentlyPlayingMusicDetail != MusicDetail.NONE) {
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

    public void updateAllIdlePlaySources(List<Playlist> playlistSources, List<Album> albumSources) {
        Set<MusicCollection> toRemove = new HashSet<>();
        Set<MusicCollection> toAdd = new HashSet<>();
        Set<MusicCollection> serverIdlePlaySources = Set.copyOf(this.serverIdlePlaySources);
        for (MusicCollection musicCollection : serverIdlePlaySources) {
            //noinspection SuspiciousMethodCalls
            if (!playlistSources.contains(musicCollection) && !albumSources.contains(musicCollection)) {
                toRemove.add(musicCollection);
                serverIdlePlaySourceChangeListeners.forEach((listener) -> listener.accept(musicCollection));
                serverIdlePlaySourceRemoveListeners.forEach((listener) -> listener.accept(musicCollection));
            }
        }
        Player player = Minecraft.getInstance().player;
        for (MusicCollection musicCollection : playlistSources) {
            if (!serverIdlePlaySources.contains(musicCollection) && !(player != null && musicCollection.getPusherInfo().getPlayerUUID().equals(player.getUUID()))) {
                toAdd.add(musicCollection);
                serverIdlePlaySourceChangeListeners.forEach((listener) -> listener.accept(musicCollection));
                serverIdlePlaySourceAddListeners.forEach((listener) -> listener.accept(musicCollection));
            }
        }
        for (MusicCollection musicCollection : albumSources) {
            if (!serverIdlePlaySources.contains(musicCollection) && !(player != null && musicCollection.getPusherInfo().getPlayerUUID().equals(player.getUUID()))) {
                toAdd.add(musicCollection);
                serverIdlePlaySourceChangeListeners.forEach((listener) -> listener.accept(musicCollection));
                serverIdlePlaySourceAddListeners.forEach((listener) -> listener.accept(musicCollection));
            }
        }
        this.serverIdlePlaySources.removeAll(toRemove);
        this.serverIdlePlaySources.addAll(toAdd);
    }

    public CompletableFuture<List<Playlist>> loadUserPlaylists() {
        CompletableFuture<List<Playlist>> completableFuture = new CompletableFuture<>();
        if (LoginService.getInstance().isLogined()) {
            MusicHud.EXECUTOR.execute(() -> {
                clientNetworkService.sendToServer(GetUserPlaylistRequest.REQUEST);
                Thread pendingThread = Thread.currentThread();
                GetUserPlaylistResponse.setConsumer(value -> {
                    completableFuture.complete(value);
                    pendingThread.interrupt();
                });
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

    public CompletableFuture<List<Album>> loadUserAlbums() {
        CompletableFuture<List<Album>> completableFuture = new CompletableFuture<>();
        if (LoginService.getInstance().isLogined()) {
            MusicHud.EXECUTOR.execute(() -> {
                clientNetworkService.sendToServer(GetUserAlbumsRequest.REQUEST);
                Thread pendingThread = Thread.currentThread();
                GetUserAlbumsResponse.setConsumer(value -> {
                    completableFuture.complete(value);
                    pendingThread.interrupt();
                });
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

    public CompletableFuture<List<Artist>> loadUserArtists() {
        CompletableFuture<List<Artist>> completableFuture = new CompletableFuture<>();
        if (LoginService.getInstance().isLogined()) {
            MusicHud.EXECUTOR.execute(() -> {
                clientNetworkService.sendToServer(GetUserArtistsRequest.REQUEST);
                Thread pendingThread = Thread.currentThread();
                GetUserArtistsResponse.setConsumer(value -> {
                    completableFuture.complete(value);
                    pendingThread.interrupt();
                });
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

    @RegisterMark
    public static class RegisterImpl implements ClientRegister {
        public static void reset() {
            if (instance != null) {
                instance.switchMusic(MusicDetail.NONE, MusicDetail.NONE, null, "");
                instance.idlePlaySourceLoaded = false;
                instance.musicQueue.clear();
                instance.localIdlePlaySourceAddListeners.clear();
                instance.localIdlePlaySourceRemoveListeners.clear();
                instance.localIdlePlaySourceChangeListeners.clear();
                instance.musicQueueRefreshListeners.clear();
                instance.musicQueuePushListeners.clear();
                instance.musicQueueRemoveListeners.clear();
            }
            if (HudRendererManager.isLoaded()) {
                HudRendererManager.getInstance().reset();
            }
            NowPlayingInfo.getInstance().switchMusicInfo(MusicDetail.NONE, MusicDetail.NONE);
        }

        @Override
        public void register() {
            LoginService.getInstance().getLoginCompleteListeners().add((loginCookieInfo) -> {
                MusicService.getInstance().loadIdlePlaySourceFromConfig();
            });
            IClientEventService.getInstance().registerClientPlayerQuit((player) -> {
                reset();
            });
        }
    }
}