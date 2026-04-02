package indi.etern.musichud.client.ui.hud;

import com.mojang.blaze3d.systems.RenderPass;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.hud.metadata.*;
import indi.etern.musichud.client.ui.hud.renderer.*;
import indi.etern.musichud.client.ui.utils.TransitionStatus;
import indi.etern.musichud.client.ui.utils.image.ImageBlurPostProcessor;
import indi.etern.musichud.client.ui.utils.image.ImageTextureData;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.format.DateTimeFormatter;

public class HudRendererManager {
    private static volatile HudRendererManager instance;
    @Getter
    private static volatile boolean loaded = false;
    private final BackgroundRenderer HUD_RENDERER = BackgroundRenderer.getInstance();
    private final AlbumImageRenderer IMAGE_RENDERER = AlbumImageRenderer.getInstance();
    private final PlayerHeadRenderer PLAYER_HEAD_RENDERER = PlayerHeadRenderer.getInstance();
    private final PlayingStatusRenderer PLAYING_STATUS_RENDERER = PlayingStatusRenderer.getInstance();
    private final ProgressRenderer PROGRESS_RENDERER = ProgressRenderer.getInstance();
    private final TextRenderer TITLE_RENDERER = new TextRenderer();
    private final TextRenderer ARTISTS_AND_ALBUM_RENDERER = new TextRenderer();
    private final TextRenderer PLAY_TIME_RENDERER = new TextRenderer();
    private final ScrollingLyricLineRenderer LYRICS_LINE_RENDERER = new ScrollingLyricLineRenderer();
    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private volatile HudRenderData hudBaseData;
    private volatile HudRenderData imageDisplayData;
    private volatile HudRenderData progressDisplayData;
    @Setter
    private volatile Layout baseLayout;
    @Setter
    private volatile BackgroundColor bgColor;
    private float contentInterval;
    private float contentPadding;

    protected HudRendererManager() {
        nowPlayingInfo.getLyricLineUpdateListener().add((lyricLine) -> {
            String text = lyricLine.getText();
            String translatedText = lyricLine.getTranslatedText();

            ScrollingLyricLineRenderer.TextStyle style1 = new ScrollingLyricLineRenderer.TextStyle(text, Theme.NORMAL_TEXT_COLOR);
            ScrollingLyricLineRenderer.TextStyle style2 = new ScrollingLyricLineRenderer.TextStyle(translatedText, Theme.SECONDARY_TEXT_COLOR);

            Duration duration = lyricLine.getDuration();
            long scrollMillis;
            if (duration != null) {
                scrollMillis = duration.toMillis();
            } else {
                scrollMillis = nowPlayingInfo.getMusicDuration().minus(lyricLine.getStartTime()).toMillis();
            }
            scrollMillis = (long) (scrollMillis * 0.8);
            LYRICS_LINE_RENDERER.setLines(style1, scrollMillis, style2, scrollMillis, 300);
        });
    }

    public static HudRendererManager getInstance() {
        if (instance == null) {
            synchronized (HudRendererManager.class) {
                if (instance == null) {
                    instance = new HudRendererManager();
                    BackgroundColor bgColor = new BackgroundColor(
                            0x801A1A1A, 0xFC202020,
                            0XC0202020, 0xC02A2A2A
                    );
                    instance.setBgColor(bgColor);
                    instance.updateLayoutFromConfig();
                    instance.refreshStyle();

                    updateStatus(StreamAudioPlayer.Status.IDLE);
                    StreamAudioPlayer.getInstance().getStatusChangeListener().add(HudRendererManager::updateStatus);
                    loaded = true;
                }
            }
        }
        return instance;
    }

    private static void updateStatus(StreamAudioPlayer.Status status) {
        if (instance != null) {
            instance.PLAYING_STATUS_RENDERER.setStatus(status);
        }
    }

