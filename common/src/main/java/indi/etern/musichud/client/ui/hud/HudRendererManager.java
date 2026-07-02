package indi.etern.musichud.client.ui.hud;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.hud.metadata.*;
import indi.etern.musichud.client.ui.hud.renderer.*;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.IClientEventService;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public class HudRendererManager {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static volatile HudRendererManager instance;
    @Getter
    private static volatile boolean loaded = false;
    private final BackgroundRenderer BACKGROUND_RENDERER = BackgroundRenderer.getInstance();
    private final AlbumImageRenderer IMAGE_RENDERER = AlbumImageRenderer.getInstance();
    private final PlayerHeadRenderer PLAYER_HEAD_RENDERER = new PlayerHeadRenderer();
    private final PlayingStatusRenderer PLAYING_STATUS_RENDERER = PlayingStatusRenderer.getInstance();
    private final ProgressRenderer PROGRESS_RENDERER = ProgressRenderer.getInstance();
    private final TextRenderer TITLE_RENDERER = new TextRenderer();
    private final TextRenderer ARTISTS_AND_ALBUM_RENDERER = new TextRenderer();
    private final TextRenderer PLAY_TIME_RENDERER = new TextRenderer();
    private final ScrollingLyricLineRenderer LYRICS_LINE_RENDERER = new ScrollingLyricLineRenderer();
    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private final DateTimeFormatter LONG_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final DateTimeFormatter SHORT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("mm:ss");
    private final HudRenderContext hudRenderContext = new HudRenderContext();
    private volatile HudRenderData hudBaseData;
    private volatile HudRenderData imageDisplayData;
    @Setter
    private volatile Layout baseLayout;
    private float contentInterval;
    private String musicDurationString = "";
    private Logger logger;

    protected HudRendererManager() {
        nowPlayingInfo.getLyricLineUpdateListener().add((lyricLine) -> {
            MusicHud.EXECUTOR.execute(() -> {
                String text = lyricLine == null ? "" : lyricLine.getText();
                String translatedText = lyricLine == null ? "" : lyricLine.getTranslatedText();

                long scrollMillis = -1;
                if (lyricLine != null) {
                    Duration duration = lyricLine.getDuration();
                    if (duration != null) {
                        scrollMillis = duration.toMillis();
                    } else {
                        scrollMillis = nowPlayingInfo.getMusicDuration().minus(lyricLine.getStartTime()).toMillis();
                    }
                    scrollMillis = (long) (scrollMillis * 0.8);
                }

                ScrollingLyricLineRenderer.Line style1 = new ScrollingLyricLineRenderer.Line(lyricLine, text, Theme.HUD_FADE_COLOR, Theme.HUD_EMPHASIZE_COLOR, scrollMillis);
                ScrollingLyricLineRenderer.Line style2 = new ScrollingLyricLineRenderer.Line(lyricLine, translatedText, Theme.HUD_FADE_COLOR, Theme.HUD_FADE_COLOR, scrollMillis);

                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                }

                LYRICS_LINE_RENDERER.setLines(style1, style2, 300);
            });
        });
    }

    public static HudRendererManager getInstance() {
        if (instance == null) {
            synchronized (HudRendererManager.class) {
                if (instance == null) {
                    instance = new HudRendererManager();
                    instance.updateLayoutFromConfig();
                    instance.refreshStyle();

                    updateStatus(StreamAudioPlayer.Status.IDLE);
                    StreamAudioPlayer.getInstance().getStatusChangeListener().add(HudRendererManager::updateStatus);
                    MusicHud.getConnectStatusListeners().add((connectStatus) -> HudRendererManager.updateStatus(null));
                    loaded = true;
                }
            }
        }
        return instance;
    }

    private static void updateStatus(@Nullable StreamAudioPlayer.Status status) {
        if (instance != null) {
            instance.PLAYING_STATUS_RENDERER.updateStatus(status);
        }
    }

    public void updateLayoutFromConfig() {
        try {
            Layout layout = new Layout(
                    "Base",
                    clientConfig.getHudOffsetX(),
                    clientConfig.getHudOffsetY(),
                    clientConfig.getHudWidth(),
                    clientConfig.getHudHeight(),
                    clientConfig.getHudCornerRadius(),
                    clientConfig.getHudHorizontalPosition(),
                    clientConfig.getHudVerticalPosition()
            );
            setBaseLayout(layout);
            IClientEventService.getInstance().registerClientPlayerJoin((player) -> {
                MusicHud.EXECUTOR.execute(() -> {
                    MusicDetail currentlyPlayingMusicDetail = NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail();
                    if (currentlyPlayingMusicDetail == null || currentlyPlayingMusicDetail == MusicDetail.NONE) {
                        reset();
                    }
                });
            });
        } catch (Exception e) {
            if (logger == null) {
                logger = MusicHud.getLogger(HudRendererManager.class);
            }
            logger.error("While configure HUD layout from config", e);
        }
    }

    public void refreshStyle() {
        try {
            float height = baseLayout.getHeight();
            float halfHeight = height / 2;
            if (baseLayout.getRadius() > halfHeight) {
                baseLayout.setRadius(halfHeight);
            }

            configureBaseRenderer(baseLayout);

            Layout baseLayout = hudBaseData.getLayout();
            float contentPadding = Math.max(height / 10, 3);

            float imageHeightAndWidth = height - 2 * contentPadding;
            float imageRadius = Math.clamp(baseLayout.getRadius() - contentPadding, 0, imageHeightAndWidth / 2f);
            Layout imageLayout = new Layout("Album", contentPadding, contentPadding, imageHeightAndWidth, imageHeightAndWidth, imageRadius);
            imageLayout.setParent(baseLayout);

            configureImageRenderer(imageLayout);

            float contentHeight = height - contentPadding * 2;
            float contentWidth = baseLayout.getWidth() - imageHeightAndWidth - 3 * contentPadding - baseLayout.getRadius() / 3;
            float contentUnit = Math.max(contentHeight / 32f, 1);
            float titleSize = contentUnit * 7;
            boolean showProgress = contentHeight > 14f;

            float progressHeight = showProgress ? contentUnit * 2 : 0;
            float mainContentX = contentPadding + imageHeightAndWidth + contentPadding;
            float progressY = contentPadding + imageHeightAndWidth - progressHeight - 1;
            float progressRadius = progressHeight / 2;
            Layout progressLayout = new Layout("Progress", mainContentX, progressY, contentWidth, progressHeight, progressRadius);
            progressLayout.setParent(baseLayout);

            configureProgressRenderer(progressLayout);

            contentInterval = Math.min(contentUnit * 2.5f, 2f);

            float maxTitleWidth = contentWidth - titleSize - Math.max(4, contentInterval);

            float titleY = showProgress ? contentPadding + 1f : contentPadding + (contentHeight - titleSize) / 2;
            float statusX = Math.max(mainContentX + contentWidth - titleSize, imageHeightAndWidth + contentPadding - titleSize);
            boolean statusVisible = !(maxTitleWidth - 1.25 * titleSize <= 0);
            float headX = statusX - (statusVisible ? titleSize + Math.max(4, contentInterval): 0);

            boolean showInfoLine = contentHeight - titleSize > 11f;
            float infoTextSize = showInfoLine ? contentUnit * 5.5f : 0;

            boolean showLyrics = contentHeight - titleSize - infoTextSize > 14f;
            boolean showSubLyrics = contentHeight - titleSize - infoTextSize > 20f;
            float lyricsSize = showLyrics ? showSubLyrics ? contentUnit * 6 : contentUnit * 7 : 0;
            float subLyricsSize = showSubLyrics ? contentUnit * 5 : 0;

            float lyricsY = contentPadding + titleSize + contentInterval;
            float aboveProgressY = progressY - infoTextSize - contentInterval;
            float progressRightX = mainContentX + contentWidth;

            Layout statusLayout = new Layout("Status", statusX, titleY, titleSize, titleSize, 0f);
            statusLayout.setParent(baseLayout);
            PLAYING_STATUS_RENDERER.configure(statusLayout);
            PLAYING_STATUS_RENDERER.setVisibility(statusVisible);

            Layout layout1 = new Layout("PlayerHead", headX, titleY, titleSize, titleSize, 0f);
            layout1.setParent(baseLayout);
            PLAYER_HEAD_RENDERER.configure(layout1);

            Layout titleLayout = Layout.ofTextLayout("Title", mainContentX, titleY, maxTitleWidth, titleSize);
            titleLayout.setParent(baseLayout);
            TITLE_RENDERER.configure(titleLayout, Theme.EMPHASIZE_TEXT_COLOR, TextRenderer.Position.LEFT);

            float lyricHeight = contentHeight - titleSize - progressHeight - infoTextSize - contentInterval * 2;
            Layout layout = new Layout("MainContent", mainContentX, lyricsY, contentWidth, lyricHeight, 0);
            layout.setParent(baseLayout);
            LYRICS_LINE_RENDERER.setLayout(layout);
            LYRICS_LINE_RENDERER.setLine1Height(lyricsSize);
            LYRICS_LINE_RENDERER.setLine2Height(subLyricsSize);
            LYRICS_LINE_RENDERER.setLineSpacing((int) contentInterval);

            Layout artistAndAlbumLayout = Layout.ofTextLayout("InfoText", mainContentX, aboveProgressY, contentWidth, infoTextSize);
            artistAndAlbumLayout.setParent(baseLayout);
            Layout playTimeLayout = Layout.ofTextLayout("PlayTimeText", progressRightX, aboveProgressY, contentWidth, infoTextSize);
            playTimeLayout.setParent(baseLayout);
            ARTISTS_AND_ALBUM_RENDERER.configure(artistAndAlbumLayout, Theme.HUD_FADE_COLOR, TextRenderer.Position.LEFT);
            PLAY_TIME_RENDERER.configure(playTimeLayout, Theme.HUD_FADE_COLOR, TextRenderer.Position.RIGHT);
        } catch (Exception e) {
            if (logger == null) {
                logger = MusicHud.getLogger(HudRendererManager.class);
            }
            logger.error("While refresh HUD style", e);
        }
    }

    private void configureProgressRenderer(Layout layout) {
        PROGRESS_RENDERER.setProgressData(new ProgressBarData(
                layout,
                Theme.HUD_PROGRESS_LEFT,
                Theme.HUD_PROGRESS_CURRENT,
                Theme.HUD_PROGRESS_BACKGROUND,
                layout.getHeight() * 6,
                2f,
                0.01f
        ));
    }

    private void configureBaseRenderer(@NotNull Layout layout) {
        BackgroundImages bgImage = getBackgroundImagesOrElse(null);
        if (hudBaseData == null) {
            hudBaseData = new HudRenderData(layout, bgImage);
        } else {
            hudBaseData.setLayout(layout);
        }
        BACKGROUND_RENDERER.configure(hudBaseData);
    }

    private void configureImageRenderer(Layout imageLayout) {
        if (imageDisplayData == null) {
            imageDisplayData = new HudRenderData(imageLayout);
            imageDisplayData.setFallback(hudBaseData);
        } else {
            imageDisplayData.setLayout(imageLayout);
        }
        IMAGE_RENDERER.configure(imageDisplayData);
    }

    private BackgroundImages getBackgroundImagesOrElse(BackgroundImages bgImages) {
        if (hudBaseData != null) {
            BackgroundImages images = hudBaseData.getTransitionableBackground().getCurrent().image();
            if (images != null) {
                bgImages = images;
            }
        }
        return bgImages;
    }

    public void switchMusic(MusicDetail musicDetail) {
        try {
            if (musicDetail == null || musicDetail.equals(MusicDetail.NONE)) {
                reset();
            } else {
                TITLE_RENDERER.setText(musicDetail.getName());
                String artists = musicDetail.getArtists().stream()
                        .map(Artist::getName)
                        .reduce((a, b) -> a + " / " + b)
                        .orElse("");
                ARTISTS_AND_ALBUM_RENDERER.setText(artists + " - " + musicDetail.getAlbum().getName());
                LYRICS_LINE_RENDERER.clear();
                PLAYER_HEAD_RENDERER.setSkinResource(nowPlayingInfo.getPusherSkinResource());
                loadAlbumImage(musicDetail).thenAccept((unused) -> {
                    Duration musicDuration = nowPlayingInfo.getMusicDuration();
                    DateTimeFormatter formatter = musicDuration.toHoursPart() >= 1 ?
                            LONG_DATE_TIME_FORMATTER :
                            SHORT_DATE_TIME_FORMATTER;
                    musicDurationString = formatter.format(LocalTime.MIDNIGHT.plusSeconds(musicDuration.toSeconds()));
                });
            }
        } catch (Exception e) {
            if (logger == null) {
                logger = MusicHud.getLogger(HudRendererManager.class);
            }
            logger.error("While switching music", e);
        }
    }

    private CompletableFuture<Void> loadAlbumImage(MusicDetail musicDetail) {
        return ImageUtils.downloadAsync(musicDetail.getAlbum().getThumbnailPicUrl(240))
                .thenAccept(imageTextureData -> {
                    if (musicDetail.equals(nowPlayingInfo.getCurrentlyPlayingMusicDetail())) {
                        BackgroundImages backgroundImages = new BackgroundImages(imageTextureData, 1f);
                        var nextData = new BackgroundData(backgroundImages);
                        hudBaseData.getTransitionableBackground().startTransition(nextData);
                    }
                }).exceptionallyAsync(e -> {
                    var nextData = BackgroundData.NONE;
                    hudBaseData.getTransitionableBackground().startTransition(nextData);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {}
                    loadAlbumImage(musicDetail);
                    return null;
                }, MusicHud.EXECUTOR);
    }

    public void reset() {
        TITLE_RENDERER.setText(I18n.get(MusicHud.MOD_ID + ".text.idle"));
        ARTISTS_AND_ALBUM_RENDERER.setText("");
        LYRICS_LINE_RENDERER.clear();
        PLAY_TIME_RENDERER.setText("");
        PLAYER_HEAD_RENDERER.setSkinResource(null);
        musicDurationString = "";
        var nextData = BackgroundData.NONE;
        hudBaseData.getTransitionableBackground().startTransition(nextData);
    }

    public void renderFrame(GuiGraphics graphics, DeltaTracker deltaTracker) {
        try {
            if (!clientConfig.getEnable() || !clientConfig.getEnableHud()) {
                return;
            }
            NowPlayingInfo nowPlayingInfo = this.nowPlayingInfo;
            MusicDetail musicDetail = nowPlayingInfo.getCurrentlyPlayingMusicDetail();
            if ((musicDetail == null || musicDetail.equals(MusicDetail.NONE)) &&
                    clientConfig.getHideHudWhenNotPlaying()) {
                return;
            }
            hudBaseData.getTransitionableBackground().updateTransition();

            Duration playedDuration = nowPlayingInfo.getPlayedDuration();
            Duration musicDuration = nowPlayingInfo.getMusicDuration();
            if (playedDuration != null && musicDuration != null && !musicDuration.isZero()) {
                DateTimeFormatter formatter = musicDuration.toHoursPart() >= 1 ?
                        LONG_DATE_TIME_FORMATTER :
                        SHORT_DATE_TIME_FORMATTER;
                String playTimeString = formatter.format(
                        LocalTime.MIDNIGHT.plusSeconds(playedDuration.toSeconds())
                ) + " / " + musicDurationString;
                PLAY_TIME_RENDERER.setText(playTimeString);
            }

            hudRenderContext.clearContext();
            hudRenderContext.setGraphics(graphics);

            PLAYER_HEAD_RENDERER.setSkinResource(nowPlayingInfo.getPusherSkinResource());

            BACKGROUND_RENDERER.render(hudRenderContext);

            IMAGE_RENDERER.render(hudRenderContext);
            PLAYER_HEAD_RENDERER.render(hudRenderContext);
            PLAYING_STATUS_RENDERER.render(hudRenderContext);
            PROGRESS_RENDERER.render(hudRenderContext);

            float progressWidth = PROGRESS_RENDERER.getProgressData().getLayout().getWidth();
            Layout titleLayout = TITLE_RENDERER.getLayout();
            float maxTitleWidth = progressWidth - PLAYER_HEAD_RENDERER.getLayout().getWidth() - Math.max(4, contentInterval);
            if (PLAYING_STATUS_RENDERER.isVisible()) {
                titleLayout.setWidth(maxTitleWidth - Math.max(4, contentInterval) - PLAYING_STATUS_RENDERER.getLayout().getWidth());
            } else {
                titleLayout.setWidth(maxTitleWidth);
            }

            TITLE_RENDERER.render(hudRenderContext);
            LYRICS_LINE_RENDERER.render(hudRenderContext);

            ARTISTS_AND_ALBUM_RENDERER.getLayout().setWidth(progressWidth - PLAY_TIME_RENDERER.calcDisplayWidth() - contentInterval);
            ARTISTS_AND_ALBUM_RENDERER.render(hudRenderContext);
            PLAY_TIME_RENDERER.render(hudRenderContext);

            hudRenderContext.prepareUniforms();
        } catch (Exception e) {
            if (logger == null) {
                logger = MusicHud.getLogger(HudRendererManager.class);
            }
            logger.error("While rendering HUD frame", e);
        }
    }
}
