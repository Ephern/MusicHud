package indi.etern.musichud.client.ui.hud.scene;

import com.mojang.blaze3d.platform.Window;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.interfaces.IClientEventService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.hud.layout.HudLayouts;
import indi.etern.musichud.client.ui.hud.metadata.ProgressBarData;
import indi.etern.musichud.client.ui.hud.metadata.SceneRenderData;
import indi.etern.musichud.client.ui.hud.presentation.BackgroundState;
import indi.etern.musichud.client.ui.hud.presentation.HudPresentationModel;
import indi.etern.musichud.client.ui.hud.presentation.HudPresenter;
import indi.etern.musichud.client.ui.hud.renderer.*;
import indi.etern.musichud.client.ui.hud.renderer.debug.HudLayoutDebugRenderer;
import indi.etern.musichud.client.ui.render.*;
import indi.etern.musichud.client.ui.screen.MusicHudScreen;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.Unregister;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

public final class HudScene implements RenderScene {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static volatile HudScene instance;

    private final HudPresenter presenter = HudPresenter.getInstance();
    private final BackgroundRenderer BACKGROUND_RENDERER =
            new BackgroundRenderer(BackgroundTarget.HUD);
    private final AlbumImageRenderer IMAGE_RENDERER = new AlbumImageRenderer();
    private final PlayerHeadRenderer PLAYER_HEAD_RENDERER = new PlayerHeadRenderer();
    private final PlayingStatusRenderer PLAYING_STATUS_RENDERER = new PlayingStatusRenderer();
    private final ProgressRenderer PROGRESS_RENDERER = new ProgressRenderer();
    private final TextRenderer TITLE_RENDERER = new TextRenderer();
    private final TextRenderer ARTISTS_AND_ALBUM_RENDERER = new TextRenderer();
    private final TextRenderer PLAY_TIME_RENDERER = new TextRenderer();
    private final ScrollingLyricLineRenderer LYRICS_LINE_RENDERER = new ScrollingLyricLineRenderer();
    private final HudLayoutDebugRenderer HUD_LAYOUT_DEBUG_RENDERER = new HudLayoutDebugRenderer();

    @Getter
    @Setter
    private volatile SceneStyle baseHudStyle;
    private volatile SceneRenderData hudBaseData;
    private volatile SceneRenderData imageDisplayData;
    private volatile HudLayouts layout;
    private Window window;
    private Logger logger;
    private Supplier<String> appliedSkinSupplier;
    private int appliedStatusRevision = -1;
    private int appliedThumbnailSize = -1;

    private HudScene() {
        presenter.setLyricSink(new HudPresenter.LyricSink() {
            @Override
            public void submit(ScrollingLyricLineRenderer.Line line1, ScrollingLyricLineRenderer.Line line2) {
                LYRICS_LINE_RENDERER.setLines(line1, line2, true);
            }

            @Override
            public void clear() {
                LYRICS_LINE_RENDERER.clear();
            }
        });
        updateLayoutFromConfig();
        refreshStyle();
        registerPipelinePrecompile();
    }

    /**
     * The background chain's GLSL compiles on first use (~200 ms with a cold driver cache), so
     * compiling it lazily stalls the moment the first song starts. The first client tick happens
     * during startup, long before any background is rendered; the hook retries until the render
     * device is ready and then unregisters itself.
     */
    private void registerPipelinePrecompile() {
        try {
            Unregister[] handle = new Unregister[1];
            handle[0] = IClientEventService.getInstance().registerClientTickPost(() -> {
                RenderRuntime runtime = RenderRuntime.getInstance();
                runtime.precompileScenePipelines();
                if (runtime.isPipelinePrecompileSettled() && handle[0] != null) {
                    handle[0].unregister();
                }
            });
        } catch (RuntimeException failure) {
            logger().debug("While registering the scene pipeline precompile hook", failure);
        }
    }

    public static HudScene getInstance() {
        if (instance == null) {
            synchronized (HudScene.class) {
                if (instance == null) {
                    instance = new HudScene();
                }
            }
        }
        return instance;
    }

    public void render(SceneRenderContext context) {
        try {
            // State advances even while hidden, so transitions keep their real start time.
            updateFrameState();
            if (MusicHudScreen.isHudOccluded()
                    || presenter.getModel().getHudAlpha() <= 1e-4f) {
                return;
            }
            drawFrame(context);
        } catch (Exception e) {
            logger().error("While rendering HUD frame", e);
        }
    }

    private void updateFrameState() {
        presenter.updateFrame();
        HudPresentationModel model = presenter.getModel();

        SceneStyle baseStyle = baseHudStyle;
        if (baseStyle != null) {
            baseStyle.setAlpha(model.getHudAlpha());
        }

        TITLE_RENDERER.setText(model.getTitle());
        ARTISTS_AND_ALBUM_RENDERER.setText(model.getArtistsAndAlbum());
        PLAY_TIME_RENDERER.setText(model.getPlayTime());

        Supplier<String> skinSupplier = model.getPlayerSkinSupplier();
        if (skinSupplier != appliedSkinSupplier) {
            PLAYER_HEAD_RENDERER.setPlayerSkinSupplier(skinSupplier);
            appliedSkinSupplier = skinSupplier;
        }
        if (appliedStatusRevision != model.getStatusRevision()) {
            PLAYING_STATUS_RENDERER.updateStatus(model.getAudioStatus());
            appliedStatusRevision = model.getStatusRevision();
        }

        TITLE_RENDERER.update();
        ARTISTS_AND_ALBUM_RENDERER.update();
        PLAY_TIME_RENDERER.update();
        PLAYER_HEAD_RENDERER.update();
        LYRICS_LINE_RENDERER.update();

        HudLayouts currentLayout = layout;
        if (currentLayout != null) {
            float playTimeWidth = PLAY_TIME_RENDERER.measureDisplayWidth(currentLayout.getInfoTextSize());
            currentLayout.arrange(PLAYER_HEAD_RENDERER.isVisible(),
                    PLAYING_STATUS_RENDERER.isVisible(), playTimeWidth);
        }
        refreshThumbnailSize();
    }

