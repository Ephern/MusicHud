package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.metadata.*;
import indi.etern.musichud.client.ui.hud.pipelines.HudRenderPipelines;
import indi.etern.musichud.client.ui.hud.pipelines.HudRenderState;
import indi.etern.musichud.client.ui.utils.image.ImageTextureData;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public class AlbumImageRenderer implements HudRenderer {
    private static volatile AlbumImageRenderer instance;
    private Identifier defaultImageLocation;
    private HudRenderData currentData;

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

        TextureSetup textureSetup = getMixedTextureSetup();
        Layout layout = currentData.getLayout();

        HudRenderState hudRenderState = new HudRenderState(
                HudRenderPipelines.ROUNDED_ALBUM,
                textureSetup,
                context.currentPose(),
                layout,
                layout,
                DynamicStatusUniform.getInstance()
        );
        context.submitHudRenderState(hudRenderState);
    }

    private @NotNull TextureSetup getMixedTextureSetup() {
        var background = currentData.getTransitionableBackground();
        BackgroundData next = background.getNext();
        BackgroundData current = background.getCurrent();
        Identifier nextUnblurred = next == null || next.image() == null ? null : next.image().unblurredLocation;
        Identifier currentUnblurred = current.image() != null ? current.image().unblurredLocation : null;
        DynamicTexture currentTexture = getDynamicTexture(currentUnblurred);
        DynamicTexture nextTexture = getDynamicTexture(nextUnblurred);
        DynamicTexture transitionTexture = background.isTransitioning() ?
                nextTexture : currentTexture;

        TextureSetup textureSetup;
        if (currentTexture != null) {
            textureSetup = transitionTexture != null ?
                    TextureSetup.doubleTexture(
                            currentTexture.getTextureView(), currentTexture.getSampler(),
                            transitionTexture.getTextureView(), transitionTexture.getSampler()
                    ) : TextureSetup.singleTexture(currentTexture.getTextureView(), currentTexture.getSampler());
        } else {
            textureSetup = TextureSetup.noTexture();
        }
        return textureSetup;
    }

    private DynamicTexture getDynamicTexture(Identifier imageLocation) {
        if (imageLocation == null) {
            if (defaultImageLocation == null) {
                String greyImageBase64 = MusicHud.ICON_BASE64;
                ImageTextureData imageTextureData = ImageUtils.loadBase64(greyImageBase64);
                imageTextureData.register().join();
                defaultImageLocation = imageTextureData.getLocation();
            }
            return getDynamicTexture(defaultImageLocation);
        }
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(imageLocation);
        if (texture instanceof DynamicTexture dynamicTexture) return dynamicTexture;
        return null;
    }
}
