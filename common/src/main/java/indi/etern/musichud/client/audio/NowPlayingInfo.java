package indi.etern.musichud.client.audio;

import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.LyricInfo;
import indi.etern.musichud.client.ui.dto.LyricLine;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.services.music.MusicService;
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
    @Setter
    @Getter
    private Duration updateInAdvanceDuration = Duration.of(500, ChronoUnit.MILLIS);
    private MusicDetail smtcPlayingMusicDetail = null;
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
    private Thread lyricUpdaterVThread;

    final Runnable lyricUpdater = () -> {
        Thread thread = Thread.currentThread();
        lyricUpdaterVThread = thread;
        thread.setName("MHWorker-Lyrics-Updater");
        while (true) {
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
        lyricUpdaterVThread = null;
    };

    private NowPlayingInfo() {
        Thread smtcThread = new Thread(this::jmtcLoop, "MH-SMTC");
        smtcThread.setDaemon(true);
        smtcThread.start();
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

    private void jmtcLoop() {
        jmtc = JMTC.getInstance(new JMTCSettings("Minecraft-MusicHUD", "Minecraft-MusicHUD"));
        JMTCCallbacks jmtcCallbacks = new JMTCCallbacks();
        jmtcCallbacks.onPlay = () -> {
            MusicHud.EXECUTOR.execute(() -> {
                clientConfig.setMuted(false);
                clientConfig.save();
            });
            jmtc.setPlayingState(JMTCPlayingState.PLAYING);
            jmtc.updateDisplay();
        };
        jmtcCallbacks.onPause = () -> {
            MusicHud.EXECUTOR.execute(() -> {
                clientConfig.setMuted(true);
                clientConfig.save();
            });
            jmtc.setPlayingState(JMTCPlayingState.PAUSED);
            jmtc.updateDisplay();
        };
        jmtcCallbacks.onNext = () -> {
            MusicHud.EXECUTOR.execute(() -> {
                MusicService.getInstance().voteForSkipCurrent();
            });
        };

        jmtc.setEnabled(true);
        jmtc.setEnabledButtons(new JMTCEnabledButtons(
                true, true, false, true, false
        ));
        jmtc.setCallbacks(jmtcCallbacks);

        // begin in STOPPED state with track info loaded
        jmtc.setPlayingState(JMTCPlayingState.STOPPED);
        jmtc.setMediaType(JMTCMediaType.Music);
        jmtc.setParameters(new JMTCParameters(JMTCParameters.LoopStatus.Track, 1.0, 1.0, false));
        jmtc.updateDisplay();

        while (true) {
            MusicDetail musicDetail = currentlyPlayingMusicDetail == null ? MusicDetail.NONE : currentlyPlayingMusicDetail;
            boolean updateDisplay = false;
            if (musicDetail != smtcPlayingMusicDetail) {
                smtcPlayingMusicDetail = musicDetail;
                String artists = musicDetail.getArtists().stream()
                        .map(Artist::getName)
                        .reduce((a, b) -> a + " / " + b)
                        .orElse("");
                ArrayList<MusicDetail> albumTracks = new ArrayList<>(musicDetail.getAlbum().getMusicDetails());
                long durationMillis = musicDetail.getDurationMillis();
                jmtc.setTimelineProperties(new JMTCTimelineProperties(0L, durationMillis, 0L, durationMillis));
                URI artUri = null;
                String picUrl = musicDetail.getAlbum().getPicUrl();
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
                jmtc.setMediaProperties(new JMTCMusicProperties(
                        smtcPlayingMusicDetail.getName(),
                        artists,
                        musicDetail.getAlbum().getName(),
                        artists,
                        new String[]{""},
                        albumTracks.size(),
                        albumTracks.indexOf(musicDetail),
                        artUri
                ));
                updateDisplay = true;
                jmtc.setPlayingState(JMTCPlayingState.PLAYING);
            }
            if (musicDetail != MusicDetail.NONE) {
                Duration playedDuration = getPlayedDuration();
                long position = playedDuration.toMillis();
                jmtc.setPosition(position);
//                System.out.println("position: " + position);
                if (getPlayedDuration().equals(musicDuration)) {
                    jmtc.setPlayingState(JMTCPlayingState.STOPPED);
                } else if (clientConfig.getMuted()) {
                    jmtc.setPlayingState(JMTCPlayingState.PAUSED);
                } else {
                    jmtc.setPlayingState(JMTCPlayingState.PLAYING);
                }
                updateDisplay = true;
            } else {
                jmtc.setPlayingState(JMTCPlayingState.CLOSED);
            }
            if (updateDisplay) {
                jmtc.updateDisplay();
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }
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
        } else {
            musicDuration = null;
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
        HudRendererManager.getInstance().switchMusic(musicDetail);
        List.copyOf(musicSwitchListener).forEach(consumer -> {
            consumer.accept(previous, musicDetail);
        });
        callLyricsUpdateListeners(null);
    }

    public void startAt(ZonedDateTime zonedDateTime) {
        musicStartTime = Objects.requireNonNullElseGet(zonedDateTime, ZonedDateTime::now);
        // SMTC state change picked up by jmtcLoop polling
        if (lyricLines != null && !lyricLines.isEmpty()) {
            if (lyricUpdaterVThread == null) {
                MusicHud.EXECUTOR.execute(lyricUpdater);
            } else {
                lyricUpdaterVThread.interrupt();
            }
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
            return MusicService.getInstance().getMusicQueue().peek().musicDetail();
        } else {
            return nextToPlayIdleMusicDetail;
        }
    }

    public void stop() {
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
    }
}