    private void drawFrame(SceneRenderContext context) {
        BACKGROUND_RENDERER.render(context);
        IMAGE_RENDERER.render(context);
        PLAYER_HEAD_RENDERER.render(context);
        PLAYING_STATUS_RENDERER.render(context);
        PROGRESS_RENDERER.render(context);
        TITLE_RENDERER.render(context);
        LYRICS_LINE_RENDERER.render(context);
        ARTISTS_AND_ALBUM_RENDERER.render(context);
        PLAY_TIME_RENDERER.render(context);

        if (clientConfig.getShowHudLayoutBoxes()) {
            HUD_LAYOUT_DEBUG_RENDERER.render(context);
        }
    }

    public void updateLayoutFromConfig() {
        try {
            baseHudStyle = new SceneStyle(
                    clientConfig.getHudOffsetX(),
                    clientConfig.getHudOffsetY(),
                    clientConfig.getHudWidth(),
                    clientConfig.getHudHeight(),
                    clientConfig.getHudCornerRadius(),
                    HorizontalAlign.valueOf(clientConfig.getHudHorizontalPosition()),
                    VerticalAlign.valueOf(clientConfig.getHudVerticalPosition())
            );
        } catch (Exception e) {
            logger().error("While configure HUD layout from config", e);
        }
    }

    public void refreshStyle() {
        try {
            SceneStyle base = baseHudStyle;
            if (base == null) {
                return;
            }
            HudLayouts newLayout = HudLayouts.create(base);
            HUD_LAYOUT_DEBUG_RENDERER.configure(newLayout);

            configureBaseRenderer(base);
            configureImageRenderer(newLayout.getImageStyle());
            configureProgressRenderer(newLayout.getProgressStyle());

            PLAYING_STATUS_RENDERER.configure(newLayout.getStatusStyle());
            PLAYING_STATUS_RENDERER.setVisibility(true);
            PLAYER_HEAD_RENDERER.configure(newLayout.getHeadStyle());
            TITLE_RENDERER.configure(newLayout.getTitleStyle(), Theme.EMPHASIZE_TEXT_COLOR, TextRenderer.Position.LEFT);

            LYRICS_LINE_RENDERER.setHudStyle(newLayout.getLyricsStyle());
            LYRICS_LINE_RENDERER.setLine1Height(newLayout.getLyricsSize());
            LYRICS_LINE_RENDERER.setLine2Height(newLayout.getSubLyricsSize());
            LYRICS_LINE_RENDERER.setLineSpacing((int) newLayout.getContentInterval());

            ARTISTS_AND_ALBUM_RENDERER.configure(newLayout.getArtistsStyle(), Theme.HUD_FADE_COLOR, TextRenderer.Position.LEFT);
            PLAY_TIME_RENDERER.configure(newLayout.getPlayTimeStyle(), Theme.HUD_FADE_COLOR, TextRenderer.Position.RIGHT);

            newLayout.arrange(PLAYER_HEAD_RENDERER.isVisible(),
                    PLAYING_STATUS_RENDERER.isVisible(),
                    PLAY_TIME_RENDERER.measureDisplayWidth(newLayout.getInfoTextSize()));
            // Swap after the first arrange: a live swap lets the render thread measure the same tree.
            layout = newLayout;
            refreshThumbnailSize();
        } catch (Exception e) {
            logger().error("While refresh HUD style", e);
        }
    }

    private void refreshThumbnailSize() {
        if (window == null) {
            Minecraft minecraft = Minecraft.getInstance();
            // Minecraft.getInstance() returns null during NeoForge launch
            //noinspection ConstantValue
            if (minecraft != null) {
                window = minecraft.getWindow();
            }
        }
        //noinspection ConstantValue
        if (window != null && imageDisplayData != null) {
            int thumbnailSize = (int) (imageDisplayData.getHudStyle().getWidth() * window.getGuiScale());
            if (thumbnailSize != appliedThumbnailSize) {
                appliedThumbnailSize = thumbnailSize;
                presenter.setThumbnailSize(thumbnailSize);
            }
        }
    }

    private void configureProgressRenderer(SceneStyle hudStyle) {
        PROGRESS_RENDERER.setProgressData(new ProgressBarData(
                hudStyle,
                Theme.HUD_PROGRESS_LEFT,
                Theme.HUD_PROGRESS_CURRENT,
                Theme.HUD_PROGRESS_BACKGROUND,
                hudStyle.getHeight() * 6,
                2f,
                0.01f
        ));
    }

    private void configureBaseRenderer(SceneStyle hudStyle) {
        if (hudBaseData == null) {
            hudBaseData = new SceneRenderData(hudStyle);
            hudBaseData.setTransitionableBackground(BackgroundState.getInstance().getTransition());
        } else {
            hudBaseData.setHudStyle(hudStyle);
        }
        BACKGROUND_RENDERER.configure(hudBaseData);
    }

    private void configureImageRenderer(SceneStyle imageHudStyle) {
        if (imageDisplayData == null) {
            imageDisplayData = new SceneRenderData(imageHudStyle);
            imageDisplayData.setFallback(hudBaseData);
        } else {
            imageDisplayData.setHudStyle(imageHudStyle);
        }
        IMAGE_RENDERER.configure(imageDisplayData);
    }

    private Logger logger() {
        if (logger == null) {
            logger = MusicHud.getLogger(HudScene.class);
        }
        return logger;
    }
}
