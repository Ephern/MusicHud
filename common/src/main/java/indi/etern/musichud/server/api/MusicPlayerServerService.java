package indi.etern.musichud.server.api;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.VipType;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.interfaces.ServerConfig;
import indi.etern.musichud.interfaces.ServerRegister;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.payloads.pushMessages.s2c.RefreshMusicQueueMessage;
import indi.etern.musichud.network.payloads.pushMessages.s2c.SwitchMusicMessage;
import indi.etern.musichud.network.payloads.pushMessages.s2c.SyncCurrentPlayingMessage;
import indi.etern.musichud.network.payloads.pushMessages.s2c.UpdateAllIdlePlaySourcesMessage;
import indi.etern.musichud.server.api.impl.ncm.LoginApiService;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MusicPlayerServerService {
    private static volatile MusicPlayerServerService instance;
    private final IMusicApiService IMusicApiService = indi.etern.musichud.server.api.IMusicApiService.getInstance(ApiProvider.NCM);
    private final CurrentVoteInfo currentVoteInfo = new CurrentVoteInfo();
    private final Logger logger = MusicHud.getLogger(MusicPlayerServerService.class);
    private final Cache<CacheKey, MusicResourceInfo> musicResourceInfoCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(20)
            .build();
    @Getter
    ArrayDeque<MusicDetail> musicQueue = new ArrayDeque<>();
    Map<ServerPlayer, Set<IdlePlaySource>> idlePlaySources = new ConcurrentHashMap<>();
    boolean continuable;
    @Getter
    private volatile MusicDetail currentMusicDetail = MusicDetail.NONE;
    @Getter
    private MusicDetail nextIdleMusicDetail = MusicDetail.NONE;
    @Getter
    private volatile ZonedDateTime nowPlayingStartTime = ZonedDateTime.of(LocalDateTime.MIN, ZoneId.systemDefault());
    private volatile Thread pusherThread;
    private volatile boolean pusherThreadRunning = false;
    private boolean haveSentMusic = false;
    private final IServerNetworkService serverNetworkService = IServerNetworkService.getInstance();
    private final Runnable musicPusher = new Runnable() {
        private MusicDetail preloadMusicDetail = MusicDetail.NONE;

        @Override
        public void run() {
            Thread thread = Thread.currentThread();
            thread.setName("Music Data Pusher");
            pusherThread = thread;
            pusherThreadRunning = true;
            String message = "";
            while (MusicPlayerServerService.this.continuable) {
                MusicDetail switchedToPlay = null;
                try {
                    Map<ServerPlayer, LoginApiService.PlayerLoginInfo> loginedPlayerInfoMap = ILoginApiService.getInstance(ApiProvider.NCM).getLoginedPlayerInfoMap();
                    if (musicQueue.isEmpty()) {
                        Optional<MusicDetail> optionalMusicDetail = getRandomMusicFromIdleSources();
                        if (optionalMusicDetail.isEmpty()) {
                            break;
                        } else {
                            MusicDetail musicDetail = optionalMusicDetail.get();
                            if (preloadMusicDetail == null || preloadMusicDetail.equals(MusicDetail.NONE)) {
                                preloadMusicDetail = musicDetail;
                                Optional<MusicDetail> optionalMusicDetail1 = getRandomMusicFromIdleSources();
                                if (optionalMusicDetail1.isPresent()) {
                                    switchedToPlay = preloadMusicDetail;
                                    nextIdleMusicDetail = optionalMusicDetail1.get();
                                    preloadMusicDetail = nextIdleMusicDetail;
                                } else {
                                    switchedToPlay = musicDetail;
                                    preloadMusicDetail = MusicDetail.NONE;
                                }
                            } else {
                                switchedToPlay = preloadMusicDetail;
                                preloadMusicDetail = musicDetail;
                            }
                            PusherInfo pusherInfo = switchedToPlay.getPusherInfo();
                            if (pusherInfo != null &&
                                    loginedPlayerInfoMap.keySet().stream().noneMatch(
                                            serverPlayer -> serverPlayer.getUUID().equals(pusherInfo.playerUUID())
                                    )
                            ) {
                                continue;
                            }
                        }
                    } else {
                        switchedToPlay = musicQueue.remove();
                        serverNetworkService.sendToPlayers(loginedPlayerInfoMap.keySet(),
                                new RefreshMusicQueueMessage(musicQueue));
                    }

                    nextIdleMusicDetail = preloadMusicDetail != null ? preloadMusicDetail : MusicDetail.NONE;

                    ILoginApiService ILoginApiService = indi.etern.musichud.server.api.ILoginApiService.getInstance(ApiProvider.NCM);
                    MusicResourceInfo resourceInfo = IMusicApiService.getResourceInfo(switchedToPlay, Quality.STANDARD, ILoginApiService.randomVipCookieOr(ILoginApiService::getAnonymousCookie));
                    if (resourceInfo.equals(MusicResourceInfo.NONE)) {
                        continue;
                    }
                    if (switchedToPlay.getLyricInfo() == null || switchedToPlay.getLyricInfo().equals(LyricInfo.NONE)) {
                        switchedToPlay.setLyricInfo(IMusicApiService.getLyricInfo(switchedToPlay));
                    }
                    serverNetworkService.sendToPlayers(
                            loginedPlayerInfoMap.keySet(),
                            new SwitchMusicMessage(switchedToPlay, nextIdleMusicDetail, message)
                    );
                    message = "";
                    currentVoteInfo.resetTo(switchedToPlay);
                    haveSentMusic = true;
                    currentMusicDetail = switchedToPlay;
                    nowPlayingStartTime = ZonedDateTime.now();
                    logger.info("Switched to music: {} (ID: {})", switchedToPlay.getName(), switchedToPlay.getId());
                    try {
                        int musicIntervalMillis = 1000;
                        //noinspection BusyWait
                        Thread.sleep(switchedToPlay.getDurationMillis() + musicIntervalMillis);
                    } catch (InterruptedException ignored) {//When force switch
                        logger.info("Skip current, switch to nextIdle");
                        message = MusicHud.MOD_ID + ".text.votePassed";
                    }
                } catch (Exception e) {
                    logger.error("Failed to push music: {} (id: {})",
                            switchedToPlay != null ? switchedToPlay.getName() : "null",
                            switchedToPlay != null ? switchedToPlay.getId() : "-1",
                            e);
                    try {
                        //noinspection BusyWait
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            }
            logger.info("Music Pusher stopped due to no more music");
            pusherThread = null;
            pusherThreadRunning = false;
            MusicPlayerServerService.this.stopSendingMusic();
        }

        private Optional<MusicDetail> getRandomMusicFromIdleSources() {
            if (idlePlaySources.isEmpty()) {
                return Optional.empty();
            }

            List<Map.Entry<ServerPlayer, Set<IdlePlaySource>>> entryList =
                    new ArrayList<>(idlePlaySources.entrySet());

            if (entryList.isEmpty()) {
                return Optional.empty();
            }

            Map.Entry<ServerPlayer, Set<IdlePlaySource>> randomEntry =
                    entryList.get(MusicHud.RANDOM.nextInt(entryList.size()));

            ServerPlayer sourcePlayer = randomEntry.getKey();
            Set<IdlePlaySource> idlePlaySource = randomEntry.getValue();

            List<MusicDetail> allTracks = idlePlaySource.stream()
                    .flatMap(playSource -> playSource.getMusicCollection().getMusicDetails().stream())
                    .toList();

            if (allTracks.isEmpty()) {
                return Optional.empty();
            }

            MusicDetail randomTrack = allTracks.get(MusicHud.RANDOM.nextInt(allTracks.size()));

            LoginApiService.PlayerLoginInfo loginInfo =
                    ILoginApiService.getInstance(ApiProvider.NCM).getLoginInfoByServerPlayer(sourcePlayer);
            if (loginInfo != null) {
                PusherInfo pusherInfo = new PusherInfo(
                        loginInfo.getProfile().getUserId(),
                        sourcePlayer.getUUID(),
                        sourcePlayer.getName().getString()
                );
                randomTrack.setPusherInfo(pusherInfo);
            } else {
                randomTrack.setPusherInfo(PusherInfo.EMPTY);
            }
            return Optional.of(randomTrack);
        }
    };

    public MusicPlayerServerService() {
        updateContinuable(!ILoginApiService.getInstance(ApiProvider.NCM).getLoginStateChangeListeners().isEmpty());
    }

    public static MusicPlayerServerService getInstance() {
        if (instance == null) {
            synchronized (MusicPlayerServerService.class) {
                if (instance == null) {
                    instance = new MusicPlayerServerService();
                }
            }
        }
        return instance;
    }

    private static PusherInfo getPusherInfo(ServerPlayer pusher) {
        LoginApiService.PlayerLoginInfo loginInfo = ILoginApiService.getInstance(ApiProvider.NCM).getLoginedPlayerInfoMap().get(pusher);
        PusherInfo pusherInfo = PusherInfo.EMPTY;
        if (loginInfo != null) {
            pusherInfo = new PusherInfo(
                    loginInfo.getProfile() == null ? 0 : loginInfo.getProfile().getUserId(),
                    pusher.getUUID(),
                    pusher.getName().getString()
            );
        }
        return pusherInfo;
    }

    private void updateContinuable(boolean continuable) {
        this.continuable = continuable;
        if (continuable) {
            startMusicPusher();
        }
    }

    private void startMusicPusher() {
        if (!pusherThreadRunning) {
            synchronized (MusicPlayerServerService.class) {
                if (!pusherThreadRunning) {
                    pusherThreadRunning = true;
                    MusicHud.EXECUTOR.execute(musicPusher);
                }
            }
        }
    }

    private void stopSendingMusic() {
        this.continuable = false;
        currentMusicDetail = MusicDetail.NONE;
        if (haveSentMusic) {
            haveSentMusic = false;
            serverNetworkService.sendToPlayers(
                    ILoginApiService.getInstance(ApiProvider.NCM).getLoginedPlayerInfoMap().keySet(),
                    new SwitchMusicMessage(MusicDetail.NONE, MusicDetail.NONE, "")
            );
            currentVoteInfo.resetTo(MusicDetail.NONE);
        }
    }

    public void sendSyncPlayingStatusToPlayer(ServerPlayer serverPlayer) {
        serverNetworkService.sendToPlayer(serverPlayer,
                new RefreshMusicQueueMessage(musicQueue));
        sendUpdateAllIdlePlaySourcesMessageTo(Collections.singleton(serverPlayer));
        if (currentMusicDetail != MusicDetail.NONE) {
            haveSentMusic = true;
            serverNetworkService.sendToPlayer(serverPlayer,
                    new SyncCurrentPlayingMessage(currentMusicDetail, nextIdleMusicDetail, nowPlayingStartTime));
        }
    }

    public void sendUpdateAllIdlePlaySourcesMessageTo(Collection<ServerPlayer> players) {
        List<Playlist> publicPlaylists = new ArrayList<>();
        List<Playlist> privatePlaylists = new ArrayList<>();
        List<Album> albums = new ArrayList<>();
        for (IdlePlaySource playSource : idlePlaySources.values().stream().flatMap(Collection::stream).toList()) {
            MusicCollection musicCollection = playSource.getMusicCollection();
            PusherInfo pusherInfo = getPusherInfo(playSource.getPlayer());
            if (musicCollection instanceof Playlist playlist) {
                if (playlist.getPrivacy() == Privacy.PUBLIC) {
                    publicPlaylists.add(playlist.copyWithPusherInfo(pusherInfo));
                } else {
                    privatePlaylists.add(playlist.copyWithPusherInfo(pusherInfo));
                }
            } else if (musicCollection instanceof Album album) {
                albums.add(album.copyWithPusherInfo(pusherInfo));
            } else {
                throw new IllegalArgumentException("Invalid music collection type");
            }
        }

        Map<ServerPlayer, LoginApiService.PlayerLoginInfo> loginedPlayerInfoMap = ILoginApiService.getInstance(ApiProvider.NCM).getLoginedPlayerInfoMap();
        for (ServerPlayer player : players) {
            Profile playerProfile = loginedPlayerInfoMap.get(player).getProfile();
            List<Playlist> processedPrivatePlaylists = new ArrayList<>();
            for (Playlist playlist : privatePlaylists) {
                if (playlist.getCreator().equals(playerProfile)) {
                    processedPrivatePlaylists.add(playlist);
                } else {
                    processedPrivatePlaylists.add(playlist.copyWithSensitiveErased());
                }
            }
            processedPrivatePlaylists.addAll(publicPlaylists);
            serverNetworkService.sendToPlayer(player, new UpdateAllIdlePlaySourcesMessage(processedPrivatePlaylists, albums));
        }
    }

    public void pushMusicToQueue(long musicDetailId, ServerPlayer pusher) {
        List<MusicDetail> musicDetailByIds = IMusicApiService.getMusicDetailByIds(List.of(musicDetailId));
        if (musicDetailByIds.size() != 1) {
            throw new IllegalStateException();
        }
        MusicDetail musicDetail = musicDetailByIds.getFirst();
        PusherInfo pusherInfo = getPusherInfo(pusher);
        musicDetail.setPusherInfo(pusherInfo);
        musicQueue.add(musicDetail);
        serverNetworkService.sendToPlayers(ILoginApiService.getInstance(ApiProvider.NCM).getLoginedPlayerInfoMap().keySet(),
                new RefreshMusicQueueMessage(musicQueue));
        updateContinuable(true);
    }

    public void removeMusicDetailFromQueue(int index, long id, ServerPlayer serverPlayer) {
        ArrayList<MusicDetail> list = new ArrayList<>(musicQueue);
        try {
            MusicDetail musicDetail = list.get(index);
            try {
                if (musicDetail.getId() == id) {
                    try {
                        removeMusicInternal(index, musicDetail, serverPlayer);
                    } catch (IllegalAccessException ignored) {
                        tryPreviousOne(index, id, serverPlayer, list);
                    }
                } else {
                    tryPreviousOne(index, id, serverPlayer, list);
                }
            } catch (RuntimeException e) {
                trySimplyRemove(musicDetail, serverPlayer);
            }
        } catch (IndexOutOfBoundsException ignored) {
            logger.warn("Failed to remove music from queue as index out of bounds");
        }
    }

    @SneakyThrows
    private void tryPreviousOne(int index, long id, ServerPlayer serverPlayer, ArrayList<MusicDetail> list) {
        if (index > 1) {//in case the queue just pulled
            MusicDetail musicDetail1 = list.get(index - 1);
            if (musicDetail1.getId() == id) {
                removeMusicInternal(index - 1, musicDetail1, serverPlayer);
            } else {
                throw new RuntimeException("failed to remove music from queue");
            }
        } else {
            throw new RuntimeException("failed to remove music from queue");
        }
    }

    private void removeMusicInternal(int index, MusicDetail musicDetail, ServerPlayer player) throws IllegalAccessException {
        if (musicDetail.getPusherInfo().playerUUID().equals(player.getUUID())) {
            AtomicInteger index1 = new AtomicInteger(0);
            musicQueue.removeIf(musicDetail1 -> index == index1.getAndIncrement() && musicDetail.equals(musicDetail1));
            serverNetworkService.sendToPlayers(ILoginApiService.getInstance(ApiProvider.NCM).getLoginedPlayerInfoMap().keySet(),
                    new RefreshMusicQueueMessage(musicQueue));
        } else {
            throw new IllegalAccessException();
        }
    }

    private void trySimplyRemove(MusicDetail musicDetail, ServerPlayer serverPlayer) {
        if (musicDetail.getPusherInfo().playerUUID().equals(serverPlayer.getUUID())) {
            musicQueue.remove(musicDetail);
            serverNetworkService.sendToPlayers(ILoginApiService.getInstance(ApiProvider.NCM).getLoginedPlayerInfoMap().keySet(),
                    new RefreshMusicQueueMessage(musicQueue));
        }
    }

    public void addIdlePlaySource(long id, Class<?> type, ServerPlayer player) {
        Set<IdlePlaySource> musicCollections = idlePlaySources.getOrDefault(player, new HashSet<>());
        IdlePlaySource idlePlaySource = new IdlePlaySource(id, type);
        idlePlaySource.setPlayer(player);
        idlePlaySource.serverLoadMusicCollection(player);
        musicCollections.add(idlePlaySource);
        idlePlaySources.put(player, musicCollections);
        updateContinuable(true);
        sendUpdateAllIdlePlaySourcesMessageTo(ILoginApiService.getInstance(ApiProvider.NCM).getLoginedPlayerInfoMap().keySet());
    }

    public void removeIdlePlaySource(long id, Class<?> musicCollectionClass, ServerPlayer player) {
        Set<IdlePlaySource> musicCollections = idlePlaySources.get(player);
        if (musicCollections != null) {
            IdlePlaySource idlePlaySource = new IdlePlaySource(id, musicCollectionClass);
            idlePlaySource.setPlayer(player);
            musicCollections.remove(idlePlaySource);
            if (musicCollections.isEmpty()) {
                idlePlaySources.remove(player);
            }
            sendUpdateAllIdlePlaySourcesMessageTo(ILoginApiService.getInstance(ApiProvider.NCM).getLoginedPlayerInfoMap().keySet());
        }
    }

    public void voteSkipCurrent(long id, ServerPlayer player) {
        currentVoteInfo.vote(id, player);
    }

    public MusicResourceInfo getMusicResourceInfo(long id, Quality quality, String retryFor, ServerPlayer serverPlayer) {
        try {
            List<MusicDetail> musicDetails = indi.etern.musichud.server.api.IMusicApiService.getInstance(ApiProvider.NCM).getMusicDetailByIds(List.of(id));
            if (musicDetails.size() == 1) {
                MusicDetail musicDetail = musicDetails.getFirst();
                try {
                    logger.debug("Try to load music resource info from cache with id: {}", id);
                    MusicResourceInfo musicResourceInfo = musicResourceInfoCache.get(new CacheKey(id, quality),
                            () -> {
                                logger.debug("Cache not found with id: {}, loading", id);
                                return getMusicResourceInfoWithoutCache(quality, musicDetail, serverPlayer);
                            });
                    if (musicResourceInfo.getUrl().equals(retryFor)) {
                        logger.debug("Reload music resource info due to client retry for url \"{}\"", retryFor);
                        musicResourceInfo = getMusicResourceInfoWithoutCache(quality, musicDetail, serverPlayer);
                        musicResourceInfoCache.put(new CacheKey(id, quality), musicResourceInfo);
                    }
                    return musicResourceInfo;
                } catch (Exception e) {
                    logger.error("Failed to get resource info for music: {}", musicDetail.getName(), e);
                    return MusicResourceInfo.NONE;
                }
            } else if (musicDetails.size() > 1) {
                throw new IllegalStateException();
            } else {
                return MusicResourceInfo.NONE;
            }
        } catch (Exception e) {
            return MusicResourceInfo.NONE;
        }
    }

    private @NotNull MusicResourceInfo getMusicResourceInfoWithoutCache(Quality quality, MusicDetail musicDetail, ServerPlayer serverPlayer) {
        ILoginApiService ILoginApiService = indi.etern.musichud.server.api.ILoginApiService.getInstance(ApiProvider.NCM);
        var loginInfo = ILoginApiService.getLoginInfoByServerPlayer(serverPlayer);
        String cookie;
        if (loginInfo.getVipType() == VipType.VIP) {
            cookie = loginInfo.getLoginCookieInfo().rawCookie();
        } else {
            cookie = ILoginApiService.randomVipCookieOr(() -> loginInfo.getLoginCookieInfo().rawCookie());
        }
        MusicResourceInfo resourceInfo = IMusicApiService.getResourceInfo(musicDetail, quality, cookie);
        if (resourceInfo != null && !resourceInfo.equals(MusicResourceInfo.NONE)) {
            return resourceInfo;
        } else {
            throw new RuntimeException("Failed to get resource info for music: " + musicDetail.getName() + " (ID: " + musicDetail.getId() + ")");
        }
    }

    public void removeAllIdlePlaySource(ServerPlayer player) {
        idlePlaySources.remove(player);
        sendUpdateAllIdlePlaySourcesMessageTo(ILoginApiService.getInstance(ApiProvider.NCM).getLoginedPlayerInfoMap().keySet());
    }

    private record CacheKey(long musicId, Quality quality) {
    }

    @RegisterMark
    public static class Register implements ServerRegister {
        @Override
        public void register() {
            ILoginApiService.getInstance(ApiProvider.NCM).getLoginStateChangeListeners().add((map) -> {
                if (instance != null) {
                    instance.updateContinuable(!map.isEmpty());
                }
            });
        }
    }

    @Getter
    @Setter
    private class CurrentVoteInfo {
        final Set<ServerPlayer> votedPlayers = new HashSet<>();
        MusicDetail musicDetail;
        float voteRate;

        public void vote(long id, ServerPlayer player) {
            if (!votedPlayers.contains(player) && musicDetail.getId() == id) {
                votedPlayers.add(player);
                voteRate += 1.0f / ILoginApiService.getInstance(ApiProvider.NCM).getLoginedPlayerInfoMap().size();
                if (musicDetail.getPusherInfo().playerUUID().equals(player.getUUID())) {
                    voteRate += (float) ServerConfig.getInstance().getPusherVoteAdditionalRate();
                    logger.info("Pusher player \"{}\" voted for skip current music {}:{}", player.getName().getString(), id, musicDetail.getName());
                } else {
                    logger.info("Player \"{}\" voted for skip current music {}:{}", player.getName().getString(), id, musicDetail.getName());
                }
                voteRate = Math.clamp(voteRate, 0.0f, 1.0f);
                if (voteRate >= 0.5) {
                    logger.info("Try to skip current music as voting rate reach: {} >= 0.5", voteRate);
                    if (pusherThread != null) {
                        pusherThread.interrupt();
                    }
                    resetTo(MusicDetail.NONE);
                }
            }
        }

        public void resetTo(MusicDetail musicDetail) {
            this.musicDetail = musicDetail;
            voteRate = 0;
            votedPlayers.clear();
        }
    }
}
