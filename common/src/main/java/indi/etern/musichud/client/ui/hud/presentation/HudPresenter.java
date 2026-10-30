package indi.etern.musichud.client.ui.hud.presentation;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.interfaces.IClientEventService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.hud.renderer.ScrollingLyricLineRenderer;
import indi.etern.musichud.client.ui.screen.HudConfigScreen;
import indi.etern.musichud.client.utils.PlayerInfoUtil;
import indi.etern.musichud.client.utils.ui.Easing;
import indi.etern.musichud.connection.ConnectionStateMachine;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Getter;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class HudPresenter {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final long LYRIC_DELAY_NANOS = 300_000_000L;
    private static final long HUD_FADE_MS = 300;
    private static final DateTimeFormatter LONG_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter SHORT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("mm:ss");
    private static volatile HudPresenter instance;

    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private final BackgroundState backgroundState = BackgroundState.getInstance();
    private final CoverImageStore coverImageStore = CoverImageStore.getInstance();
    @Getter
    private final HudPresentationModel model = new HudPresentationModel();
    private final ConcurrentLinkedQueue<QueuedLyric> lyricQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean lyricWorkerRunning = new AtomicBoolean();
    private final AtomicLong lyricGeneration = new AtomicLong();
    private final AtomicLong backgroundResetGeneration = new AtomicLong();
    private final Object lyricLock = new Object();
    private volatile LyricSink lyricSink;
    private volatile String idleMessage = I18n.get(MusicHud.MOD_ID + ".text.idle");
    private volatile String musicDurationString = "";
    private volatile float hudAlpha = 1f;
    private volatile long hudFadeStartMs;
    private volatile float hudFadeFrom = 1f;
    private volatile float hudFadeTo = 1f;
    private volatile long hudFadeDurationMs;
    private volatile boolean pendingSwitchToEmpty;
    private Logger logger;

    /** Language instance the cached idle message belongs to; a resource reload replaces it. */
    private Object idleMessageLanguage;
    private long playTimeCacheSeconds = Long.MIN_VALUE;
    private long playTimeCacheDurationSeconds = Long.MIN_VALUE;
    private String playTimeCacheMusicDuration;
    private String playTimeCacheText = "";

    private HudPresenter() {
        model.setBackground(backgroundState.getTransition());
        // Playback notifies HUD layout after 500 ms; invalidate covers immediately at the switch.
        nowPlayingInfo.getMusicSwitchListener().add((previous, current) -> coverImageStore.beginRequest());
        nowPlayingInfo.getLyricLineUpdateListener().add((lyricLine) -> {
            String text = lyricLine == null ? "" : lyricLine.getText();
            String translatedText = lyricLine == null ? "" : lyricLine.getTranslatedText();

            long scrollMillis = -1;
            if (lyricLine != null) {
                Duration duration = lyricLine.getDuration();
                if (duration != null) {
                    scrollMillis = duration.toMillis();
                } else {
                    Duration musicDuration = nowPlayingInfo.getMusicDuration();
                    scrollMillis = musicDuration == null
                            ? 0
                            : musicDuration.minus(lyricLine.getStartTime()).toMillis();
                }
                scrollMillis = (long) (scrollMillis * 0.75);
            }

            scheduleLyricLines(
                    new ScrollingLyricLineRenderer.Line(lyricLine, text, Theme.HUD_FADE_COLOR, Theme.HUD_EMPHASIZE_COLOR, scrollMillis),
                    new ScrollingLyricLineRenderer.Line(lyricLine, translatedText, Theme.HUD_FADE_COLOR, Theme.HUD_FADE_COLOR, scrollMillis)
            );
        });
        IClientEventService.getInstance().registerClientPlayerJoin((player) -> {
            if (!ConnectionStateMachine.getConnectStatus().equals(ConnectionStateMachine.ConnectStatus.CONNECTED)) {
                MusicHud.EXECUTOR.execute(() -> {
                    MusicDetail currentlyPlayingMusicDetail = nowPlayingInfo.getCurrentlyPlayingMusicDetail();
                    if (currentlyPlayingMusicDetail == null || currentlyPlayingMusicDetail == MusicDetail.NONE) {
                        reset();
                    }
                    model.setPlayerSkinSupplier(() -> {
                        try {
                            PlayerInfo pusherPlayerInfo = nowPlayingInfo.getPusherPlayerInfo();
                            return PlayerInfoUtil.getPlayerSkinPath(pusherPlayerInfo);
                        } catch (Exception e) {
                            return null;
                        }
                    });
                });
            }
        });
        IClientEventService.getInstance().registerClientPlayerQuit((player) -> MusicHud.EXECUTOR.execute(
                () -> model.setPlayerSkinSupplier(null)
        ));
        StreamAudioPlayer.getInstance().getStatusChangeListener().add(this::updateStatus);
        ConnectionStateMachine.getConnectStatusListeners().add((connectStatus) -> updateStatus(null));
        reset();
    }

    public static HudPresenter getInstance() {
        if (instance == null) {
            synchronized (HudPresenter.class) {
                if (instance == null) {
                    instance = new HudPresenter();
                }
            }
        }
        return instance;
    }

    @Nullable
    public static HudPresenter getActiveInstance() {
        return instance;
    }

    public void setLyricSink(@Nullable LyricSink lyricSink) {
        this.lyricSink = lyricSink;
    }

    public void updateStatus(@Nullable StreamAudioPlayer.Status status) {
        if (status != null) {
            model.setAudioStatus(status);
        }
        model.setStatusRevision(model.getStatusRevision() + 1);
    }

    public void switchMusic(MusicDetail musicDetail) {
        MusicDetail actual = nowPlayingInfo.getCurrentlyPlayingMusicDetail();
        boolean requestedIdle = musicDetail == null || musicDetail.equals(MusicDetail.NONE);
        boolean actualIdle = actual == null || actual.equals(MusicDetail.NONE);
        if (requestedIdle ? !actualIdle : !musicDetail.equals(actual)) return;
        long coverToken = coverImageStore.beginRequest();
        try {
            boolean wasIdle = model.isIdle();
            boolean nowIdle = musicDetail == null || musicDetail.equals(MusicDetail.NONE);
            model.setIdle(nowIdle);
            boolean autoHide = clientConfig.getHideHudWhenNotPlaying();
            if (nowIdle) {
                if (autoHide && !wasIdle) {
                    scheduleBackgroundResetAfterFade();
                    pendingSwitchToEmpty = true;
                } else {
                    reset();
                }
            } else {
                backgroundResetGeneration.incrementAndGet();
                model.setTitle(musicDetail.getName());
                String artists = musicDetail.getArtists().stream()
                        .map(Artist::getName)
                        .reduce((a, b) -> a + " / " + b)
                        .orElse("");
                model.setArtistsAndAlbum(artists + " - " + musicDetail.getAlbum().getName());
                coverImageStore.load(musicDetail, coverToken, this::isCurrentCoverTrack,
                        this::updateMusicDuration);
                pendingSwitchToEmpty = false;
            }
        } catch (Exception e) {
            logger().error("While switching music", e);
        }
    }

    public void reset() {
        backgroundResetGeneration.incrementAndGet();
        resetBackgroundNow();
        model.setIdle(true);
        musicDurationString = "";
        idleMessage = I18n.get(MusicHud.MOD_ID + ".text.idle");
        boolean autoHide = clientConfig.getHideHudWhenNotPlaying();
        if (autoHide) {
            model.setTitle("");
        } else if (!idleMessage.equals(MusicHud.MOD_ID + ".text.idle")) {
            model.setTitle(idleMessage);
        }
        model.setArtistsAndAlbum("");
        model.setPlayTime("");
        invalidateLyrics();
        pendingSwitchToEmpty = false;
        hudFadeDurationMs = 0;
        hudAlpha = targetAlpha();
        hudFadeTo = hudAlpha;
        model.setHudAlpha(hudAlpha);
    }

    private void scheduleBackgroundResetAfterFade() {
        long generation = backgroundResetGeneration.incrementAndGet();
        MusicHud.EXECUTOR.execute(() -> {
            try {
                Thread.sleep(HUD_FADE_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            if (backgroundResetGeneration.get() == generation) {
                resetBackgroundNow();
            }
        });
    }

    private void resetBackgroundNow() {
        coverImageStore.invalidate();
        backgroundState.reset();
    }

    public void invalidateLyrics() {
        synchronized (lyricLock) {
            lyricGeneration.incrementAndGet();
            lyricQueue.clear();
            LyricSink sink = lyricSink;
            if (sink != null) {
                sink.clear();
            }
        }
    }

    public void setThumbnailSize(int thumbnailSize) {
        coverImageStore.setThumbnailSize(
                thumbnailSize, this::isCurrentCoverTrack, this::updateMusicDuration);
    }

    public void updateFrame() {
        updateHudFade();
        model.setHudAlpha(hudAlpha);
        if (!clientConfig.getHideHudWhenNotPlaying()) {
            refreshIdleMessage();
        }

        Duration playedDuration = nowPlayingInfo.getPlayedDuration();
        Duration musicDuration = nowPlayingInfo.getMusicDuration();
        model.setPlayedDuration(playedDuration);
        model.setMusicDuration(musicDuration);
        model.setProgressRate(nowPlayingInfo.getProgressRate());
        model.setPlayTime(formatPlayTime(playedDuration, musicDuration));
    }

    /**
     * Cached translation lookup: the idle message only changes with the language instance, so the
     * per-frame path is a single reference comparison instead of an I18n lookup plus a string
     * comparison.
     */
    private void refreshIdleMessage() {
        String title = model.getTitle();
        if (title != null && !title.isBlank()) {
            return;
        }
        Object language = Language.getInstance();
        String message = idleMessage;
        if (language != idleMessageLanguage || message == null) {
            idleMessageLanguage = language;
            message = I18n.get(MusicHud.MOD_ID + ".text.idle");
            idleMessage = message;
        }
        // Only show the message when the key is actually translated.
        if (!message.equals(MusicHud.MOD_ID + ".text.idle")) {
            model.setTitle(message);
        }
    }

    /**
     * The HUD renders at frame rate but the play time string only changes once per second, so the
     * formatting result is memoized on (played seconds, duration seconds, duration text).
     */
    private String formatPlayTime(@Nullable Duration playedDuration, @Nullable Duration musicDuration) {
        if (playedDuration == null || musicDuration == null || musicDuration.isZero()) {
            playTimeCacheSeconds = Long.MIN_VALUE;
            playTimeCacheText = "";
            return "";
        }
        long seconds = playedDuration.toSeconds();
        long durationSeconds = musicDuration.toSeconds();
        String total = musicDurationString;
        if (seconds == playTimeCacheSeconds
                && durationSeconds == playTimeCacheDurationSeconds
                && Objects.equals(total, playTimeCacheMusicDuration)) {
            return playTimeCacheText;
        }
        DateTimeFormatter formatter = musicDuration.toHoursPart() >= 1
                ? LONG_DATE_TIME_FORMATTER
                : SHORT_DATE_TIME_FORMATTER;
        String playTime = formatter.format(LocalTime.MIDNIGHT.plusSeconds(seconds)) + " / " + total;
        playTimeCacheSeconds = seconds;
        playTimeCacheDurationSeconds = durationSeconds;
        playTimeCacheMusicDuration = total;
        playTimeCacheText = playTime;
        return playTime;
    }

    private void scheduleLyricLines(ScrollingLyricLineRenderer.Line line1, ScrollingLyricLineRenderer.Line line2) {
        lyricQueue.add(new QueuedLyric(line1, line2, System.nanoTime(), lyricGeneration.get()));
        if (lyricWorkerRunning.compareAndSet(false, true)) {
            MusicHud.EXECUTOR.execute(this::drainLyricQueue);
        }
    }

    private void drainLyricQueue() {
        try {
            QueuedLyric queued;
            while ((queued = lyricQueue.poll()) != null) {
                long remaining = queued.enqueueNanos() + LYRIC_DELAY_NANOS - System.nanoTime();
                if (remaining > 0) {
                    try {
                        //noinspection BusyWait
                        Thread.sleep(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
                    } catch (InterruptedException ignored) {
                    }
                }
                synchronized (lyricLock) {
                    if (queued.generation() != lyricGeneration.get()) {
                        continue;
                    }
                    LyricSink sink = lyricSink;
                    if (sink != null) {
                        sink.submit(queued.line1(), queued.line2());
                    }
                }
            }
        } finally {
            lyricWorkerRunning.set(false);
            if (!lyricQueue.isEmpty() && lyricWorkerRunning.compareAndSet(false, true)) {
                MusicHud.EXECUTOR.execute(this::drainLyricQueue);
            }
        }
    }

    private void transitiveHudAlphaTo(float target) {
        hudFadeFrom = hudAlpha;
        hudFadeTo = target;
        hudFadeDurationMs = HUD_FADE_MS;
        hudFadeStartMs = System.currentTimeMillis();
    }

    private float targetAlpha() {
        if (!clientConfig.getEnable() || !clientConfig.getEnableHud()) {
            return 0f;
        }
        if (HudConfigScreen.isVisible()) {
            return 1f;
        }
        if (!clientConfig.getHideHudWhenNotPlaying()) {
            return 1f;
        }
        return model.isIdle() ? 0f : 1f;
    }

    private void updateHudFade() {
        float target = targetAlpha();
        if (target != hudFadeTo) {
            transitiveHudAlphaTo(target);
        }
        if (hudFadeDurationMs > 0) {
            long now = System.currentTimeMillis();
            float t = Math.clamp((float) (now - hudFadeStartMs) / hudFadeDurationMs, 0f, 1f);
            float eased = Easing.EASE_IN_OUT_SINE.getInterpolation(t);
            hudAlpha = hudFadeFrom + (hudFadeTo - hudFadeFrom) * eased;
            if (t >= 1f) {
                hudAlpha = hudFadeTo;
                hudFadeDurationMs = 0;
                if (pendingSwitchToEmpty && hudFadeTo <= 1e-4f) {
                    pendingSwitchToEmpty = false;
                    reset();
                }
            }
        } else {
            hudAlpha = hudFadeTo;
        }
    }

    private boolean isCurrentCoverTrack(MusicDetail musicDetail) {
        return !model.isIdle() && musicDetail.equals(nowPlayingInfo.getCurrentlyPlayingMusicDetail());
    }

    private void updateMusicDuration(Duration duration) {
        if (duration == null) {
            return;
        }
        DateTimeFormatter formatter = duration.toHoursPart() >= 1
                ? LONG_DATE_TIME_FORMATTER : SHORT_DATE_TIME_FORMATTER;
        musicDurationString = formatter.format(LocalTime.MIDNIGHT.plusSeconds(duration.toSeconds()));
    }

    private Logger logger() {
        if (logger == null) {
            logger = MusicHud.getLogger(HudPresenter.class);
        }
        return logger;
    }

    public interface LyricSink {
        void submit(ScrollingLyricLineRenderer.Line line1, ScrollingLyricLineRenderer.Line line2);

        void clear();
    }

    private record QueuedLyric(ScrollingLyricLineRenderer.Line line1,
                               ScrollingLyricLineRenderer.Line line2,
                               long enqueueNanos,
                               long generation) {
    }
}