    public void updateLayoutFromConfig() {
        Layout layout = new Layout(
                clientConfig.getHudOffsetX(),
                clientConfig.getHudOffsetY(),
                clientConfig.getHudWidth(),
                clientConfig.getHudHeight(),
                clientConfig.getHudCornerRadius(),
                clientConfig.getHudHorizontalPosition(),
                clientConfig.getHudVerticalPosition()
        );
        setBaseLayout(layout);
    }

    public void refreshStyle() {
        if (baseLayout.radius > baseLayout.height / 2) {
            baseLayout.radius = baseLayout.height / 2;
        }

        BackgroundImage bgImage = getBackgroundImageOrElse(new BackgroundImage(null, null, 1));
        configureBaseRenderer(baseLayout, bgColor, bgImage);

        Layout baseLayout = hudBaseData.getLayout();
        contentPadding = Math.max(baseLayout.height / 10, 3);

        float imageHeightAndWidth = baseLayout.height - 2 * contentPadding;
        float imageRadius = Math.clamp(baseLayout.radius - contentPadding, 0, imageHeightAndWidth / 2f);
        Layout imageLayout = new Layout(contentPadding, contentPadding, imageHeightAndWidth, imageHeightAndWidth, imageRadius);
        imageLayout.setParent(baseLayout);

        configureImageRenderer(imageLayout, bgImage);

        float contentHeight = baseLayout.height - contentPadding * 2;
        float contentUnit = Math.max(contentHeight / 32f, 1);
        float titleSize = contentUnit * 7;
        boolean showProgress = contentHeight > 14f;

        float progressWidth = baseLayout.width - imageHeightAndWidth - 3 * contentPadding - baseLayout.radius / 3;
        float progressHeight = showProgress ? contentUnit * 2 : 0;
        float mainContentX = contentPadding + imageHeightAndWidth + contentPadding;
        float progressY = contentPadding + imageHeightAndWidth - progressHeight - 1;
        float progressRadius = progressHeight / 2;
        Layout progressLayout = new Layout(mainContentX, progressY, progressWidth, progressHeight, progressRadius);
        progressLayout.setParent(baseLayout);

        configureProgressRenderer(progressLayout);

        contentInterval = Math.min(contentUnit * 3, 1.5f);

        float titleY = showProgress ? contentPadding + 1f : contentPadding + (contentHeight - titleSize) / 2;
        float headX = Math.max(mainContentX + progressWidth - titleSize, imageHeightAndWidth + contentPadding - titleSize);
        float statusX = headX - titleSize - contentPadding;

        boolean showInfoLine = contentHeight - titleSize > 11f;
        float infoTextSize = showInfoLine ? contentUnit * 5.5f : 0;

        boolean showLyrics = contentHeight - titleSize - infoTextSize > 14f;
        boolean showSubLyrics = contentHeight - titleSize - infoTextSize > 20f;
        float lyricsSize = showLyrics ? showSubLyrics ? contentUnit * 6 : contentUnit * 7 : 0;
        float subLyricsSize = showSubLyrics ? contentUnit * 5 : 0;

        float lyricsY = contentPadding + titleSize + contentInterval;
        float aboveProgressY = progressY - infoTextSize - contentInterval;
        float progressRightX = mainContentX + progressWidth;

        Layout layout1 = new Layout(headX, titleY, titleSize, titleSize, 0f);
        layout1.setParent(baseLayout);
        PLAYER_HEAD_RENDERER.configureLayout(layout1);

        float maxTitleWidth = progressWidth - titleSize - contentInterval;

        Layout statusLayout = new Layout(statusX, titleY, titleSize, titleSize, 0f);
        statusLayout.setParent(baseLayout);
        PLAYING_STATUS_RENDERER.configureLayout(statusLayout);
        if (maxTitleWidth - 1.25 * titleSize <= 0) {
            PLAYING_STATUS_RENDERER.setVisibility(false);
        }

        Layout titleLayout = Layout.ofTextLayout(mainContentX, titleY, maxTitleWidth, titleSize);
        titleLayout.setParent(baseLayout);
        TITLE_RENDERER.configureLayout(titleLayout, Theme.EMPHASIZE_TEXT_COLOR, TextRenderer.Position.LEFT);

        float lyricHeight = contentHeight - titleSize - progressHeight - infoTextSize - contentInterval * 2;
        Layout layout = new Layout(mainContentX, lyricsY, progressWidth, lyricHeight, 0);
        layout.setParent(baseLayout);
        LYRICS_LINE_RENDERER.setLayout(layout);
        LYRICS_LINE_RENDERER.setLine1Height(lyricsSize);
        LYRICS_LINE_RENDERER.setLine2Height(subLyricsSize);
        LYRICS_LINE_RENDERER.setLineSpacing((int) contentInterval);

        Layout artistAndAlbumLayout = Layout.ofTextLayout(mainContentX, aboveProgressY, progressWidth, infoTextSize);
        artistAndAlbumLayout.setParent(baseLayout);
        Layout playTimeLayout = Layout.ofTextLayout(progressRightX, aboveProgressY, progressWidth, infoTextSize);
        playTimeLayout.setParent(baseLayout);
        ARTISTS_AND_ALBUM_RENDERER.configureLayout(artistAndAlbumLayout, Theme.SECONDARY_TEXT_COLOR, TextRenderer.Position.LEFT);
        PLAY_TIME_RENDERER.configureLayout(playTimeLayout, Theme.SECONDARY_TEXT_COLOR, TextRenderer.Position.RIGHT);


        HudRenderData.getTransitionStatus().setOnCompleteCallback(nextData -> {
            bgImage.currentBlurredLocation = nextData.nextBlurred();
            bgImage.currentUnblurredLocation = nextData.nextUnblurred();
            bgImage.currentAspect = nextData.nextAspect();
        });
    }

