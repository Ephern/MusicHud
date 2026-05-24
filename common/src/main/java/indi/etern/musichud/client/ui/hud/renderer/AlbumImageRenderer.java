package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.ui.hud.metadata.DynamicStatusUniform;
import indi.etern.musichud.client.ui.hud.metadata.HudRenderData;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import indi.etern.musichud.client.ui.hud.pipelines.HudRenderPipelines;
import indi.etern.musichud.client.ui.hud.pipelines.HudRenderState;
import indi.etern.musichud.client.ui.utils.image.ImageTextureData;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.NotNull;

public class AlbumImageRenderer implements HudRenderer {
    private static volatile AlbumImageRenderer instance;
    private HudRenderData currentData;
    private ImageTextureData icon;

    public static AlbumImageRenderer getInstance() {
        if (instance == null) {
            synchronized (AlbumImageRenderer.class) {
                if (instance == null)
                    instance = new AlbumImageRenderer();
            }
        }
        return instance;
    }

    public void configure(HudRenderData data) {
        this.currentData = data;
    }

    @Override
    public void render(HudRenderContext context) {
        if (currentData == null) return;

        Integer[] textureIds = getMixedTextureIds();
        Layout layout = currentData.getLayout();

        HudRenderState hudRenderState = new HudRenderState(
                HudRenderPipelines.ROUNDED_ALBUM,
                textureIds,
                context.currentPose(),
                layout,
                layout,
                DynamicStatusUniform.getInstance()
        );
        context.submitHudRenderState(hudRenderState);
    }

    private Integer[] getMixedTextureIds() {
        var background = currentData.getTransitionableBackground();
        BackgroundData next = background.getNext();
        BackgroundData current = background.getCurrent();
        DynamicTexture currentTexture = current == null || current.image() == null || current.image().current == null ? getIconTexture() : current.image().current.getTexture();
        DynamicTexture nextTexture = next == null || next.image() == null || next.image().current == null ? getIconTexture() : next.image().current.getTexture();
        DynamicTexture transitionTexture = background.isTransitioning() ? nextTexture : currentTexture;

        Integer sampler0 = currentTexture != null ? currentTexture.getId() : null;
        Integer sampler1 = (background.isTransitioning() && nextTexture != null) ? nextTexture.getId() : null;

        return new Integer[] { sampler0, sampler1 };
    }

    private DynamicTexture getIconTexture() {
        if (icon == null) {
            icon = ImageUtils.loadBase64(MusicHud.ICON_BASE64);
        }
        return icon.getTexture();
    }
}
