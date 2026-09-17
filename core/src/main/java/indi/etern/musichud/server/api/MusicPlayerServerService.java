package indi.etern.musichud.server.api;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.IdlePlaySource;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.result.MessagedResult;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.interfaces.RegisterMark;
import indi.etern.musichud.interfaces.ServerConfig;
import indi.etern.musichud.interfaces.ServerRegister;
import indi.etern.musichud.network.IPlayerClient;
import indi.etern.musichud.network.IServerNetworkService;
import indi.etern.musichud.network.payloads.pushMessages.s2c.CommonNotificationMessage;
import indi.etern.musichud.network.payloads.pushMessages.s2c.RefreshMusicQueueMessage;
import indi.etern.musichud.network.payloads.pushMessages.s2c.SwitchMusicMessage;
import indi.etern.musichud.network.payloads.pushMessages.s2c.UpdateAllIdlePlaySourcesMessage;
import indi.etern.musichud.network.payloads.pushMessages.s2c.UpdateNextToPlayMessage;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MusicPlayerServerService {
    private static final ServerConfig serverConfig = ServerConfig.getInstance();
    private static final ILoginApiService loginApiService = ILoginApiService.getInstance(ApiProvider.NCM);
    private static final long DEBOUNCE_DELAY_MILLIS = 500;
    /** Bounded wait when the only remaining idle sources are intelligent ones currently loading. */
    private static final long INTELLIGENT_LOAD_WAIT_MILLIS = 10_000;
    /** Client-facing bound for a reroll; must outlast the in-flight intelligent load wait. */
    private static final long ROTATE_TIMEOUT_MILLIS = INTELLIGENT_LOAD_WAIT_MILLIS + 5_000;
    /** Consecutive resource loading failures for the current music before it is skipped. */
    private static final int MUSIC_RESOURCE_LOAD_FAILURE_THRESHOLD = 5;
    private static volatile MusicPlayerServerService instance;
    final Map<PusherInfo, Set<IdlePlaySource>> idlePlaySources = new ConcurrentHashMap<>();
    /** Per (player, collection) monitors; entries live for the server uptime. */
    private final ConcurrentHashMap<String, Object> idleSourceKeyLocks = new ConcurrentHashMap<>();
    /** Pusher events (rerolls, resource-load deadline resets) handed to the pusher thread, which owns all preload/next state. */
    private final BlockingQueue<PusherEvent> pendingPusherEvents = new LinkedBlockingQueue<>();
    private final IMusicApiService musicApiService = IMusicApiService.getInstance(ApiProvider.NCM);
    private final CurrentVoteInfo currentVoteInfo = new CurrentVoteInfo();
    private final Logger logger = MusicHud.getLogger(MusicPlayerServerService.class);
    private final Cache<CacheKey, MusicResourceInfo> musicResourceInfoCache = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(20)
            .build();
    /** Consecutive resource loading failures of the current music; reset on any success. */
    private int musicResourceLoadFailureCount = 0;
    /** Music id the failure streak belongs to. */
    private long musicResourceLoadFailureMusicId = -1;
    /** Message attached to the next switch triggered by a resource-failure skip. */
    private volatile String pendingSwitchMessage;
    /** Music id the pusher currently awaits; the one-shot resource-load reset only applies to it. */
    private long awaitedMusicId = -1;
    /** Music id whose one-shot await-deadline reset has already been claimed. */
    private long awaitDeadlineResetClaimedMusicId = -1;
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
                    beginAwaitForMusic(playingMusic.getId());
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
                    awaitCurrentTrack(playingMusic.getId(), Math.max(1000, playingMusic.getDurationMillis() - musicMixMillis));
                } catch (InterruptedException ignored) {//When force switch
                    logger.info("Skip current, switch to nextIdle");
                    String switchMessage = pendingSwitchMessage;
                    pendingSwitchMessage = null;
                    if (switchMessage != null) {
                        message = switchMessage;
                    } else if (MusicHud.getCurrentEnvironment().getSide() != Environment.Side.CLIENT
                            || (IClientDistUtil.getInstance().inIntegratedServer() && !IClientDistUtil.getInstance().inSinglePlayer())) {
                        message = MusicHud.MOD_ID + ".text.votePassed";
                    }
                } catch (Exception e) {
                    String message1 = MusicHud.MOD_ID + ".text.musicPushError";
                    if (e instanceof MusicResourceLoadingException e1) {
                        if (e1.isUsingSubstitute()) {
                            message1 = MusicHud.MOD_ID + ".text.substituteMusicPushError";
                        }
                        MusicDetail musicDetail = e1.getMusicDetail();
                        if (musicDetail != null) {
                            nextToPlayName = musicDetail.getName();
                        }
                        nextToPlayId = e1.getId();
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

    };

    private boolean hasAvailableIdlePlaySourcesMusic(Map<PusherInfo, Set<IdlePlaySource>> idlePlaySources) {
        return !idlePlaySources.isEmpty() && idlePlaySources.values().stream()
                .flatMap(Set::stream)
                .anyMatch(playSource -> playSource.getPlayMode().isAvailable(playSource));
    }

    /**
     * R1: pick one user. Prefer users with at least one ready source; only when none is
     * ready fall back to users that merely have an available source and let
     * {@link #sampleIdleTrackByPusherInfo} wait for their loading inside the deadline.
     */
    private Optional<Traceable<MusicDetail>> getRandomMusicFromIdleSources(Map<PusherInfo, Set<IdlePlaySource>> idlePlaySources) {
        final long deadline = System.currentTimeMillis() + INTELLIGENT_LOAD_WAIT_MILLIS;
        while (true) {
            cleanupBrokenSources(idlePlaySources);

            List<PusherInfo> readyUsers = new ArrayList<>();
            List<PusherInfo> availableUsers = new ArrayList<>();
            for (Map.Entry<PusherInfo, Set<IdlePlaySource>> entry : idlePlaySources.entrySet()) {
                boolean ready = false;
                boolean available = false;
                for (IdlePlaySource playSource : entry.getValue()) {
                    if (playSource.getPlayMode().isReady(playSource)) {
                        ready = true;
                        break;
                    }
                    if (playSource.getPlayMode().isAvailable(playSource)) {
                        available = true;
                    }
                }
                if (ready) {
                    readyUsers.add(entry.getKey());
                } else if (available) {
                    availableUsers.add(entry.getKey());
                }
            }

            List<PusherInfo> pool = !readyUsers.isEmpty() ? readyUsers : availableUsers;
            if (pool.isEmpty()) {
                return Optional.empty();
            }
            PusherInfo pusherInfo = pool.get(MusicHud.RANDOM.nextInt(pool.size()));

            Optional<Traceable<MusicDetail>> sampled =
                    sampleIdleTrackByPusherInfo(idlePlaySources.get(pusherInfo), pusherInfo, deadline);
            if (sampled.isPresent()) {
                return sampled;
            }
            if (System.currentTimeMillis() >= deadline) {
                return Optional.empty();
            }
        }
    }

    /**
     * R2 + R3 for one user: rebuild the candidate sources from the live collection on
     * every round, start pending loads and wait (bounded by {@code deadline}) before
     * weighting a source and delegating the actual track pick to its play mode.
     */
    private static Optional<Traceable<MusicDetail>> sampleIdleTrackByPusherInfo(
            Collection<IdlePlaySource> userSources, PusherInfo pusherInfo, long deadline) {
        if (userSources == null || userSources.isEmpty()) {
            return Optional.empty();
        }
        while (true) {
            List<SelectableSource> readySources = new ArrayList<>();
            List<SelectableSource> loadingSources = new ArrayList<>();
            for (IdlePlaySource playSource : userSources) {
                if (playSource.getPlayMode().isReady(playSource)) {
                    readySources.add(new SelectableSource(pusherInfo, playSource));
                } else {
                    playSource.getPlayMode().ensureLoading(playSource);
                    if (playSource.getPlayMode().loadingFuture(playSource) != null) {
                        loadingSources.add(new SelectableSource(pusherInfo, playSource));
                    }
                }
            }

            if (!readySources.isEmpty()) {
                Optional<Traceable<MusicDetail>> sampled = weightedSampleRandomTrack(readySources);
                if (sampled.isPresent()) {
                    return sampled;
                }
                continue;
            }

            if (!loadingSources.isEmpty() && System.currentTimeMillis() < deadline) {
                if (!awaitAnyLoading(loadingSources, deadline)) {
                    return Optional.empty();
                }
                continue;
            }
            return Optional.empty();
        }
    }

    /** R2 source weighting (by actual track count) followed by R3 mode sampling. */
    private static Optional<Traceable<MusicDetail>> weightedSampleRandomTrack(List<SelectableSource> readySources) {
        int totalWeight = readySources.stream()
                .mapToInt(source -> source.playSource().getMusicCollection().getMusicDetails().size())
                .sum();
        SelectableSource selected = readySources.getLast();
        if (totalWeight > 0) {
            int remaining = MusicHud.RANDOM.nextInt(totalWeight);
            for (SelectableSource source : readySources) {
                remaining -= source.playSource().getMusicCollection().getMusicDetails().size();
                if (remaining < 0) {
                    selected = source;
                    break;
                }
            }
        }
        Traceable<MusicDetail> sampled = selected.playSource().sampleRandomTrack();
        return sampled == null ? Optional.empty() : Optional.of(sampled);
    }

    /**
     * Blocks until one of the loading sources completes or the deadline passes.
     *
     * @return {@code false} when the calling thread was interrupted
     */
    private static boolean awaitAnyLoading(List<SelectableSource> loadingSources, long waitDeadline) {
        CompletableFuture<?> loading = null;
        for (SelectableSource selectableSource : loadingSources) {
            CompletableFuture<?> future = selectableSource.playSource().getPlayMode().loadingFuture(selectableSource.playSource());
            if (future != null) {
                loading = future;
                break;
            }
        }
        try {
            if (loading != null) {
                loading.get(Math.max(1, waitDeadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
            } else {
                Thread.sleep(200);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException ignored) {
        }
        return true;
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

    /** True when the throwable chain carries an {@link InterruptedException} (possibly wrapped). */
    private static boolean containsInterrupt(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (t instanceof InterruptedException) {
                return true;
            }
            if (t == t.getCause()) {
                break;
            }
        }
        return false;
    }

    @Getter
    ArrayDeque<QueueItem> musicQueue = new ArrayDeque<>();
    boolean continuable;
    @Getter
    private volatile Traceable<MusicDetail> currentMusicDetail = Traceable.of(MusicDetail.NONE);
    @Getter
    private volatile Traceable<MusicDetail> nextIdleMusicDetail = Traceable.of(MusicDetail.NONE);
    private volatile Traceable<MusicDetail> preloadMusicDetail = Traceable.of(MusicDetail.NONE);
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
                    onMusicResourceLoadSuccess(id);
                    return musicResourceInfo;
                } catch (Exception e) {
                    logger.error("Failed to get resource info for music: {}", musicDetail.getName(), e);
                    onMusicResourceLoadFailure(id);
                    return MusicResourceInfo.NONE;
                }
            } else if (musicDetails.size() > 1) {
                throw new IllegalStateException();
            } else {
                onMusicResourceLoadFailure(id);
                return MusicResourceInfo.NONE;
            }
        } catch (Exception e) {
            onMusicResourceLoadFailure(id);
            return MusicResourceInfo.NONE;
        }
    }

    /**
     * Arms the one-shot resource-load deadline reset for the given music. Called by the pusher
     * before the switch is pushed so that the client's resource request can never be missed.
     * The duration-based wait stays the fallback when no client requests the resource.
     */
    private synchronized void beginAwaitForMusic(long musicId) {
        awaitedMusicId = musicId;
        awaitDeadlineResetClaimedMusicId = -1;
    }

    /**
     * Resets the consecutive failure streak: a successful load anywhere means the current music
     * is no longer considered broken.
     *
     * <p>Additionally, the first successful load for the music the pusher is currently awaiting
     * restarts that await from the moment the resource became available, so a slow load no longer
     * makes the switch happen before the track actually played through. Every further successful
     * load of the same track is ignored, and the plain duration-based wait remains untouched.</p>
     */
    private synchronized void onMusicResourceLoadSuccess(long id) {
        musicResourceLoadFailureCount = -1;
        musicResourceLoadFailureMusicId = -1;
        if (awaitedMusicId != id || awaitDeadlineResetClaimedMusicId == id) {
            return;
        }
        awaitDeadlineResetClaimedMusicId = id;
        //noinspection ResultOfMethodCallIgnored
        pendingPusherEvents.offer(new DeadlineResetRequest(id));
    }

    /**
     * Tracks consecutive resource loading failures. Once the streak for the currently playing
     * music reaches {@link #MUSIC_RESOURCE_LOAD_FAILURE_THRESHOLD}, the music is skipped.
     */
    private synchronized void onMusicResourceLoadFailure(long id) {
        Traceable<MusicDetail> current = currentMusicDetail;
        MusicDetail playingMusic = current != null ? current.value() : null;
        if (playingMusic == null || playingMusic == MusicDetail.NONE || playingMusic.getId() != id) {
            // Not the music currently playing; it can neither be skipped nor keep a streak.
            musicResourceLoadFailureCount = 0;
            musicResourceLoadFailureMusicId = -1;
            return;
        }
        if (musicResourceLoadFailureMusicId != id) {
            musicResourceLoadFailureMusicId = id;
            if (musicResourceLoadFailureCount == -1) {
                return;
            }
            musicResourceLoadFailureCount = 0;
        }
        int failures = ++musicResourceLoadFailureCount;
        if (failures < MUSIC_RESOURCE_LOAD_FAILURE_THRESHOLD) {
            return;
        }
        musicResourceLoadFailureCount = 0;
        musicResourceLoadFailureMusicId = -1;
        Thread thread = pusherThread;
        if (thread == null) {
            return;
        }
        logger.warn("Music resource loading failed {} times consecutively for current playing music: {} (ID: {}), skipping",
                failures, playingMusic.getName(), id);
        pendingSwitchMessage = MusicHud.MOD_ID + ".text.switchDueToNoValidResource";
        thread.interrupt();
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

    public MessagedResult<Void> rotateNextToPlay(PusherInfo pusherInfo) {
        if (!idlePlaySources.containsKey(pusherInfo)) {
            return MessagedResult.fail(MusicHud.MOD_ID + ".text.rotateNextFailed", null);
        }
        RerollRequest request = new RerollRequest(pusherInfo, new CompletableFuture<>());
        pendingPusherEvents.add(request);
        try {
            return request.result().get(ROTATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            //noinspection ResultOfMethodCallIgnored
            pendingPusherEvents.remove(request);
            return MessagedResult.fail(MusicHud.MOD_ID + ".text.rotateNextFailed", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return MessagedResult.fail(MusicHud.MOD_ID + ".text.rotateNextFailed", null);
        } catch (ExecutionException e) {
            return MessagedResult.fail(MusicHud.MOD_ID + ".text.rotateNextFailed", null);
        }
    }

    /**
     * Waits out the current track, servicing reroll requests while idle. The track's end
     * time bounds every reroll.
     *
     * <p>The initial deadline is the plain duration-based wait. When the first resource load
     * for this track succeeds, a {@link DeadlineResetRequest} restarts that wait from the
     * moment the resource became available, giving a slow-loading track its full airtime.
     * Without any resource request the behaviour is exactly the duration-based fallback.</p>
     */
    private void awaitCurrentTrack(long musicId, long millis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + millis;
        while (true) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return;
            }
            PusherEvent event = pendingPusherEvents.poll(remaining, TimeUnit.MILLISECONDS);
            switch (event) {
                case null -> {
                    return;
                }
                case DeadlineResetRequest(long id) -> {
                    if (id == musicId) {
                        logger.info("Resource loaded for current music (ID: {}), restarting track wait", musicId);
                        deadline = System.currentTimeMillis() + millis;
                    }
                }
                case RerollRequest reroll -> handleReroll(reroll, deadline);
                default -> {
                }
            }
        }
    }

    private void handleReroll(RerollRequest request, long deadline) {
        if (request.result().isDone()) {
            return;
        }
        PusherInfo pusherInfo = request.pusher();
        try {
            Traceable<MusicDetail> nextToPlay = preloadMusicDetail;
            if (nextToPlay == null || !pusherInfo.equals(nextToPlay.value().getPusherInfo())) {
                request.result().complete(MessagedResult.fail(MusicHud.MOD_ID + ".text.rotateNextNotOwned", null));
                return;
            }
            Set<IdlePlaySource> userSources = idlePlaySources.get(pusherInfo);
            if (userSources == null || userSources.isEmpty()) {
                request.result().complete(MessagedResult.fail(MusicHud.MOD_ID + ".text.rotateNextFailed", null));
                return;
            }
            Optional<Traceable<MusicDetail>> rerolled = sampleIdleTrackByPusherInfo(userSources, pusherInfo, deadline);
            if (rerolled.isEmpty()) {
                request.result().complete(MessagedResult.fail(MusicHud.MOD_ID + ".text.rotateNextFailed", null));
                return;
            }
            preloadMusicDetail = rerolled.get();
            nextIdleMusicDetail = preloadMusicDetail;
            serverNetworkService.sendToPlayerInfos(
                    loginApiService.getPlayerInfoMap().values(),
                    new UpdateNextToPlayMessage(preloadMusicDetail));
            request.result().complete(MessagedResult.success(null));
        } catch (Exception e) {
            boolean interrupted = containsInterrupt(e) || Thread.currentThread().isInterrupted();
            logger.error("Failed to rotate next to play for {}", pusherInfo, e);
            request.result().complete(MessagedResult.fail(MusicHud.MOD_ID + ".text.rotateNextFailed", null));
            if (interrupted) {
                // Re-assert so the outer wait turns this into a skip instead of swallowing it
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Event consumed by the pusher thread while it awaits the current track. */
    private sealed interface PusherEvent permits RerollRequest, DeadlineResetRequest {
    }

    private record RerollRequest(PusherInfo pusher, CompletableFuture<MessagedResult<Void>> result) implements PusherEvent {
    }

    /** Requests a one-shot restart of the current await, tagged with the music it belongs to. */
    private record DeadlineResetRequest(long musicId) implements PusherEvent {
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

    record SelectableSource(PusherInfo pusherInfo, IdlePlaySource playSource) {
    }
}