    private void configureProgressRenderer(Layout layout) {
        if (progressDisplayData == null) {
            progressDisplayData = new HudRenderData(layout, null, null);
            progressDisplayData.setProgressBar(new ProgressBar(
                    0x00A0A0A0,
                    0x50FFFFFF,
                    0x40A0A0A0,
                    12f,
                    2f,
                    0.01f
            ));
        } else {
            progressDisplayData.setLayout(layout);
        }
        PROGRESS_RENDERER.configure(progressDisplayData);
    }

    private void configureImageRenderer(Layout imageLayout, BackgroundImage bgImage) {
        if (imageDisplayData == null) {
            imageDisplayData = new HudRenderData(imageLayout, null, bgImage);
        } else {
            imageDisplayData.setLayout(imageLayout);
        }
        IMAGE_RENDERER.configure(imageDisplayData);
    }

    private void configureBaseRenderer(@NotNull Layout layout, @NotNull BackgroundColor bgColor, BackgroundImage bgImage) {
        if (hudBaseData == null) {
            hudBaseData = new HudRenderData(layout, bgColor, bgImage);
        } else {
            hudBaseData.setLayout(layout);
            hudBaseData.setBackgroundColor(bgColor);
        }
        HUD_RENDERER.configure(hudBaseData);
    }

    private BackgroundImage getBackgroundImageOrElse(BackgroundImage bgImage) {
        if (hudBaseData != null && hudBaseData.getBackgroundImage() != null) {
            bgImage = hudBaseData.getBackgroundImage();
        }
        return bgImage;
    }

