package indi.etern.musichud.client.services;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.networking.NetworkManager;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.UIManager;
import icyllis.modernui.widget.Toast;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.login.LoginType;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.client.config.ClientConfigDefinition;
import indi.etern.musichud.client.config.ProfileConfigData;
import indi.etern.musichud.client.music.NowPlayingInfo;
import indi.etern.musichud.client.music.StreamAudioPlayer;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import indi.etern.musichud.interfaces.ClientRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.network.pushMessages.c2s.*;
import indi.etern.musichud.network.requestResponseCycle.*;
import indi.etern.musichud.throwable.ApiException;
import lombok.Getter;
import net.minecraft.client.resources.language.I18n;
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
    private static final Cache<Long, AlbumInfo> albumCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(20)
            .build();
    private static final ProfileConfigData profileConfigData = ProfileConfigData.getInstance();
    private static volatile MusicService instance;
    @Getter
    private final Set<MusicCollection> idlePlaySources = new HashSet<>();
    @Getter
    private final List<Consumer<MusicCollection>> idlePlaySourceAddListeners = new ArrayList<>();
    @Getter
    private final List<Consumer<MusicCollection>> idlePlaylistRemoveListeners = new ArrayList<>();
    @Getter
    private final List<Consumer<MusicCollection>> idlePlaySourceChangeListeners = new ArrayList<>();
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
                            NetworkManager.sendToServer(new AddToIdlePlaySourceMessage(idlePlaySource));
                            loadIdlePlaySource(idlePlaySource.getType(), idlePlaySource.getId()).thenAcceptAsync(musicCollection -> {
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
        if (type.equals(AlbumInfo.class)) {
            return loadAlbumDetail(id);
        } else if (type.equals(Playlist.class)) {
            return loadPlaylistDetail(id);
        }
        return null;
    }

    public CompletableFuture<Playlist> loadPlaylistDetail(long id) {
        Playlist cachedPlaylist = playlistCache.getIfPresent(id);
        if (cachedPlaylist != null) {
            return CompletableFuture.completedFuture(cachedPlaylist);
        }
        CompletableFuture<Playlist> completableFuture = new CompletableFuture<>();
        MusicHud.EXECUTOR.execute(() -> {
            NetworkManager.sendToServer(new GetPlaylistDetailRequest(id));
            Thread pendingThread = Thread.currentThread();
            GetPlaylistDetailResponse.setReceiver(id, playlist -> {
                if (playlist != null) {
                    playlistCache.put(id, playlist);
                    completableFuture.complete(playlist);
                    pendingThread.interrupt();
                }
            });
            try {
                Thread.sleep(Duration.of(5, ChronoUnit.SECONDS));
                completableFuture.completeExceptionally(new ApiException());
            } catch (InterruptedException ignored) {
            }
        });
        return completableFuture;
    }

    public CompletableFuture<AlbumInfo> loadAlbumDetail(long id) {
        AlbumInfo cachedPlaylist = albumCache.getIfPresent(id);
        if (cachedPlaylist != null) {
            return CompletableFuture.completedFuture(cachedPlaylist);
        }
        CompletableFuture<AlbumInfo> completableFuture = new CompletableFuture<>();
        MusicHud.EXECUTOR.execute(() -> {
            NetworkManager.sendToServer(new GetAlbumDetailRequest(id));
            Thread pendingThread = Thread.currentThread();
            GetAlbumDetailResponse.setReceiver(id, albumInfo -> {
                albumCache.put(id, albumInfo);
                completableFuture.complete(albumInfo);
                pendingThread.interrupt();
            });
            try {
                Thread.sleep(Duration.of(5, ChronoUnit.SECONDS));
                completableFuture.completeExceptionally(new ApiException());
            } catch (InterruptedException ignored) {
            }
        });
        return completableFuture;
    }

    public void addToIdlePlaySource(MusicCollection collection) {
        idlePlaySources.add(collection);
        idlePlaySourceAddListeners.forEach(l -> l.accept(collection));
        idlePlaySourceChangeListeners.forEach(l -> l.accept(collection));
        IdlePlaySource idlePlaySource = new IdlePlaySource(collection.getId(), collection.getClass());
        profileConfigData.getIdlePlaySources().add(idlePlaySource);
        profileConfigData.saveToConfig();
        NetworkManager.sendToServer(new AddToIdlePlaySourceMessage(idlePlaySource));
    }

    public void removeFromIdlePlaySource(MusicCollection collection) {
        idlePlaySources.remove(collection);
        idlePlaylistRemoveListeners.forEach(l -> l.accept(collection));
        idlePlaySourceChangeListeners.forEach(l -> l.accept(collection));
        IdlePlaySource idlePlaySource = new IdlePlaySource(collection.getId(), collection.getClass());
        profileConfigData.getIdlePlaySources().remove(idlePlaySource);
        profileConfigData.saveToConfig();
        NetworkManager.sendToServer(new RemoveFromIdlePlaySourceMessage(idlePlaySource));
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
        NetworkManager.sendToServer(new ClientPushMusicToQueueMessage(musicDetail.getId()));
    }

    public void sendRemoveMusicFromQueue(int index, MusicDetail musicDetail) {
        NetworkManager.sendToServer(new ClientRemoveMusicFromQueueMessage(index, musicDetail.getId()));
    }

    public synchronized void switchMusic(MusicDetail musicDetail, ZonedDateTime serverStartTime, String message) {
        if (ClientConfigDefinition.enable.get()) {
            if (!message.isEmpty()) {
                MuiModApi.postToUiThread(() -> {
                    //noinspection UnstableApiUsage
                    Context context = UIManager.getInstance().getDecorView().getContext();
                    ToastUtil.show(Toast.makeText(context, message, Toast.LENGTH_SHORT));
                });
            }
            if (!musicDetail.equals(MusicDetail.NONE)) {
                ImageUtils.downloadAsync(musicDetail.getAlbum().getThumbnailPicUrl(200));
                StreamAudioPlayer streamAudioPlayer = StreamAudioPlayer.getInstance();
                streamAudioPlayer.playAsync(musicDetail, serverStartTime).thenAccept(zonedDateTime -> {
                    NowPlayingInfo.getInstance().switchMusic(musicDetail, zonedDateTime);
                }).exceptionally(e -> {
                    return null;//TODO display error in hud
                });
            } else {
                NowPlayingInfo.getInstance().switchMusic(MusicDetail.NONE, null);
                StreamAudioPlayer streamAudioPlayer = StreamAudioPlayer.getInstance();
                streamAudioPlayer.stop();
            }
        }
    }

    public CompletableFuture<Artist> loadArtist(long id) {
        CompletableFuture<Artist> future = new CompletableFuture<>();
        GetArtistDetailResponse.setReceiver(id, future::complete);
        NetworkManager.sendToServer(new GetArtistDetailRequest(id));
        return future;
    }

    public CompletableFuture<List<MusicDetail>> loadArtistMusic(long id, int offset) {
        CompletableFuture<List<MusicDetail>> future = new CompletableFuture<>();
        GetArtistMoreMusicResponse.RequestData requestData = new GetArtistMoreMusicResponse.RequestData(id, offset);
        GetArtistMoreMusicResponse.setReceiver(requestData, future::complete);
        NetworkManager.sendToServer(new GetArtistMoreMusicRequest(id, offset));
        return future;
    }

    public void voteForSkipCurrent() {
        if (NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail() != null) {
            NetworkManager.sendToServer(new VoteSkipCurrentMusicMessage(NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail().getId()));
        }
    }

    public void keyBindsVoteSkipCurrent() {
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

    @RegisterMark
    public static class RegisterImpl implements ClientRegister {
        public static void reset() {
            if (instance != null) {
                instance.switchMusic(MusicDetail.NONE, null, "");
                instance.idlePlaySourceLoaded = false;
                instance.musicQueue.clear();
                instance.idlePlaySourceAddListeners.clear();
                instance.idlePlaylistRemoveListeners.clear();
                instance.idlePlaySourceChangeListeners.clear();
                instance.musicQueueRefreshListeners.clear();
                instance.musicQueuePushListeners.clear();
                instance.musicQueueRemoveListeners.clear();
            }
            if (HudRendererManager.isLoaded()) {
                HudRendererManager.getInstance().reset();
            }
            NowPlayingInfo.getInstance().switchMusic(MusicDetail.NONE, null);
        }

        @Override
        public void register() {
            LoginService.getInstance().getLoginCompleteListeners().add((loginCookieInfo) -> {
                if (loginCookieInfo.type() != LoginType.ANONYMOUS) {
                    MusicService.getInstance().loadIdlePlaySourceFromConfig();
                }
            });
            ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
                reset();
            });
        }
    }
}