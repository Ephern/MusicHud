package indi.etern.musichud.client.audio;

import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.LyricInfo;
import indi.etern.musichud.beans.music.LyricLine;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.hud.HudRendererManager;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.ui.utils.lyrics.FullLineLyricParser;
import indi.etern.musichud.client.ui.utils.lyrics.WordByWordLyricParser;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.apache.logging.log4j.Logger;

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
    @Setter
    @Getter
    private Duration updateInAdvanceDuration = Duration.of(500, ChronoUnit.MILLIS);
    @Getter
    private final Set<BiConsumer<MusicDetail, MusicDetail>> musicSwitchListener = new HashSet<>();
    @Getter
    private MusicDetail currentlyPlayingMusicDetail;
    private MusicDetail nextToPlayIdleMusicDetail;
    @Getter
    private volatile Duration musicDuration = null;
    @Getter
    private volatile ZonedDateTime musicStartTime = null;
    @Getter
    private ArrayDeque<LyricLine> lyricLines;
    private final AtomicReference<ArrayDeque<LyricLine>> atomicLyricLines = new AtomicReference<>();
    @Getter
    private LyricLine currentLyricLine;
    private Thread lyricUpdaterVThread;

    final Runnable lyricUpdater = () -> {
        Thread thread = Thread.currentThread();
        lyricUpdaterVThread = thread;
        thread.setName("MH-Lyrics-Updater");
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

    private Duration getCallTime(LyricLine line) {
        return line.getStartTime().minus(updateInAdvanceDuration);
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
        } catch (IllegalStateException ignored) {}
        HudRendererManager.getInstance().switchMusic(musicDetail);
        List.copyOf(musicSwitchListener).forEach(consumer -> {
            consumer.accept(previous, musicDetail);
        });
        callLyricsUpdateListeners(null);
    }

    public void startAt(ZonedDateTime zonedDateTime) {
        musicStartTime = Objects.requireNonNullElseGet(zonedDateTime, ZonedDateTime::now);
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
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            throw new IllegalStateException();
        }
        if (currentlyPlayingMusicDetail != null) {
            return connection.getPlayerInfo(currentlyPlayingMusicDetail.getPusherInfo().getPlayerUUID());
        } else {
            return null;
        }
    }

    public MusicDetail getNextToPlayIdleMusicDetail() {
        if (!MusicService.getInstance().getMusicQueue().isEmpty()) {
            return MusicService.getInstance().getMusicQueue().peek();
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