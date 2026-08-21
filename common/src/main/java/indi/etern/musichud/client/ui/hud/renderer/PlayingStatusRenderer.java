package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import indi.etern.musichud.connection.ConnectionStateMachine;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.Identifier;

public class PlayingStatusRenderer implements HudRenderer {
    // From Lucide Icons. Plain resource paths so this file stays identical across the
    // ResourceLocation -> Identifier rename.
    public static final String LOADING_ICON_LOCATION = MusicHud.MOD_ID + ":textures/gui/icons/loader_circle.png";
    public static final String RETRYING_ICON_LOCATION = MusicHud.MOD_ID + ":textures/gui/icons/rotate_cw.png";
    public static final String ERROR_ICON_LOCATION = MusicHud.MOD_ID + ":textures/gui/icons/circle_x.png";
    public static final String PLAYING_CONNECTED_ICON_LOCATION = MusicHud.MOD_ID + ":textures/gui/icons/link.png";
    public static final String PLAYING_ISOLATED_LOCATION = MusicHud.MOD_ID + ":textures/gui/icons/unlink.png";
    public static final String MUTED_LOCATION = MusicHud.MOD_ID + ":textures/gui/icons/volume_x.png";
    private static volatile PlayingStatusRenderer instance;

    private final ClientConfig clientConfig = ClientConfig.getInstance();
    StreamAudioPlayer.Status status;
    @Getter
    private Layout layout;
    @Setter
    private boolean visibility = true;
    private String currentResourceLocation;

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
                if (clientConfig.getMuted()) {
                    yield MUTED_LOCATION;
                } else if (ConnectionStateMachine.getConnectStatus() == MusicHud.ConnectStatus.CONNECTED) {
                    yield PLAYING_CONNECTED_ICON_LOCATION;
                } else {
                    yield PLAYING_ISOLATED_LOCATION;
                }
            }
        };
    }

    @Override
    public void render(HudRenderContext hudRenderContext) {
        String currentResourceLocation1 = currentResourceLocation;
        if (currentResourceLocation1 != null && visibility) {
            float rotationRadians;
            if (currentResourceLocation1.equals(RETRYING_ICON_LOCATION) || currentResourceLocation1.equals(LOADING_ICON_LOCATION)) {
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
                    .end(transforming -> {
                        hudRenderContext.graphics().blitTextured(currentResourceLocation1, screenX, screenY, 0, 0, width, height, width, height);
                    });
        }
    }

    public boolean isVisible() {
        return visibility && currentResourceLocation != null;
    }
}
