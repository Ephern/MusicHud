package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public class PlayingStatusRenderer implements HudRenderer {
    // From Lucide Icons
    public static final Identifier LOADING_ICON_LOCATION = MusicHud.location("textures/gui/icons/loader_circle.png");
    public static final Identifier RETRYING_ICON_LOCATION = MusicHud.location("textures/gui/icons/rotate_cw.png");
    public static final Identifier ERROR_ICON_LOCATION = MusicHud.location("textures/gui/icons/circle_x.png");
    public static final Identifier PLAYING_CONNECTED_ICON_LOCATION = MusicHud.location("textures/gui/icons/link.png");
    public static final Identifier PLAYING_ISOLATED_LOCATION = MusicHud.location("textures/gui/icons/unlink.png");
    private static volatile PlayingStatusRenderer instance;
    StreamAudioPlayer.Status status;
    @Getter
    private Layout layout;
    @Setter
    private boolean visibility = true;
    private Identifier currentResourceLocation;

    public static PlayingStatusRenderer getInstance() {
        if (instance == null) {
            synchronized (PlayingStatusRenderer.class) {
                if (instance == null)
                    instance = new PlayingStatusRenderer();
            }
        }
        return instance;
    }

    public void configure(Layout layout) {
        this.layout = layout;
    }

    public void updateStatus(StreamAudioPlayer.Status status) {
        if (status != null) {
            this.status = status;
        }
        currentResourceLocation = switch (this.status) {
            case BUFFERING -> LOADING_ICON_LOCATION;
            case RETRYING -> RETRYING_ICON_LOCATION;
            case ERROR -> ERROR_ICON_LOCATION;
            default -> {
                if (MusicHud.getConnectStatus() == MusicHud.ConnectStatus.CONNECTED) {
                    yield PLAYING_CONNECTED_ICON_LOCATION;
                } else if (MusicHud.getConnectStatus() == MusicHud.ConnectStatus.NOT_CONNECTED) {
                    yield PLAYING_ISOLATED_LOCATION;
                } else {
                    yield null;
                }
            }
        };
    }

    @Override
    public void render(HudRenderContext hudRenderContext) {
        Identifier currentResourceLocation1 = currentResourceLocation;
        if (currentResourceLocation1 != null && visibility) {
            float rotationRadians;
            if (currentResourceLocation1 == RETRYING_ICON_LOCATION || currentResourceLocation1 == LOADING_ICON_LOCATION) {
                rotationRadians = (float) ((Math.PI * 2) * ((float) (System.currentTimeMillis() % 1000) / 1000));
            } else {
                rotationRadians = 0;
            }

            Layout.AbsolutePosition absolutePosition = layout.calcAbsolutePosition(hudRenderContext);
            int screenX = (int) absolutePosition.x();
            int screenY = (int) absolutePosition.y();
            int width = (int) layout.getWidth();
            int height = (int) layout.getHeight();

            float centerX = screenX + width / 2f;
            float centerY = screenY + height / 2f;

            hudRenderContext.transform()
                    .translate(centerX, centerY)
                    .rotate(rotationRadians)
                    .translate(-centerX, -centerY)
                    .then(transforming -> {
                        hudRenderContext.blit(RenderPipelines.GUI_TEXTURED, currentResourceLocation1, screenX, screenY, 0, 0, width, height, width, height);
                    });
        }
    }

    public boolean isVisible() {
        return visibility && currentResourceLocation != null;
    }
}
