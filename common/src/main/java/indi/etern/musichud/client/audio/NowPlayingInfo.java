package indi.etern.musichud.client.audio;

import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.LyricInfo;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.QueueItem;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.dto.LyricLine;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.utils.PlayerInfoUtil;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.lyrics.FullLineLyricParser;
import indi.etern.musichud.client.utils.lyrics.WordByWordLyricParser;
import indi.etern.musichud.interfaces.ClientConfig;
import io.github.selemba1000.*;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class NowPlayingInfo {
    private static volatile NowPlayingInfo instance = null;
    private final Logger logger = MusicHud.getLogger(NowPlayingInfo.class);
    @Getter
    private final Set<Consumer<LyricLine>> lyricLineUpdateListener = new HashSet<>();
    @Getter
    private final Set<BiConsumer<MusicDetail, MusicDetail>> musicSwitchListener = new HashSet<>();
    private final AtomicReference<ArrayDeque<LyricLine>> atomicLyricLines = new AtomicReference<>();
    private final ClientConfig clientConfig = ClientConfig.getInstance();
    private volatile JMTC jmtc;
    private final ExecutorService jmtcExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MH-JMTC");
        t.setDaemon(true);
        return t;
    });
    @Setter
    @Getter
    private Duration updateInAdvanceDuration = Duration.of(500, ChronoUnit.MILLIS);
    @Getter
    private MusicDetail currentlyPlayingMusicDetail;
    private MusicDetail nextToPlayIdleMusicDetail;
    @Getter
    private volatile Duration musicDuration = null;
    @Getter
    private volatile ZonedDateTime musicStartTime = null;
    @Getter
    private ArrayDeque<LyricLine> lyricLines;
    @Getter
    private LyricLine currentLyricLine;
    private volatile Thread lyricUpdaterVThread;
    private final AtomicLong lyricUpdaterGeneration = new AtomicLong(0);

    final Runnable lyricUpdater = () -> {
        Thread thread = Thread.currentThread();
        lyricUpdaterVThread = thread;
        thread.setName("MHWorker-Lyrics-Updater");
        long generation = lyricUpdaterGeneration.get();
        while (generation == lyricUpdaterGeneration.get()) {
            if (this.musicStartTime == null) {
                break;
            }
            ArrayDeque<LyricLine> lyricLines1 = this.atomicLyricLines.get();
            if (lyricLines1 == null || lyricLines1.isEmpty()) break;
            LyricLine line = lyricLines1.peek();
            if (line != null) {
                if (line.getStartTime() != null) {
                    if (ZonedDateTime.now().isAfter(this.musicStartTime.plus(getCallTime(line)))) {
                        lyricLines1.poll();
                        currentLyricLine = line;
                        LyricLine next = lyricLines1.peek();
                        if (next == null) {
                            callLyricsUpdateListeners(line);
                            logger.debug("lyricsUpdater stopped due to no more lyrics");
                            break;
                        } else if (ZonedDateTime.now().isBefore(this.musicStartTime.plus(getCallTime(next)))) {
                            callLyricsUpdateListeners(line);
                            if (sleepUntil(this.musicStartTime, getCallTime(next))) {
                                logger.debug("lyricsUpdater interruption");
                            }
                        }
                    } else if (sleepUntil(this.musicStartTime, getCallTime(line))) {
                        logger.debug("lyricsUpdater interruption");
                    }
                } else {
                    lyricLines1.poll();
                }
            }
        }
        if (lyricUpdaterVThread == thread) {
            lyricUpdaterVThread = null;
        }
    };

    private NowPlayingInfo() {
        jmtcExecutor.execute(this::initJmtc);
    }

    public static NowPlayingInfo getInstance() {
        if (instance == null) {
            synchronized (NowPlayingInfo.class) {
                if (instance == null) {
                    instance = new NowPlayingInfo();
                }
            }
        }
        return instance;
    }

    private void initJmtc() {
        jmtc = JMTC.getInstance(new JMTCSettings("Minecraft-MusicHUD", "Minecraft-MusicHUD"));
        JMTCCallbacks jmtcCallbacks = new JMTCCallbacks();
        jmtcCallbacks.onPlay = () -> {
            MusicHud.EXECUTOR.execute(() -> {
                clientConfig.setMuted(false);
                clientConfig.save();
            });
            postJmtc(() -> {
                jmtc.setPlayingState(JMTCPlayingState.PLAYING);
                jmtc.updateDisplay();
            });
        };
        jmtcCallbacks.onPause = () -> {
            MusicHud.EXECUTOR.execute(() -> {
                clientConfig.setMuted(true);
                clientConfig.save();
            });
            postJmtc(() -> {
                jmtc.setPlayingState(JMTCPlayingState.PAUSED);
                jmtc.updateDisplay();
            });
        };
        jmtcCallbacks.onNext = () -> MusicHud.EXECUTOR.execute(() -> MusicService.getInstance().voteForSkipCurrent());
        jmtcCallbacks.onVolume = (volume) -> {
            clientConfig.forceSetSoundVolume(Math.clamp(volume.intValue() * 100L, 0, 100));
            clientConfig.save();
        };

        jmtc.setEnabled(true);
        jmtc.setEnabledButtons(new JMTCEnabledButtons(true, true, false, true, false));
        jmtc.setCallbacks(jmtcCallbacks);
        jmtc.setPlayingState(JMTCPlayingState.STOPPED);
        jmtc.setMediaType(JMTCMediaType.Music);
        jmtc.setParameters(new JMTCParameters(JMTCParameters.LoopStatus.Track, clientConfig.getMuted() ? 0 : clientConfig.getSoundVolume() / 100.0, 1.0, false));
        jmtc.updateDisplay();
    }

    private void postJmtc(Runnable task) {
        jmtcExecutor.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.warn("JMTC task failed", e);
            }
        });
    }

    /**
     * Called by the play worker thread (throttled ~1s) to push position/state.
     */
    public void onPlaybackTick() {
        MusicDetail current = currentlyPlayingMusicDetail;
        if (current == null || current.equals(MusicDetail.NONE)) {
            return;
        }
        Duration played = getPlayedDuration();
        Duration duration = musicDuration;
        boolean muted = clientConfig.getMuted();
        long position = played.toMillis();
        postJmtc(() -> {
            jmtc.setPosition(position);
            JMTCPlayingState state = played.equals(duration)
                    ? JMTCPlayingState.STOPPED
                    : muted ? JMTCPlayingState.PAUSED : JMTCPlayingState.PLAYING;
            jmtc.setPlayingState(state);
            jmtc.updateDisplay();
        });
    }

    private void postMediaInfo(MusicDetail musicDetail) {
        String artists = musicDetail.getArtists().stream()
                .map(Artist::getName)
                .reduce((a, b) -> a + " / " + b)
                .orElse("");
        String album = musicDetail.getAlbum().getName();
        ArrayList<MusicDetail> albumTracks = new ArrayList<>(musicDetail.getAlbum().getMusicDetails());
        long durationMillis = musicDetail.getDurationMillis();
        int tracks = albumTracks.size();
        int track = albumTracks.indexOf(musicDetail);
        postJmtc(() -> {
            jmtc.setTimelineProperties(new JMTCTimelineProperties(0L, durationMillis, 0L, durationMillis));
            jmtc.setMediaProperties(new JMTCMusicProperties(musicDetail.getName(), artists, album, artists, new String[]{""}, tracks, track, null));
            jmtc.setPlayingState(JMTCPlayingState.PLAYING);
            jmtc.updateDisplay();
        });
        loadAlbumArtAsync(musicDetail, artUri -> postJmtc(() ->
                jmtc.setMediaProperties(new JMTCMusicProperties(musicDetail.getName(), artists, album, artists, new String[]{""}, tracks, track, artUri))));
    }

    private void loadAlbumArtAsync(MusicDetail musicDetail, Consumer<URI> onReady) {
        String picUrl = musicDetail.getAlbum().getPicUrl();
        MusicHud.EXECUTOR.submit(() -> {
            URI artUri = null;
            if (picUrl.startsWith("http")) {
                try {
                    String suffix = "png";
                    String[] splits = picUrl.split("\\.");
                    if (splits.length > 1) {
                        suffix = splits[splits.length - 1];
                    }
                    Path tempFile = Files.createTempFile("MusicHUD-SMTC-Album", "." + suffix);
                    tempFile.toFile().deleteOnExit();
                    artUri = ImageUtils.downloadAsync(picUrl, inputStream -> {
                        try {
                            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                            return tempFile.toUri();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }, false).join();
                } catch (Exception e) {
                    logger.warn("Failed to download album art for SMTC", e);
                }
            } else {
                try {
                    Path tempFile = Files.createTempFile("MusicHUD-SMTC-Icon", ".png");
                    tempFile.toFile().deleteOnExit();
                    try (InputStream iconStream = getClass().getResourceAsStream("/assets/music_hud/icon.png")) {
                        if (iconStream != null) {
                            Files.copy(iconStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                            artUri = tempFile.toUri();
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Failed to set default SMTC icon", e);
                }
            }
            if (artUri != null) {
                onReady.accept(artUri);
            }
        });
    }

    private Duration getCallTime(LyricLine line) {
        return line.getStartTime().minus(updateInAdvanceDuration);
    }

    public float getProgressRate() {
        if (musicDuration == null || musicStartTime == null) {
            return 0.0f;
        }
        return (float) Duration.between(musicStartTime, ZonedDateTime.now()).toMillis() / musicDuration.toMillis();
    }

    public void switchMusicInfo(MusicDetail musicDetail, MusicDetail idleNextToPlay) {
        MusicDetail previous = currentlyPlayingMusicDetail;
        currentlyPlayingMusicDetail = musicDetail;
        currentLyricLine = null;
        nextToPlayIdleMusicDetail = idleNextToPlay;
        if (!musicDetail.equals(MusicDetail.NONE)) {
            musicDuration = Duration.ofMillis(musicDetail.getDurationMillis());
            postMediaInfo(musicDetail);
        } else {
            musicDuration = null;
            postJmtc(() -> {
                jmtc.setPlayingState(JMTCPlayingState.CLOSED);
                jmtc.updateDisplay();
            });
        }
        musicStartTime = null;
        LyricInfo lyricInfo = musicDetail.getLyricInfo();
        ArrayDeque<LyricLine> lyricLines;
        if (!lyricInfo.equals(LyricInfo.NONE)) {
            try {
                if (lyricInfo.withWordByWordLyric()) {
                    lyricLines = WordByWordLyricParser.parse(musicDetail);
                } else {
                    lyricLines = FullLineLyricParser.parse(musicDetail);
                }
                this.lyricLines = lyricLines;
                this.atomicLyricLines.set(new ArrayDeque<>(lyricLines));
            } catch (Exception e) {
                logger.warn("Failed to load lyrics of music: {} (id:{}), exception: {}: {}", musicDetail.getName(), musicDetail.getId(), e.getClass().getName(), e.getMessage());
            }
        } else {
            this.lyricLines = null;
            this.atomicLyricLines.set(null);
        }
        try {
            MuiModApi.postToUiThread(() -> MainFragment.switchMusic(musicDetail, idleNextToPlay, this.lyricLines));
        } catch (IllegalStateException ignored) {
        }
        MusicHud.EXECUTOR.submit(() -> {
            // 补偿音频过渡
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
            HudRendererManager.getInstance().switchMusic(musicDetail);
        });
        List.copyOf(musicSwitchListener).forEach(consumer -> {
            consumer.accept(previous, musicDetail);
        });
        callLyricsUpdateListeners(null);
    }

    public void startAt(ZonedDateTime zonedDateTime) {
        musicStartTime = Objects.requireNonNullElseGet(zonedDateTime, ZonedDateTime::now);
        if (lyricLines != null && !lyricLines.isEmpty()) {
            lyricUpdaterGeneration.incrementAndGet();
            Thread old = lyricUpdaterVThread;
            if (old != null) {
                old.interrupt();
            }
            // 代际递增后总是新提交一个更新器，旧更新器检测到代际变化自行退出，避免并发消费歌词
            MusicHud.EXECUTOR.execute(lyricUpdater);
        }
    }

    private void callLyricsUpdateListeners(LyricLine line) {
        Set.copyOf(lyricLineUpdateListener).forEach(c -> {
            try {
                c.accept(line);
            } catch (Exception e) {
                logger.warn(e);
            }
        });
    }

    private boolean sleepUntil(ZonedDateTime musicStartTime, Duration startTime) {
        Duration between = Duration.between(ZonedDateTime.now(), musicStartTime.plus(startTime));
        if (between.isPositive()) {
            try {
                Thread.sleep(between);
            } catch (InterruptedException ignored) {
                return true;
            }
        }
        return false;
    }

    public Duration getPlayedDuration() {
        if (musicStartTime == null) {
            return Duration.ZERO;
        }
        Duration startedPlayingDuration = Duration.between(musicStartTime, ZonedDateTime.now());
        if (musicDuration != null && startedPlayingDuration.compareTo(musicDuration) > 0) {
            return musicDuration;
        } else {
            return startedPlayingDuration;
        }
    }

    public boolean isCompleted() {
        if (musicStartTime == null) {
            return true;
        }
        Duration startedPlayingDuration = Duration.between(musicStartTime, ZonedDateTime.now());
        return startedPlayingDuration.compareTo(musicDuration) > 0;
    }

    public PlayerInfo getPusherPlayerInfo() {
        if (currentlyPlayingMusicDetail != null) {
            return PlayerInfoUtil.getPlayerInfoByUUID(currentlyPlayingMusicDetail.getPusherInfo().getPlayerUUID());//Mainly just a Map.get call, no need to cache
        } else {
            return null;
        }
    }

    public MusicDetail getNextToPlayIdleMusicDetail() {
        if (!MusicService.getInstance().getMusicQueue().isEmpty()) {
            QueueItem peek = MusicService.getInstance().getMusicQueue().peek();
            if (peek == null) {
                return MusicDetail.NONE;
            }
            return peek.musicDetail();
        } else {
            return nextToPlayIdleMusicDetail;
        }
    }

    public void stop() {
        lyricUpdaterGeneration.incrementAndGet();
        if (lyricUpdaterVThread != null) {
            lyricUpdaterVThread.interrupt();
        }
        lyricUpdaterVThread = null;
        currentlyPlayingMusicDetail = MusicDetail.NONE;
        nextToPlayIdleMusicDetail = MusicDetail.NONE;
        musicDuration = null;
        musicStartTime = null;
        lyricLines = null;
        atomicLyricLines.set(null);
        currentLyricLine = null;
        postJmtc(() -> {
            jmtc.setPlayingState(JMTCPlayingState.CLOSED);
            jmtc.updateDisplay();
        });
    }
}