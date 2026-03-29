package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.music.StreamAudioPlayer;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

public class PlayingStatusRenderer {
    public static final ResourceLocation LOADING_ICON_LOCATION = MusicHud.location("textures/gui/icons/loader_circle.png");
    public static final ResourceLocation RETRYING_ICON_LOCATION = MusicHud.location("textures/gui/icons/rotate_cw.png");
    public static final ResourceLocation ERROR_ICON_LOCATION = MusicHud.location("textures/gui/icons/circle_x.png");
    private static volatile PlayingStatusRenderer instance;

    @Getter
    private Layout layout;
    private float rotationRadians = 0f;
    @Setter
    private boolean visibility = true;
    StreamAudioPlayer.Status status;
    private ResourceLocation currentResourceLocation;

    public static PlayingStatusRenderer getInstance() {
        if (instance == null) {
            synchronized (PlayingStatusRenderer.class) {
                if (instance == null)
                    instance = new PlayingStatusRenderer();
            }
        }
        return instance;
    }

    public void configureLayout(Layout layout) {
        this.layout = layout;
    }

    public void setStatus(StreamAudioPlayer.Status status) {
        this.status = status;
        currentResourceLocation = switch (status) {
            case BUFFERING -> LOADING_ICON_LOCATION;
            case RETRYING -> RETRYING_ICON_LOCATION;
            case ERROR -> ERROR_ICON_LOCATION;
            default -> null;
        };
    }

    public void render(GuiGraphics gr) {
        if (currentResourceLocation != null && visibility) {
            if (currentResourceLocation == ERROR_ICON_LOCATION) {
                rotationRadians = 0;
            } else {
                rotationRadians = (float) ((Math.PI * 2) * ((float) (System.currentTimeMillis() % 1000) / 1000));
            }

            Layout.AbsolutePosition absolutePosition = layout.calcAbsolutePosition(gr);
            int screenX = (int) absolutePosition.x();   // 图片左上角 X
            int screenY = (int) absolutePosition.y();   // 图片左上角 Y
            int width = (int) layout.width;     // 图片宽度
            int height = (int) layout.height;    // 图片高度

            // 计算图片中心坐标
            float centerX = screenX + width / 2f;
            float centerY = screenY + height / 2f;

            // 保存当前变换状态
            gr.pose().pushMatrix();

            gr.pose().translate(centerX, centerY);
            gr.pose().rotate(rotationRadians);
            gr.pose().translate(-centerX, -centerY);

            // 绘制图片（注意：此时坐标已经过变换）
            // 使用 blit 方法，RenderPipeline 选择 GUI_TEXTURED（标准纹理渲染）
            gr.blit(RenderPipelines.GUI_TEXTURED, currentResourceLocation, screenX, screenY, 0, 0, width, height, width, height);

            // 恢复变换
            gr.pose().popMatrix();
        }
    }

    public boolean isVisible() {
        return visibility && currentResourceLocation != null;
    }
}
