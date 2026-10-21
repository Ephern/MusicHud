package indi.etern.musichud.client.ui.hud.presentation;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.interfaces.IClientEventService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundImages;
import indi.etern.musichud.client.ui.hud.renderer.ScrollingLyricLineRenderer;
import indi.etern.musichud.client.ui.screen.HudConfigScreen;
import indi.etern.musichud.client.utils.PlayerInfoUtil;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.ColorExtractor;
import indi.etern.musichud.client.utils.ui.Easing;
import indi.etern.musichud.client.utils.ui.Transitionable;
import indi.etern.musichud.connection.ConnectionStateMachine;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Getter;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class HudPresentationService {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final long LYRIC_DELAY_NANOS = 300_000_000L;
    private static final int DOWN_SCALE_TARGET_LENGTH = (int) Math.sqrt(ColorExtractor.MAX_SAMPLES);
    private static final long HUD_FADE_MS = 300;
    private static final DateTimeFormatter LONG_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter SHORT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("mm:ss");
    private static volatile HudPresentationService instance;

    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    @Getter
    private final HudPresentationModel model = new HudPresentationModel();
    @Getter
    private final Transitionable<BackgroundData> backgroundTransition =
            new Transitionable<>(BackgroundData.NONE, 500);
    private final ConcurrentLinkedQueue<QueuedLyric> lyricQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean lyricWorkerRunning = new AtomicBoolean();
    private final AtomicLong lyricGeneration = new AtomicLong();
    private final Object lyricLock = new Object();
    private volatile LyricSink lyricSink;
    private volatile int albumImageThumbnailSize = -1;
    private volatile String idleMessage = I18n.get(MusicHud.MOD_ID + ".text.idle");
    private volatile String musicDurationString = "";
    private volatile float hudAlpha = 1f;
    private volatile long hudFadeStartMs;
    private volatile float hudFadeFrom = 1f;
    private volatile float hudFadeTo = 1f;
    private volatile long hudFadeDurationMs;
    private volatile boolean pendingSwitchToEmpty;
    private Logger logger;

    private HudPresentationService() {
        model.setBackground(backgroundTransition);
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

    public static HudPresentationService getInstance() {
        if (instance == null) {
            synchronized (HudPresentationService.class) {
                if (instance == null) {
                    instance = new HudPresentationService();
                }
            }
        }
        return instance;
    }

    @Nullable
    public static HudPresentationService getActiveInstance() {
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
        try {
            boolean wasIdle = model.isIdle();
            boolean nowIdle = musicDetail == null || musicDetail.equals(MusicDetail.NONE);
            model.setIdle(nowIdle);
            boolean autoHide = clientConfig.getHideHudWhenNotPlaying();
            if (nowIdle) {
                if (autoHide && !wasIdle) {
                    pendingSwitchToEmpty = true;
                } else {
                    reset();
                }
            } else {
                model.setTitle(musicDetail.getName());
                String artists = musicDetail.getArtists().stream()
                        .map(Artist::getName)
                        .reduce((a, b) -> a + " / " + b)
                        .orElse("");
                model.setArtistsAndAlbum(artists + " - " + musicDetail.getAlbum().getName());
                loadAndSwitchAlbumImageWithRetry(musicDetail);
                pendingSwitchToEmpty = false;
            }
        } catch (Exception e) {
            logger().error("While switching music", e);
        }
    }

    public void reset() {
        idleMessage = I18n.get(MusicHud.MOD_ID + ".text.idle");
        boolean autoHide = clientConfig.getHideHudWhenNotPlaying();
        if (autoHide) {
            model.setTitle("");
        } else if (!idleMessage.equals(MusicHud.MOD_ID + ".text.idle")) {
            model.setTitle(idleMessage);
        }
        model.setIdle(true);
        model.setArtistsAndAlbum("");
        model.setPlayTime("");
        musicDurationString = "";
        invalidateLyrics();
        backgroundTransition.startTransition(BackgroundData.NONE);
        pendingSwitchToEmpty = false;
        hudFadeDurationMs = 0;
        hudAlpha = targetAlpha();
        hudFadeTo = hudAlpha;
        model.setHudAlpha(hudAlpha);
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

    public void preloadAlbumImage(Album album) {
        int size = albumImageThumbnailSize;
        if (size <= 0) {
            return;
        }
        ImageUtils.downloadScaledAsync(album.getPicUrl(), size, size);
    }

    public void setThumbnailSize(int thumbnailSize) {
        if (thumbnailSize <= 0 || albumImageThumbnailSize == thumbnailSize) {
            return;
        }
        albumImageThumbnailSize = thumbnailSize;
        MusicDetail currentlyPlayingMusicDetail = nowPlayingInfo.getCurrentlyPlayingMusicDetail();
        if (currentlyPlayingMusicDetail != null && currentlyPlayingMusicDetail != MusicDetail.NONE) {
            loadAndSwitchAlbumImageWithRetry(currentlyPlayingMusicDetail);
        }
    }

    public void updateFrame() {
        updateHudFade();
        model.setHudAlpha(hudAlpha);

        if (!clientConfig.getHideHudWhenNotPlaying()) {
            String title = model.getTitle();
            if (title == null || title.isBlank()) {
                idleMessage = I18n.get(MusicHud.MOD_ID + ".text.idle");
                if (!idleMessage.equals(MusicHud.MOD_ID + ".text.idle")) {
                    model.setTitle(idleMessage);
                }
            }
        }

        backgroundTransition.updateTransition();

        Duration playedDuration = nowPlayingInfo.getPlayedDuration();
        Duration musicDuration = nowPlayingInfo.getMusicDuration();
        model.setPlayedDuration(playedDuration);
        model.setMusicDuration(musicDuration);
        model.setProgressRate(nowPlayingInfo.getProgressRate());
        if (playedDuration != null && musicDuration != null && !musicDuration.isZero()) {
            DateTimeFormatter formatter = musicDuration.toHoursPart() >= 1
                    ? LONG_DATE_TIME_FORMATTER
                    : SHORT_DATE_TIME_FORMATTER;
            String playTime = formatter.format(LocalTime.MIDNIGHT.plusSeconds(playedDuration.toSeconds()))
                    + " / " + musicDurationString;
            model.setPlayTime(playTime);
        } else {
            model.setPlayTime("");
        }
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

    private void loadAndSwitchAlbumImageWithRetry(MusicDetail musicDetail) {
        CompletableFuture<Void> future = loadAlbumImage(musicDetail);
        future.exceptionallyAsync(e -> {
            backgroundTransition.startTransition(BackgroundData.NONE);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
            loadAndSwitchAlbumImageWithRetry(musicDetail);
            return null;
        }, MusicHud.EXECUTOR);
        future.thenRun(() -> {
            Duration musicDuration = nowPlayingInfo.getMusicDuration();
            if (musicDuration == null) {
                return;
            }
            DateTimeFormatter formatter = musicDuration.toHoursPart() >= 1
                    ? LONG_DATE_TIME_FORMATTER
                    : SHORT_DATE_TIME_FORMATTER;
            musicDurationString = formatter.format(LocalTime.MIDNIGHT.plusSeconds(musicDuration.toSeconds()));
        });
    }

    private CompletableFuture<Void> loadAlbumImage(MusicDetail musicDetail) {
        if (albumImageThumbnailSize <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        Album album = musicDetail.getAlbum();
        return ImageUtils.downloadScaledAsync(album.getPicUrl(), albumImageThumbnailSize, albumImageThumbnailSize)
                .thenAcceptBoth(
                        ImageUtils.downloadScaledAsync(album.getPicUrl(), DOWN_SCALE_TARGET_LENGTH, DOWN_SCALE_TARGET_LENGTH),
                        (imageTextureData, downscaled) -> {
                            if (musicDetail.equals(nowPlayingInfo.getCurrentlyPlayingMusicDetail())) {
                                BackgroundImages backgroundImages = new BackgroundImages(imageTextureData, 1f);
                                backgroundTransition.startTransition(
                                        new BackgroundData(backgroundImages, ColorExtractor.extractColors(downscaled.getTexture())));
                            }
                        });
    }

    private Logger logger() {
        if (logger == null) {
            logger = MusicHud.getLogger(HudPresentationService.class);
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
