package indi.etern.musichud.server.api;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.user.Profile;
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
import net.minecraft.world.entity.player.Player;
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
    private static final ServerConfig serverConfig = ServerConfig.getInstance();
    private static volatile MusicPlayerServerService instance;
    final Map<Player, Set<IdlePlaySource>> idlePlaySources = new ConcurrentHashMap<>();
    private final IMusicApiService musicApiService = IMusicApiService.getInstance(ApiProvider.NCM);
    private final CurrentVoteInfo currentVoteInfo = new CurrentVoteInfo();
    private final Logger logger = MusicHud.getLogger(MusicPlayerServerService.class);
    private final Cache<CacheKey, MusicResourceInfo> musicResourceInfoCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(20)
            .build();
    private final IServerNetworkService serverNetworkService = IServerNetworkService.getInstance();
    private static final ILoginApiService loginApiService = ILoginApiService.getInstance(ApiProvider.NCM);
    @Getter
    ArrayDeque<MusicDetail> musicQueue = new ArrayDeque<>();
    boolean continuable;
    @Getter
    private volatile MusicDetail currentMusicDetail = MusicDetail.NONE;
    @Getter
    private MusicDetail nextIdleMusicDetail = MusicDetail.NONE;
    private MusicDetail preloadMusicDetail = MusicDetail.NONE;
    @Getter
    private volatile ZonedDateTime nowPlayingStartTime = ZonedDateTime.of(LocalDateTime.MIN, ZoneId.systemDefault());
    private volatile Thread pusherThread;
    private volatile boolean pusherThreadRunning = false;
    private boolean haveSentMusic = false;
    private final Runnable musicPusher = new Runnable() {

        @Override
        public void run() {
            Thread thread = Thread.currentThread();
            thread.setName("MHWorker-Music-Data-Pusher");
            pusherThread = thread;
            pusherThreadRunning = true;
            String message = "";
            while (MusicPlayerServerService.this.continuable) {
                MusicDetail switchedToPlay = null;
                try {
                    Map<UUID, LoginApiService.PlayerLoginInfo> loginedPlayerInfoMap = loginApiService.getPlayerInfoMap();
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
                                            uuid -> uuid.equals(pusherInfo.getPlayerUUID())
                                    )
                            ) {
                                continue;
                            }
                        }
                    } else {
                        switchedToPlay = musicQueue.remove();
                        serverNetworkService.sendToPlayerInfos(loginedPlayerInfoMap.values(),
                                new RefreshMusicQueueMessage(musicQueue));
                    }

                    nextIdleMusicDetail = preloadMusicDetail != null ? preloadMusicDetail : MusicDetail.NONE;

                    Player pusherPlayer = switchedToPlay.getPusherInfo().getPlayer();
                    MusicResourceInfo resourceInfo = musicApiService.getResourceInfo(switchedToPlay, Quality.STANDARD, pusherPlayer);
                    if (resourceInfo.equals(MusicResourceInfo.NONE)) {
                        continue;
                    }
                    if (switchedToPlay.getLyricInfo() == null || switchedToPlay.getLyricInfo().equals(LyricInfo.NONE)) {
                        switchedToPlay.setLyricInfo(musicApiService.getLyricInfo(switchedToPlay));
                    }
                    serverNetworkService.sendToPlayerInfos(
                            loginedPlayerInfoMap.values(),
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

            List<Map.Entry<Player, Set<IdlePlaySource>>> entryList =
                    new ArrayList<>(idlePlaySources.entrySet());

            if (entryList.isEmpty()) {
                return Optional.empty();
            }

            Map.Entry<Player, Set<IdlePlaySource>> randomEntry =
                    entryList.get(MusicHud.RANDOM.nextInt(entryList.size()));

            Player sourcePlayer = randomEntry.getKey();
            Set<IdlePlaySource> idlePlaySource = randomEntry.getValue();

            List<MusicDetail> allTracks = idlePlaySource.stream()
                    .flatMap(playSource -> playSource.getMusicCollection().getMusicDetails().stream())
                    .toList();

            if (allTracks.isEmpty()) {
                return Optional.empty();
            }

            MusicDetail randomTrack = allTracks.get(MusicHud.RANDOM.nextInt(allTracks.size()));
            if (randomTrack.getExtraInfo() == null) {
                List<MusicDetail> detailByIds = musicApiService.getMusicDetailByIds(List.of(randomTrack.getId()), null);
                if (detailByIds.size() == 1) {
                    MusicDetail musicDetail = detailByIds.getFirst();
                    if (musicDetail.getId() == randomTrack.getId()) {
                        randomTrack.setExtraInfo(musicDetail.getExtraInfo());
                    } else {
                        throw new IllegalStateException();
                    }
                } else {
                    throw new IllegalStateException();
                }
            }

            LoginApiService.PlayerLoginInfo loginInfo =
                    loginApiService.getLoginInfoByPlayer(sourcePlayer);
            if (loginInfo != null) {
                randomTrack.setPusherInfo(getPusherInfo(sourcePlayer));
            } else {
                randomTrack.setPusherInfo(PusherInfo.EMPTY);
            }
            return Optional.of(randomTrack);
        }
    };

    public MusicPlayerServerService() {
        updateContinuable(!loginApiService.getLoginStateChangeListeners().isEmpty());
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

    private static PusherInfo getPusherInfo(Player pusher) {
        LoginApiService.PlayerLoginInfo loginInfo = loginApiService.getPlayerInfoMap().get(pusher.getUUID());
        PusherInfo pusherInfo = PusherInfo.EMPTY;
        if (loginInfo != null) {
            pusherInfo = new PusherInfo(
                    loginInfo.getProfile() == null ? 0 : loginInfo.getProfile().getUserId(),
                    pusher.getUUID(),
                    pusher.getName().getString()
            );
            pusherInfo.setPlayer(pusher);
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
        if (pusherThread != null) {
            pusherThread.interrupt();
        }
        currentMusicDetail = MusicDetail.NONE;
        if (haveSentMusic) {
            haveSentMusic = false;
            serverNetworkService.sendToPlayerInfos(
                    loginApiService.getPlayerInfoMap().values(),
                    new SwitchMusicMessage(MusicDetail.NONE, MusicDetail.NONE, "")
            );
            currentVoteInfo.resetTo(MusicDetail.NONE);
        }
    }

    public void sendSyncPlayingStatusToPlayer(Player player) {
        serverNetworkService.sendToPlayer(player,
                new RefreshMusicQueueMessage(musicQueue));
        sendUpdateAllIdlePlaySourcesMessageTo(Collections.singleton(loginApiService.getLoginInfoByPlayer(player)));
        if (currentMusicDetail != MusicDetail.NONE) {
            haveSentMusic = true;
            serverNetworkService.sendToPlayer(player,
                    new SyncCurrentPlayingMessage(currentMusicDetail, nextIdleMusicDetail, nowPlayingStartTime));
        }
    }

    public void sendUpdateAllIdlePlaySourcesMessageTo(Collection<LoginApiService.PlayerLoginInfo> playerLoginInfos) {
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

        for (LoginApiService.PlayerLoginInfo playerLoginInfo : playerLoginInfos) {
            if (playerLoginInfo != null) {
                Profile playerProfile = playerLoginInfo.getProfile();
                List<Playlist> processedPrivatePlaylists = new ArrayList<>();
                for (Playlist playlist : privatePlaylists) {
                    if (playlist.getCreator().equals(playerProfile)) {
                        processedPrivatePlaylists.add(playlist);
                    } else {
                        processedPrivatePlaylists.add(playlist.copyWithSensitiveErased());
                    }
                }
                processedPrivatePlaylists.addAll(publicPlaylists);
                serverNetworkService.sendToPlayer(playerLoginInfo.getPlayer(), new UpdateAllIdlePlaySourcesMessage(processedPrivatePlaylists, albums));
            }
        }
    }

    public void pushMusicToQueue(long musicDetailId, Player pusher) {
        List<MusicDetail> musicDetailByIds = musicApiService.getMusicDetailByIds(List.of(musicDetailId), pusher);
        if (musicDetailByIds.size() != 1) {
            throw new IllegalStateException();
        }
        MusicDetail musicDetail = musicDetailByIds.getFirst();
        PusherInfo pusherInfo = getPusherInfo(pusher);
        musicDetail.setPusherInfo(pusherInfo);
        musicQueue.add(musicDetail);
        serverNetworkService.sendToPlayerInfos(loginApiService.getPlayerInfoMap().values(),
                new RefreshMusicQueueMessage(musicQueue));
        updateContinuable(true);
    }

    public void removeMusicDetailFromQueue(int index, long id, Player player) {
        ArrayList<MusicDetail> list = new ArrayList<>(musicQueue);
        try {
            MusicDetail musicDetail = list.get(index);
            try {
                if (musicDetail.getId() == id) {
                    try {
                        removeMusicInternal(index, musicDetail, player);
                    } catch (IllegalAccessException ignored) {
                        tryPreviousOne(index, id, player, list);
                    }
                } else {
                    tryPreviousOne(index, id, player, list);
                }
            } catch (RuntimeException e) {
                trySimplyRemove(musicDetail, player);
            }
        } catch (IndexOutOfBoundsException ignored) {
            logger.warn("Failed to remove music from queue as index out of bounds");
        }
    }

    @SneakyThrows
    private void tryPreviousOne(int index, long id, Player player, ArrayList<MusicDetail> list) {
        if (index > 1) {//in case the queue just pulled
            MusicDetail musicDetail1 = list.get(index - 1);
            if (musicDetail1.getId() == id) {
                removeMusicInternal(index - 1, musicDetail1, player);
            } else {
                throw new RuntimeException("failed to remove music from queue");
            }
        } else {
            throw new RuntimeException("failed to remove music from queue");
        }
    }

    private void removeMusicInternal(int index, MusicDetail musicDetail, Player player) throws IllegalAccessException {
        if (musicDetail.getPusherInfo().getPlayerUUID().equals(player.getUUID())) {
            AtomicInteger index1 = new AtomicInteger(0);
            musicQueue.removeIf(musicDetail1 -> index == index1.getAndIncrement() && musicDetail.equals(musicDetail1));
            serverNetworkService.sendToPlayerInfos(loginApiService.getPlayerInfoMap().values(),
                    new RefreshMusicQueueMessage(musicQueue));
        } else {
            throw new IllegalAccessException();
        }
    }

    private void trySimplyRemove(MusicDetail musicDetail, Player player) {
        if (musicDetail.getPusherInfo().getPlayerUUID().equals(player.getUUID())) {
            musicQueue.remove(musicDetail);
            serverNetworkService.sendToPlayerInfos(loginApiService.getPlayerInfoMap().values(),
                    new RefreshMusicQueueMessage(musicQueue));
        }
    }

    public void addIdlePlaySource(long id, Class<?> type, Player player) {
        Set<IdlePlaySource> musicCollections = idlePlaySources.getOrDefault(player, new HashSet<>());
        IdlePlaySource idlePlaySource = new IdlePlaySource(id, type);
        idlePlaySource.setPlayer(player);
        idlePlaySource.serverLoadMusicCollection(player);
        musicCollections.add(idlePlaySource);
        idlePlaySources.put(player, musicCollections);
        updateContinuable(true);
        sendUpdateAllIdlePlaySourcesMessageTo(loginApiService.getPlayerInfoMap().values());
    }

    public void removeIdlePlaySource(long id, Class<?> musicCollectionClass, Player player) {
        Set<IdlePlaySource> musicCollections = idlePlaySources.get(player);
        if (musicCollections != null) {
            IdlePlaySource idlePlaySource = new IdlePlaySource(id, musicCollectionClass);
            idlePlaySource.setPlayer(player);
            musicCollections.remove(idlePlaySource);
            if (musicCollections.isEmpty()) {
                idlePlaySources.remove(player);
            }
            sendUpdateAllIdlePlaySourcesMessageTo(loginApiService.getPlayerInfoMap().values());
        }
    }

    public void voteSkipCurrent(long id, Player player) {
        currentVoteInfo.vote(id, player);
    }

    public MusicResourceInfo getMusicResourceInfo(long id, Quality quality, String retryFor, Player player) {
        try {
            List<MusicDetail> musicDetails = IMusicApiService.getInstance(ApiProvider.NCM).getMusicDetailByIds(List.of(id), null);
            if (musicDetails.size() == 1) {
                MusicDetail musicDetail = musicDetails.getFirst();
                try {
                    logger.debug("Try to load music resource info from cache with id: {}", id);
                    MusicResourceInfo musicResourceInfo = musicResourceInfoCache.get(new CacheKey(id, quality),
                            () -> {
                                logger.debug("Cache not found with id: {}, loading", id);
                                return getMusicResourceInfoWithoutCache(quality, musicDetail, player);
                            });
                    if (musicResourceInfo.getUrl().equals(retryFor)) {
                        logger.debug("Reload music resource info due to client retry for url \"{}\"", retryFor);
                        musicResourceInfo = getMusicResourceInfoWithoutCache(quality, musicDetail, player);
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

    private @NotNull MusicResourceInfo getMusicResourceInfoWithoutCache(Quality quality, MusicDetail musicDetail, Player player) {
        MusicResourceInfo resourceInfo = musicApiService.getResourceInfo(musicDetail, quality, player);
        if (resourceInfo != null && !resourceInfo.equals(MusicResourceInfo.NONE)) {
            return resourceInfo;
        } else {
            throw new RuntimeException("Failed to get resource info for music: " + musicDetail.getName() + " (ID: " + musicDetail.getId() + ")");
        }
    }

    public void removeAllIdlePlaySource(Player player) {
        idlePlaySources.remove(player);
        sendUpdateAllIdlePlaySourcesMessageTo(loginApiService.getPlayerInfoMap().values());
    }

    public void reset() {
        musicQueue.clear();
        idlePlaySources.clear();
        stopSendingMusic();
        haveSentMusic = false;
        nextIdleMusicDetail = MusicDetail.NONE;
        preloadMusicDetail = MusicDetail.NONE;
    }

    private record CacheKey(long musicId, Quality quality) {
    }

    @RegisterMark
    public static class Register implements ServerRegister {
        @Override
        public void register() {
            loginApiService.getLoginStateChangeListeners().add((set) -> {
                if (instance != null) {
                    instance.updateContinuable(!set.isEmpty());
                }
            });
        }
    }

    @Getter
    @Setter
    private class CurrentVoteInfo {
        final Set<Player> votedPlayers = new HashSet<>();
        MusicDetail musicDetail;
        float voteRate;

        public void vote(long id, Player player) {
            if (!votedPlayers.contains(player) && musicDetail.getId() == id) {
                votedPlayers.add(player);
                voteRate += 1.0f / loginApiService.getPlayerInfoMap().size();
                if (musicDetail.getPusherInfo().getPlayerUUID().equals(player.getUUID())) {
                    voteRate += (float) serverConfig.getPusherVoteAdditionalRate();
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