    public void switchMusic(MusicDetail musicDetail) {
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
            PlayerInfo pusherPlayerInfo = this.nowPlayingInfo.getPusherPlayerInfo();
            PLAYER_HEAD_RENDERER.setPlayerInfo(pusherPlayerInfo);
            ImageUtils.downloadAsync(musicDetail.getAlbum().getThumbnailPicUrl(200))
                    .thenAccept(imageTextureData -> {
                        imageTextureData.register().thenAcceptAsync((v) -> {
                            ImageTextureData blurredImageTextureData = ImageBlurPostProcessor.blur(imageTextureData, 50);
                            blurredImageTextureData.register().thenAccept((v1) -> Minecraft.getInstance().execute(() -> {
                                if (musicDetail.equals(nowPlayingInfo.getCurrentlyPlayingMusicDetail())) {
                                    var nextData = new TransitionNextData(blurredImageTextureData.getLocation(), imageTextureData.getLocation(), 1f);
                                    HudRenderData.getTransitionStatus().startTransition(nextData);
                                }
                            }));
                        }, MusicHud.EXECUTOR);
                    }).exceptionally(e -> {
                        var nextData = new TransitionNextData(null, null, 1f);
                        HudRenderData.getTransitionStatus().startTransition(nextData);
                        return null;
                    });
        }
    }

    public void reset() {
        TITLE_RENDERER.setText(I18n.get(MusicHud.MOD_ID + ".text.idle"));
        ARTISTS_AND_ALBUM_RENDERER.setText("");
        LYRICS_LINE_RENDERER.clear();
        PLAY_TIME_RENDERER.setText("");
        PLAYER_HEAD_RENDERER.setPlayerInfo(null);
        var nextData = new TransitionNextData(null, null, 1f);
        TransitionStatus<TransitionNextData> transitionStatus = HudRenderData.getTransitionStatus();
        transitionStatus.startTransition(nextData);
    }

    public void renderFrame(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!clientConfig.getEnable() || !clientConfig.getEnableHud()) {
            return;
        }
        NowPlayingInfo nowPlayingInfo = this.nowPlayingInfo;
        MusicDetail musicDetail = nowPlayingInfo.getCurrentlyPlayingMusicDetail();
        if ((musicDetail == null || musicDetail.equals(MusicDetail.NONE)) &&
                clientConfig.getHideHudWhenNotPlaying()) {
            return;
        }
        ProgressBar progressBar = progressDisplayData.getProgressBar();

        HudRenderData.getTransitionStatus().updateTransition();
        progressBar.setProgress(nowPlayingInfo.getProgressRate());

        Duration playedDuration = nowPlayingInfo.getPlayedDuration();
        Duration musicDuration = nowPlayingInfo.getMusicDuration();
        if (playedDuration != null && musicDuration != null && !musicDuration.isZero()) {
            DateTimeFormatter formatter = musicDuration.toHoursPart() >= 1 ?
                    DateTimeFormatter.ofPattern("HH:mm:ss") :
                    DateTimeFormatter.ofPattern("mm:ss");
            String playTimeString = formatter.format(
                    java.time.LocalTime.MIDNIGHT.plusSeconds(playedDuration.toSeconds())
            ) + " / " + formatter.format(
                    java.time.LocalTime.MIDNIGHT.plusSeconds(musicDuration.toSeconds())
            );
            PLAY_TIME_RENDERER.setText(playTimeString);
        }

        HUD_RENDERER.render(graphics);
        IMAGE_RENDERER.render(graphics);
        PLAYER_HEAD_RENDERER.render(graphics);
        PLAYING_STATUS_RENDERER.render(graphics);
        PROGRESS_RENDERER.render(graphics);

        float progressWidth = PROGRESS_RENDERER.getCurrentData().getLayout().width;
        Layout titleLayout = TITLE_RENDERER.getLayout();
        float titleMaxWidth = progressWidth - PLAYER_HEAD_RENDERER.getLayout().width - contentInterval;
        if (PLAYING_STATUS_RENDERER.isVisible()) {
            titleLayout.width = titleMaxWidth - contentPadding - PLAYING_STATUS_RENDERER.getLayout().width;
        } else {
            titleLayout.width = titleMaxWidth;
        }

        TITLE_RENDERER.render(graphics);
        LYRICS_LINE_RENDERER.render(graphics);

        ARTISTS_AND_ALBUM_RENDERER.getLayout().width = progressWidth - PLAY_TIME_RENDERER.calcDisplayWidth() - 1f;
        ARTISTS_AND_ALBUM_RENDERER.render(graphics);
        PLAY_TIME_RENDERER.render(graphics);
    }

    public void updateRenderPass(RenderPass renderPass) {
        HUD_RENDERER.updateRenderPass(renderPass);
        IMAGE_RENDERER.updateRenderPass(renderPass);
        PROGRESS_RENDERER.updateRenderPass(renderPass);
    }
}
