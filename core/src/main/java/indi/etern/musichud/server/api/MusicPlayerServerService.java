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
import indi.etern.musichud.server.api.playmode.PlayMode;
import indi.etern.musichud.throwable.MusicResourceLoadingException;
import indi.etern.musichud.throwable.PlaylistTypeUnsupportedException;
import indi.etern.musichud.utils.IClientDistUtil;
import lombok.*;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MusicPlayerServerService {
    private static final ServerConfig serverConfig = ServerConfig.getInstance();
    private static final ILoginApiService loginApiService = ILoginApiService.getInstance(ApiProvider.NCM);
    private static final long DEBOUNCE_DELAY_MILLIS = 500;
    /** Bounded wait when the only remaining idle sources are intelligent ones currently loading. */
    private static final long INTELLIGENT_LOAD_WAIT_MILLIS = 10_000;
    private static volatile MusicPlayerServerService instance;
    final Map<PusherInfo, Set<IdlePlaySource>> idlePlaySources = new ConcurrentHashMap<>();
    /** Per (player, collection) monitors; entries live for the server uptime. */
    private final ConcurrentHashMap<String, Object> idleSourceKeyLocks = new ConcurrentHashMap<>();
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
            while (MusicPlayerServerService.this.continuable) {//TODO: better preload push to client
                Traceable<MusicDetail> nextToPlay;
                String nextToPlayName = "unknown";
                long nextToPlayId = -1;
                Map<PusherInfo, Set<IdlePlaySource>> idlePlaySources = MusicPlayerServerService.this.idlePlaySources;
                try {
                    if (musicQueue.isEmpty()) {
                        if (!hasAvailableIdlePlaySourcesMusic(idlePlaySources)) {
                            break;
                        }
                        Optional<Traceable<MusicDetail>> optionalMusicDetail = getRandomMusicFromIdleSources(idlePlaySources);
                        if (optionalMusicDetail.isEmpty()) {
                            break;
                        } else {
                            Traceable<MusicDetail> traceable = optionalMusicDetail.get();
                            if (preloadMusicDetail == null || preloadMusicDetail.value().equals(MusicDetail.NONE)) {
                                preloadMusicDetail = traceable;
                                Optional<Traceable<MusicDetail>> optionalMusicDetail1 = getRandomMusicFromIdleSources(idlePlaySources);
                                if (optionalMusicDetail1.isPresent()) {
                                    nextToPlay = preloadMusicDetail;
                                    nextIdleMusicDetail = optionalMusicDetail1.get();
                                    preloadMusicDetail = nextIdleMusicDetail;
                                } else {
                                    nextToPlay = traceable;
                                    preloadMusicDetail = Traceable.of(MusicDetail.NONE);
                                }
                            } else {
                                nextToPlay = preloadMusicDetail;
                                preloadMusicDetail = traceable;
                            }
                            nextToPlayName = nextToPlay.value().getName();
                            nextToPlayId = nextToPlay.value().getId();
                            PusherInfo pusherInfo = nextToPlay.value().getPusherInfo();
                            if (pusherInfo != null &&
                                    loginedPlayerInfoMap.keySet().stream().noneMatch(
                                            uuid -> uuid.equals(pusherInfo.getPlayerUUID())
                                    )
                            ) {
                                continue;
                            }
                        }
                    } else {
                        nextToPlay = musicQueue.remove().musicDetail();
                        nextToPlayName = nextToPlay.value().getName();
                        nextToPlayId = nextToPlay.value().getId();
                        serverNetworkService.sendToPlayerInfos(loginedPlayerInfoMap.values(),
                                new RefreshMusicQueueMessage(musicQueue));
                    }

                    nextIdleMusicDetail = preloadMusicDetail != null ? preloadMusicDetail : Traceable.of(MusicDetail.NONE);

                    MusicDetail playingMusic = nextToPlay.value();
                    if (playingMusic.getLyricInfo() == null || playingMusic.getLyricInfo().equals(LyricInfo.NONE)) {
                        playingMusic.setLyricInfo(musicApiService.getLyricInfo(playingMusic));
                    }
                    serverNetworkService.sendToPlayerInfos(
                            loginedPlayerInfoMap.values(),
                            new SwitchMusicMessage(nextToPlay, nextIdleMusicDetail, message)
                    );
                    message = "";
                    currentVoteInfo.resetTo(playingMusic);
                    haveSentMusic = true;
                    currentMusicDetail = nextToPlay;
                    nowPlayingStartTime = ZonedDateTime.now();
                    logger.info("Switched to music: {} (ID: {})", playingMusic.getName(), playingMusic.getId());
                    int musicMixMillis = 1200;
                    //noinspection BusyWait
                    Thread.sleep(Math.max(1000, playingMusic.getDurationMillis() - musicMixMillis));
                } catch (InterruptedException ignored) {//When force switch
                    logger.info("Skip current, switch to nextIdle");
                    if (MusicHud.getCurrentEnvironment().getSide() != Environment.Side.CLIENT
                            || (IClientDistUtil.getInstance().inIntegratedServer() && !IClientDistUtil.getInstance().inSinglePlayer())) {
                        message = MusicHud.MOD_ID + ".text.votePassed";
                    }
                } catch (Exception e) {
                    String message1;
                    if (e instanceof MusicResourceLoadingException e1 && e1.isUsingSubstitute()) {
                        message1 = MusicHud.MOD_ID + ".text.substituteMusicPushError";
                        MusicDetail musicDetail = e1.getMusicDetail();
                        if (musicDetail != null) {
                            nextToPlayName = musicDetail.getName();
                        }
                        nextToPlayId = e1.getId();
                    } else {
                        message1 = MusicHud.MOD_ID + ".text.musicPushError";
                    }
                    serverNetworkService.sendToPlayerInfos(
                            loginedPlayerInfoMap.values(),
                            new CommonNotificationMessage(MessagedResult.fail(message1, null))
                    );
                    logger.error("Failed to push music: {} (id: {})",
                            nextToPlayName,
                            nextToPlayId,
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
            // Restart on remaining work; only a drained state stops the pusher for good
            if (!musicQueue.isEmpty() || hasAvailableIdlePlaySourcesMusic(MusicPlayerServerService.this.idlePlaySources)) {
                updateContinuable(true);
            } else {
                MusicPlayerServerService.this.stopSendingMusic();
                logger.info("Music Pusher stopped");
            }
        }

        private boolean hasAvailableIdlePlaySourcesMusic(Map<PusherInfo, Set<IdlePlaySource>> idlePlaySources) {
            return !idlePlaySources.isEmpty() && idlePlaySources.values().stream()
                    .flatMap(Set::stream)
                    .anyMatch(playSource -> playSource.getPlayMode().isAvailable(playSource));
        }

        private Optional<Traceable<MusicDetail>> getRandomMusicFromIdleSources(Map<PusherInfo, Set<IdlePlaySource>> idlePlaySources) {
            record SelectableSource(PusherInfo pusherInfo, IdlePlaySource playSource) {
            }
            final long waitDeadline = System.currentTimeMillis() + INTELLIGENT_LOAD_WAIT_MILLIS;
            while (true) {
                cleanupBrokenSources(idlePlaySources);

                List<SelectableSource> readySources = new ArrayList<>();
                List<SelectableSource> loadingSources = new ArrayList<>();
                for (Map.Entry<PusherInfo, Set<IdlePlaySource>> entry : idlePlaySources.entrySet()) {
                    for (IdlePlaySource playSource : entry.getValue()) {
                        if (playSource.getPlayMode().isReady(playSource)) {
                            readySources.add(new SelectableSource(entry.getKey(), playSource));
                        } else {
                            playSource.getPlayMode().ensureLoading(playSource);
                            if (playSource.getPlayMode().loadingFuture(playSource) != null) {
                                loadingSources.add(new SelectableSource(entry.getKey(), playSource));
                            }
                        }
                    }
                }

                if (!readySources.isEmpty()) {
                    // R1: uniform user pick among users with ready sources
                    Map<PusherInfo, List<SelectableSource>> byUser = new LinkedHashMap<>();
                    for (SelectableSource selectableSource : readySources) {
                        byUser.computeIfAbsent(selectableSource.pusherInfo(), k -> new ArrayList<>()).add(selectableSource);
                    }
                    List<PusherInfo> users = new ArrayList<>(byUser.keySet());
                    PusherInfo pusherInfo = users.get(MusicHud.RANDOM.nextInt(users.size()));

                    // R2: weight = actual track list size of the collection
                    List<SelectableSource> userSources = byUser.get(pusherInfo);
                    int totalWeight = userSources.stream()
                            .mapToInt(source -> source.playSource().getMusicCollection().getMusicDetails().size())
                            .sum();
                    SelectableSource selected = userSources.getLast();
                    if (totalWeight > 0) {
                        int remaining = MusicHud.RANDOM.nextInt(totalWeight);
                        for (SelectableSource source : userSources) {
                            remaining -= source.playSource().getMusicCollection().getMusicDetails().size();
                            if (remaining < 0) {
                                selected = source;
                                break;
                            }
                        }
                    }

                    // R3: sample according to the selected source's play mode
                    Traceable<MusicDetail> sampled = selected.playSource().sampleRandomTrack();
                    if (sampled != null) {
                        return Optional.of(sampled);
                    }
                    continue;
                }

                // Only loading sources remain: bounded block, then retry
                if (!loadingSources.isEmpty() && System.currentTimeMillis() < waitDeadline) {
                    CompletableFuture<?> loading = null;
                    for (SelectableSource selectableSource : loadingSources) {
                        CompletableFuture<?> future = selectableSource.playSource().getPlayMode().loadingFuture(selectableSource.playSource());
                        if (future != null) {
                            loading = future;
                            break;
                        }
                    }
                    if (loading != null) {
                        try {
                            //noinspection BusyWait
                            loading.get(Math.max(1, waitDeadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return Optional.empty();
                        } catch (ExecutionException | TimeoutException ignored) {
                        }
                    } else {
                        try {
                            //noinspection BusyWait
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return Optional.empty();
                        }
                    }
                    continue;
                }
                return Optional.empty();
            }
        }

        private void cleanupBrokenSources(Map<PusherInfo, Set<IdlePlaySource>> idlePlaySources) {
            for (Map.Entry<PusherInfo, Set<IdlePlaySource>> entry : idlePlaySources.entrySet()) {
                for (IdlePlaySource playSource : List.copyOf(entry.getValue())) {
                    if (playSource.getPlayMode().isBroken(playSource)) {
                        notifySourceRemoved(entry.getKey());
                        removeIdlePlaySource(playSource, entry.getKey());
                    }
                }
            }
        }

        private void notifySourceRemoved(PusherInfo pusherInfo) {
            LoginApiService.PlayerLoginInfo ownerLoginInfo =
                    loginApiService.getLoginInfoByPlayerUUID(pusherInfo.getPlayerUUID());
            if (ownerLoginInfo != null) {
                serverNetworkService.sendToPlayer(ownerLoginInfo.getPlayer(),
                        new CommonNotificationMessage(MessagedResult.fail(MusicHud.MOD_ID + ".text.idleSourceLoadFailed", null)));
            }
        }
    };
    @Getter
    ArrayDeque<QueueItem> musicQueue = new ArrayDeque<>();
    boolean continuable;
    @Getter
    private volatile Traceable<MusicDetail> currentMusicDetail = Traceable.of(MusicDetail.NONE);
    @Getter
    private Traceable<MusicDetail> nextIdleMusicDetail = Traceable.of(MusicDetail.NONE);
    private Traceable<MusicDetail> preloadMusicDetail = Traceable.of(MusicDetail.NONE);
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
        currentMusicDetail = Traceable.of(MusicDetail.NONE);
        if (haveSentMusic) {
            haveSentMusic = false;
            serverNetworkService.sendToPlayerInfos(
                    loginApiService.getPlayerInfoMap().values(),
                    new SwitchMusicMessage(Traceable.of(MusicDetail.NONE), Traceable.of(MusicDetail.NONE), "")
            );
            currentVoteInfo.resetTo(MusicDetail.NONE);
        }
    }

    public void sendUpdateAllIdlePlaySourcesMessageTo(Collection<LoginApiService.PlayerLoginInfo> playerLoginInfos) {
        for (LoginApiService.PlayerLoginInfo playerLoginInfo : playerLoginInfos) {
            if (playerLoginInfo != null) {
                List<IdlePlaySource> playSources = buildIdleSourcesData(playerLoginInfo.getProfile());
                serverNetworkService.sendToPlayer(playerLoginInfo.getPlayer(),
                        new UpdateAllIdlePlaySourcesMessage(playSources));
            }
        }
    }

    private List<IdlePlaySource> buildIdleSourcesData(Profile playerProfile) {
        List<IdlePlaySource> list = new ArrayList<>();
        for (Set<IdlePlaySource> playSources : idlePlaySources.values()) {
            for (IdlePlaySource idlePlaySource : playSources) {
                if (idlePlaySource.getMusicCollection() instanceof Playlist playlist && !playlist.getCreator().equals(playerProfile)) {
                    IdlePlaySource idlePlaySource1 = IdlePlaySource.of(playlist.sensitiveErased(), idlePlaySource.getPlayMode());
                    idlePlaySource1.setPusherInfo(idlePlaySource.getPusherInfo());
                    list.add(idlePlaySource1);
                } else {
                    list.add(idlePlaySource);
                }
            }
        }
        return list;
    }

    public GetInitialStateResponse buildInitialStateFor(IPlayerClient player) {
        LoginApiService.PlayerLoginInfo loginInfo = loginApiService.getLoginInfoByPlayerUUID(player.getUUID());
        List<IdlePlaySource> idleSources = buildIdleSourcesData(loginInfo != null ? loginInfo.getProfile() : Profile.ANONYMOUS);
        return new GetInitialStateResponse(
                currentMusicDetail,
                nextIdleMusicDetail,
                nowPlayingStartTime,
                new ArrayDeque<>(musicQueue),
                idleSources);
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

    public void pushMusicToQueue(Traceable<Long> music, PusherInfo pusherInfo) {
        long musicDetailId = music.value();
        try {
            List<MusicDetail> musicDetailByIds = musicApiService.getMusicDetailByIds(List.of(musicDetailId), pusherInfo.getPlayerUUID());
            if (musicDetailByIds.size() != 1) {
                throw new IllegalStateException();
            }
            MusicDetail musicDetail = musicDetailByIds.getFirst();
            musicDetail.setPusherInfo(pusherInfo);
            musicQueue.add(new QueueItem(Traceable.of(musicDetail, music.source()), UUID.randomUUID()));
            serverNetworkService.sendToPlayerInfos(loginApiService.getPlayerInfoMap().values(),
                    new RefreshMusicQueueMessage(musicQueue));
            updateContinuable(true);
        } catch (InterruptedException | TimeoutException e) {
            logger.error("Failed to load music and pushed to queue, ID: {}", musicDetailId, e);
        }
    }

    public void removeMusicDetailFromQueue(long id, UUID queueUniqueID, UUID playerUUID) {
        for (QueueItem queueItem : musicQueue) {
            if (queueItem.musicDetail().value().getId() == id && queueItem.queueUniqueID().equals(queueUniqueID)) {
                if (queueItem.musicDetail().value().getPusherInfo().getPlayerUUID().equals(playerUUID)) {
                    musicQueue.remove(queueItem);
                    serverNetworkService.sendToPlayerInfos(loginApiService.getPlayerInfoMap().values(),
                            new RefreshMusicQueueMessage(musicQueue));
                } else {
                    logger.warn("Player {} tried to remove music {} (id: {}) not pushed by them", playerUUID, queueItem.musicDetail().value().getName(), id);
                }
                return;
            }
        }
        logger.warn("Failed to remove music from queue: id {} with queue unique id {} not found", id, queueUniqueID);
    }

    /**
     * Validates and registers an idle play source, loading the collection (and,
     * for INTELLIGENT, the first page with a random seed) before it becomes
     * visible. An incoming source matching an existing entry of the same
     * collection (id+type) only updates the play mode; on failure the previous
     * entry is kept untouched.
     *
     * <p>Requests are serialized per (player, collection): handlers run on
     * concurrent virtual threads, so two rapid mode switches could otherwise
     * interleave between the existing-entry check and the write, leaving two
     * modes of the same collection active at once. Serializing makes the last
     * processed request win.</p>
     */
    public MessagedResult<Void> addIdlePlaySource(IdlePlaySource idlePlaySource, PusherInfo pusherInfo) {
        synchronized (idleSourceKeyLock(pusherInfo.getPlayerUUID(), idlePlaySource)) {
            return addIdlePlaySourceLocked(idlePlaySource, pusherInfo);
        }
    }

    private MessagedResult<Void> addIdlePlaySourceLocked(IdlePlaySource idlePlaySource, PusherInfo pusherInfo) {
        try {
            idlePlaySource.setPusherInfo(pusherInfo);
            Set<IdlePlaySource> userSources = idlePlaySources.computeIfAbsent(pusherInfo, k -> ConcurrentHashMap.newKeySet());
            IdlePlaySource existing = userSources.stream()
                    .filter(s -> s.getId() == idlePlaySource.getId() && s.getType() == idlePlaySource.getType())
                    .findFirst().orElse(null);
            PlayMode mode = idlePlaySource.getPlayMode();
            if (existing != null && existing.getPlayMode() == mode) {
                return MessagedResult.success(null);
            }
            if (existing == null) {
                idlePlaySource.serverLoadMusicCollection(pusherInfo.getPlayerUUID());
            } else {
                // Mode switch: reuse the loaded collection, keep the old entry until the new mode is ready
                idlePlaySource.setMusicCollection(existing.getMusicCollection());
                if (idlePlaySource.getMusicCollection() == null) {
                    idlePlaySource.serverLoadMusicCollection(pusherInfo.getPlayerUUID());
                }
            }
            MusicCollection collection = idlePlaySource.getMusicCollection();
            if (collection == null || collection == Playlist.EMPTY || collection == Album.NONE
                    || collection.getMusicDetails().isEmpty()) {
                return MessagedResult.fail(MusicHud.MOD_ID + ".text.idleSourceLoadFailed", null);
            }
            if (!mode.supports(collection)) {
                return MessagedResult.fail(MusicHud.MOD_ID + ".text.intelligentUnsupported", null);
            }
            mode.onAdd(idlePlaySource, pusherInfo);
            if (existing != null) {
                userSources.remove(existing);
                existing.getPlayMode().onRemoved(existing, pusherInfo);
            }
            userSources.add(idlePlaySource);
            updateContinuable(true);
            debouncedUpdateAllIdlePlaySources();
            return MessagedResult.success(null);
        } catch (PlaylistTypeUnsupportedException e) {
            return MessagedResult.fail(MusicHud.MOD_ID + ".text.intelligentUnsupported", null);
        } catch (Exception e) {
            logger.error("Failed to add idle play source: {} ({}, mode: {})",
                    idlePlaySource.getId(), idlePlaySource.getType().getSimpleName(), idlePlaySource.getPlayMode(), e);
            return MessagedResult.fail(MusicHud.MOD_ID + ".text.idleSourceLoadFailed", null);
        }
    }

    /**
     * Monitor serializing mutating access to one (player, collection) idle source entry.
     * Virtual-thread friendly: blocking here parks the vthread instead of a carrier thread.
     */
    private Object idleSourceKeyLock(UUID playerUUID, IdlePlaySource idlePlaySource) {
        return idleSourceKeyLocks.computeIfAbsent(
                playerUUID + ":" + idlePlaySource.getType().getName() + ":" + idlePlaySource.getId(),
                k -> new Object());
    }

    public void removeIdlePlaySource(IdlePlaySource idlePlaySource, PusherInfo pusherInfo) {
        synchronized (idleSourceKeyLock(pusherInfo.getPlayerUUID(), idlePlaySource)) {
            Set<IdlePlaySource> musicCollections = idlePlaySources.get(pusherInfo);
            if (musicCollections != null) {
                idlePlaySource.setPusherInfo(pusherInfo);
                musicCollections.remove(idlePlaySource);
                if (musicCollections.isEmpty()) {
                    idlePlaySources.remove(pusherInfo);
                }
                idlePlaySource.getPlayMode().onRemoved(idlePlaySource, pusherInfo);
                debouncedUpdateAllIdlePlaySources();
            }
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
        PlayMode.onAllRemoved(pusherInfo.getPlayerUUID());
        debouncedUpdateAllIdlePlaySources();
    }

    public void reset() {
        debounceToken.incrementAndGet();
        musicQueue.clear();
        idlePlaySources.clear();
        PlayMode.resetAll();
        stopSendingMusic();
        haveSentMusic = false;
        nextIdleMusicDetail = Traceable.of(MusicDetail.NONE);
        preloadMusicDetail = Traceable.of(MusicDetail.NONE);
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
