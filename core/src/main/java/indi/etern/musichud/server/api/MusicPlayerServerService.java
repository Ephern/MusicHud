package indi.etern.musichud.server.api;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.music.actions.MessagedResult;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.interfaces.ServerConfig;
import indi.etern.musichud.interfaces.ServerRegister;
import indi.etern.musichud.network.IPlayerClient;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.payloads.pushMessages.s2c.*;
import indi.etern.musichud.network.payloads.requestResponseCycle.GetInitialStateResponse;
import indi.etern.musichud.platform.Environment;
import indi.etern.musichud.server.api.impl.ncm.LoginApiService;
import indi.etern.musichud.throwable.MusicResourceLoadingException;
import indi.etern.musichud.utils.IClientDistUtil;
import lombok.*;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MusicPlayerServerService {
    private static final ServerConfig serverConfig = ServerConfig.getInstance();
    private static final ILoginApiService loginApiService = ILoginApiService.getInstance(ApiProvider.NCM);
    private static final long DEBOUNCE_DELAY_MILLIS = 500;
    private static volatile MusicPlayerServerService instance;
    final Map<PusherInfo, Set<IdlePlaySource>> idlePlaySources = new ConcurrentHashMap<>();
    private final IMusicApiService musicApiService = IMusicApiService.getInstance(ApiProvider.NCM);
    private final CurrentVoteInfo currentVoteInfo = new CurrentVoteInfo();
    private final Logger logger = MusicHud.getLogger(MusicPlayerServerService.class);
    private final Cache<CacheKey, MusicResourceInfo> musicResourceInfoCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(20)
            .build();
    private final IServerNetworkService serverNetworkService = IServerNetworkService.getInstance();
    private final AtomicInteger debounceToken = new AtomicInteger(0);
    private final Runnable musicPusher = new Runnable() {

        @Override
        public void run() {
            Thread thread = Thread.currentThread();
            thread.setName("MHWorker-Music-Data-Pusher");
            pusherThread = thread;
            pusherThreadRunning = true;
            String message = "";
            Map<UUID, LoginApiService.PlayerLoginInfo> loginedPlayerInfoMap = loginApiService.getPlayerInfoMap();
            boolean hasAvailableIdlePlaySourcesMusic = false;
            while (MusicPlayerServerService.this.continuable) {
                MusicDetail switchedToPlay = null;
                Map<PusherInfo, Set<IdlePlaySource>> idlePlaySources = MusicPlayerServerService.this.idlePlaySources;
                try {
                    hasAvailableIdlePlaySourcesMusic = hasAvailableIdlePlaySourcesMusic(idlePlaySources);
                    if (musicQueue.isEmpty()) {
                        if (!hasAvailableIdlePlaySourcesMusic) {
                            break;
                        }
                        Optional<MusicDetail> optionalMusicDetail = getRandomMusicFromIdleSources(idlePlaySources);
                        if (optionalMusicDetail.isEmpty()) {
                            break;
                        } else {
                            MusicDetail musicDetail = optionalMusicDetail.get();
                            if (preloadMusicDetail == null || preloadMusicDetail.equals(MusicDetail.NONE)) {
                                preloadMusicDetail = musicDetail;
                                Optional<MusicDetail> optionalMusicDetail1 = getRandomMusicFromIdleSources(idlePlaySources);
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
                        switchedToPlay = musicQueue.remove().musicDetail();
                        serverNetworkService.sendToPlayerInfos(loginedPlayerInfoMap.values(),
                                new RefreshMusicQueueMessage(musicQueue));
                    }

                    nextIdleMusicDetail = preloadMusicDetail != null ? preloadMusicDetail : MusicDetail.NONE;

                    MusicResourceInfo resourceInfo = musicApiService.getResourceInfo(switchedToPlay, Quality.STANDARD, switchedToPlay.getPusherInfo().getPlayerUUID());
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
                    int musicIntervalMillis = 1000;
                    //noinspection BusyWait
                    Thread.sleep(switchedToPlay.getDurationMillis() + musicIntervalMillis);
                } catch (InterruptedException ignored) {//When force switch
                    logger.info("Skip current, switch to nextIdle");
                    if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT
                            && !IClientDistUtil.getInstance().inSinglePlayer()) {
                        message = MusicHud.MOD_ID + ".text.votePassed";
                    }
                } catch (Exception e) {
                    String message1;
                    if (e instanceof MusicResourceLoadingException e1 && e1.isUsingSubstitute()) {
                        message1 = MusicHud.MOD_ID + ".text.substituteMusicPushError";
                    } else {
                        message1 = MusicHud.MOD_ID + ".text.musicPushError";
                    }
                    serverNetworkService.sendToPlayerInfos(
                            loginedPlayerInfoMap.values(),
                            new CommonNotificationMessage(MessagedResult.fail(message1, null))
                    );
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
            pusherThread = null;
            pusherThreadRunning = false;
            MusicPlayerServerService.this.stopSendingMusic();
            // A source/queue added during the shutdown window (between the loop break and
            // pusherThreadRunning = false) would have missed the updateContinuable(true)
            // restart; pick it up here so the pusher does not stay dead.
            if (!musicQueue.isEmpty() || !hasAvailableIdlePlaySourcesMusic) {
                updateContinuable(true);
            } else {
                logger.info("Music Pusher stopped");
            }
        }

        private boolean hasAvailableIdlePlaySourcesMusic(Map<PusherInfo, Set<IdlePlaySource>> idlePlaySources) {
            return !idlePlaySources.isEmpty() && idlePlaySources.entrySet().stream()
                    .flatMap(entry -> entry.getValue().stream())
                    .flatMap(playSource -> playSource.getMusicCollection().getMusicDetails().stream())
                    .findAny().isPresent();
        }

        private Optional<MusicDetail> getRandomMusicFromIdleSources(Map<PusherInfo, Set<IdlePlaySource>> idlePlaySources) {
            List<Map.Entry<PusherInfo, Set<IdlePlaySource>>> entryList = idlePlaySources.entrySet().stream().filter(
                    entry -> entry.getValue().stream()
                            .anyMatch(playSource ->
                                    !playSource.getMusicCollection().getMusicDetails().isEmpty()
                            )
            ).toList();

            Map.Entry<PusherInfo, Set<IdlePlaySource>> randomEntry =
                    entryList.get(MusicHud.RANDOM.nextInt(entryList.size()));

            PusherInfo pusherInfo = randomEntry.getKey();
            Set<IdlePlaySource> idlePlaySource = randomEntry.getValue();

            List<MusicDetail> allTracks = idlePlaySource.stream()
                    .flatMap(playSource -> playSource.getMusicCollection().getMusicDetails().stream())
                    .toList();

            if (allTracks.isEmpty()) {
                throw new IllegalStateException();
            }

            MusicDetail randomTrack = allTracks.get(MusicHud.RANDOM.nextInt(allTracks.size()));
            if (randomTrack.getExtraInfo() == null) {
                List<MusicDetail> detailByIds = musicApiService.getMusicDetailByIds(List.of(randomTrack.getId()), null);
                if (detailByIds.size() == 1) {
                    MusicDetail musicDetail = detailByIds.getFirst();
                    if (musicDetail.getId() == randomTrack.getId()) {
                        randomTrack.setExtraInfo(musicDetail.getExtraInfo());
                    } else {
                        throw new MusicResourceLoadingException(new IllegalStateException("Api returned a music detail with different id"), randomTrack, false);
                    }
                } else {
                    throw new MusicResourceLoadingException(new IllegalStateException("Api returned a invalid music detail"), randomTrack, false);
                }
            }

            LoginApiService.PlayerLoginInfo loginInfo =
                    loginApiService.getLoginInfoByPlayerUUID(pusherInfo.getPlayerUUID());
            if (loginInfo != null) {
                randomTrack.setPusherInfo(pusherInfo);
            } else {
                randomTrack.setPusherInfo(PusherInfo.EMPTY);
            }
            return Optional.of(randomTrack);
        }
    };
    @Getter
    ArrayDeque<QueueItem> musicQueue = new ArrayDeque<>();
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

    public void sendUpdateAllIdlePlaySourcesMessageTo(Collection<LoginApiService.PlayerLoginInfo> playerLoginInfos) {
        for (LoginApiService.PlayerLoginInfo playerLoginInfo : playerLoginInfos) {
            if (playerLoginInfo != null) {
                IdleSourcesData idleSourcesData = buildIdleSourcesData(playerLoginInfo.getProfile());
                serverNetworkService.sendToPlayer(playerLoginInfo.getPlayer(),
                        new UpdateAllIdlePlaySourcesMessage(idleSourcesData.playlistSources(), idleSourcesData.albumSources()));
            }
        }
    }

    private IdleSourcesData buildIdleSourcesData(Profile playerProfile) {
        List<Playlist> publicPlaylists = new ArrayList<>();
        List<Playlist> privatePlaylists = new ArrayList<>();
        List<Album> albums = new ArrayList<>();
        for (IdlePlaySource playSource : idlePlaySources.values().stream().flatMap(Collection::stream).toList()) {
            MusicCollection musicCollection = playSource.getMusicCollection();
            PusherInfo pusherInfo = playSource.getPusherInfo();
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

        List<Playlist> processedPrivatePlaylists = new ArrayList<>();
        for (Playlist playlist : privatePlaylists) {
            if (playlist.getCreator().equals(playerProfile)) {
                processedPrivatePlaylists.add(playlist);
            } else {
                processedPrivatePlaylists.add(playlist.copyWithSensitiveErased());
            }
        }
        processedPrivatePlaylists.addAll(publicPlaylists);
        return new IdleSourcesData(processedPrivatePlaylists, albums);
    }

    public GetInitialStateResponse buildInitialStateFor(IPlayerClient player) {
        LoginApiService.PlayerLoginInfo loginInfo = loginApiService.getLoginInfoByPlayerUUID(player.getUUID());
        IdleSourcesData idleSourcesData = buildIdleSourcesData(loginInfo != null ? loginInfo.getProfile() : Profile.ANONYMOUS);
        return new GetInitialStateResponse(
                currentMusicDetail,
                nextIdleMusicDetail,
                nowPlayingStartTime,
                new ArrayDeque<>(musicQueue),
                idleSourcesData.playlistSources(),
                idleSourcesData.albumSources()
        );
    }

    private void debouncedUpdateAllIdlePlaySources() {
        final int token = debounceToken.incrementAndGet();
        MusicHud.EXECUTOR.execute(() -> {
            try {
                Thread.sleep(DEBOUNCE_DELAY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (debounceToken.get() == token) {
                sendUpdateAllIdlePlaySourcesMessageTo(loginApiService.getPlayerInfoMap().values());
            }
        });
    }

    public void pushMusicToQueue(long musicDetailId, PusherInfo pusherInfo) {
        List<MusicDetail> musicDetailByIds = musicApiService.getMusicDetailByIds(List.of(musicDetailId), pusherInfo.getPlayerUUID());
        if (musicDetailByIds.size() != 1) {
            throw new IllegalStateException();
        }
        MusicDetail musicDetail = musicDetailByIds.getFirst();
        musicDetail.setPusherInfo(pusherInfo);
        musicQueue.add(new QueueItem(musicDetail, UUID.randomUUID()));
        serverNetworkService.sendToPlayerInfos(loginApiService.getPlayerInfoMap().values(),
                new RefreshMusicQueueMessage(musicQueue));
        updateContinuable(true);
    }

    public void removeMusicDetailFromQueue(long id, UUID queueUniqueID, UUID playerUUID) {
        for (QueueItem queueItem : musicQueue) {
            if (queueItem.musicDetail().getId() == id && queueItem.queueUniqueID().equals(queueUniqueID)) {
                if (queueItem.musicDetail().getPusherInfo().getPlayerUUID().equals(playerUUID)) {
                    musicQueue.remove(queueItem);
                    serverNetworkService.sendToPlayerInfos(loginApiService.getPlayerInfoMap().values(),
                            new RefreshMusicQueueMessage(musicQueue));
                } else {
                    logger.warn("Player {} tried to remove music {} (id: {}) not pushed by them", playerUUID, queueItem.musicDetail().getName(), id);
                }
                return;
            }
        }
        logger.warn("Failed to remove music from queue: id {} with queue unique id {} not found", id, queueUniqueID);
    }

    public void addIdlePlaySource(long id, Class<?> type, PusherInfo pusherInfo) {
        Set<IdlePlaySource> musicCollections = idlePlaySources.getOrDefault(pusherInfo, new HashSet<>());
        IdlePlaySource idlePlaySource = new IdlePlaySource(id, type);
        idlePlaySource.setPusherInfo(pusherInfo);
        idlePlaySource.serverLoadMusicCollection(pusherInfo.getPlayerUUID());
        musicCollections.add(idlePlaySource);
        idlePlaySources.put(pusherInfo, musicCollections);
        updateContinuable(true);
        debouncedUpdateAllIdlePlaySources();
    }

    public void removeIdlePlaySource(long id, Class<?> musicCollectionClass, PusherInfo pusherInfo) {
        Set<IdlePlaySource> musicCollections = idlePlaySources.get(pusherInfo);
        if (musicCollections != null) {
            IdlePlaySource idlePlaySource = new IdlePlaySource(id, musicCollectionClass);
            idlePlaySource.setPusherInfo(pusherInfo);
            musicCollections.remove(idlePlaySource);
            if (musicCollections.isEmpty()) {
                idlePlaySources.remove(pusherInfo);
            }
            debouncedUpdateAllIdlePlaySources();
        }
    }

    public void voteSkipCurrent(long id, UUID playerUUID) {
        currentVoteInfo.vote(id, playerUUID);
    }

    public MusicResourceInfo getMusicResourceInfo(long id, Quality quality, String retryFor, UUID playerUUID) {
        try {
            List<MusicDetail> musicDetails = IMusicApiService.getInstance(ApiProvider.NCM).getMusicDetailByIds(List.of(id), null);
            if (musicDetails.size() == 1) {
                MusicDetail musicDetail = musicDetails.getFirst();
                try {
                    logger.debug("Try to load music resource info from cache with id: {}", id);
                    MusicResourceInfo musicResourceInfo = musicResourceInfoCache.get(new CacheKey(id, quality),
                            () -> {
                                logger.debug("Cache not found with id: {}, loading", id);
                                return getMusicResourceInfoWithoutCache(quality, musicDetail, playerUUID);
                            });
                    if (musicResourceInfo.getUrl().equals(retryFor)) {
                        logger.debug("Reload music resource info due to client retry for url \"{}\"", retryFor);
                        musicResourceInfo = getMusicResourceInfoWithoutCache(quality, musicDetail, playerUUID);
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

    private @NonNull MusicResourceInfo getMusicResourceInfoWithoutCache(Quality quality, MusicDetail musicDetail, UUID playerUUID) {
        MusicResourceInfo resourceInfo = musicApiService.getResourceInfo(musicDetail, quality, playerUUID);
        if (resourceInfo != null && !resourceInfo.equals(MusicResourceInfo.NONE)) {
            return resourceInfo;
        } else {
            throw new RuntimeException("Failed to get resource info for music: " + musicDetail.getName() + " (ID: " + musicDetail.getId() + ")");
        }
    }

    public void removeAllIdlePlaySource(PusherInfo pusherInfo) {
        idlePlaySources.remove(pusherInfo);
        debouncedUpdateAllIdlePlaySources();
    }

    public void reset() {
        debounceToken.incrementAndGet();
        musicQueue.clear();
        idlePlaySources.clear();
        stopSendingMusic();
        haveSentMusic = false;
        nextIdleMusicDetail = MusicDetail.NONE;
        preloadMusicDetail = MusicDetail.NONE;
    }

    private record IdleSourcesData(List<Playlist> playlistSources, List<Album> albumSources) {
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
        final Set<UUID> votedPlayers = new HashSet<>();
        MusicDetail musicDetail;
        float voteRate;

        public void vote(long id, UUID playerUUID) {
            if (MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT) {
                IClientDistUtil clientDistUtil = IClientDistUtil.getInstance();
                if (clientDistUtil.inSinglePlayer()) {
                    pusherThread.interrupt();
                    logger.info("Skip current music in singleplayer");
                    return;
                }
            }
            if (!votedPlayers.contains(playerUUID) && musicDetail.getId() == id) {
                votedPlayers.add(playerUUID);
                voteRate += 1.0f / loginApiService.getPlayerInfoMap().size();
                if (musicDetail.getPusherInfo().getPlayerUUID().equals(playerUUID)) {
                    voteRate += (float) serverConfig.getPusherVoteAdditionalRate();
                    logger.info("Pusher player \"{}\" voted for skip current music {}:{}", playerUUID, id, musicDetail.getName());
                } else {
                    logger.info("Player \"{}\" voted for skip current music {}:{}", playerUUID, id, musicDetail.getName());
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
