package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.render.*;
import indi.etern.musichud.client.ui.render.pipeline.*;
import indi.etern.musichud.client.ui.render.text.*;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.ui.hud.metadata.DynamicStatusUniform;
import indi.etern.musichud.client.ui.hud.metadata.SceneRenderData;
import indi.etern.musichud.client.ui.render.SceneStyle;
import indi.etern.musichud.client.ui.render.pipeline.GpuTextureViewRef;
import indi.etern.musichud.client.ui.render.pipeline.SceneRenderPipelines;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureSetup;
import indi.etern.musichud.client.utils.image.ImageTextureData;
import indi.etern.musichud.client.utils.image.ImageUtils;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.NotNull;

public class AlbumImageRenderer implements RenderLayer {
    private SceneRenderData currentData;
    private ImageTextureData icon;

    public void configure(SceneRenderData data) {
        this.currentData = data;
    }

    @Override
    public void render(SceneRenderContext context) {
        if (currentData == null) return;

        SceneTextureSetup textureSetup = getMixedTextureSetup();
        SceneStyle hudStyle = currentData.getHudStyle();

        SceneElementRenderState hudRenderState = new SceneElementRenderState(
                SceneRenderPipelines.ROUNDED_ALBUM,
                textureSetup,
                context.currentPose(),
                hudStyle,
                hudStyle,
                DynamicStatusUniform.getInstance()
        );
        context.submit(this, hudRenderState);
    }

    private @NotNull SceneTextureSetup getMixedTextureSetup() {
        var background = currentData.getTransitionableBackground();
        BackgroundData next = background.getNext();
        BackgroundData current = background.getCurrent();
        DynamicTexture currentTexture = current == null || current.image() == null || current.image().current == null ? getIconTexture() : current.image().current.getTexture();
        DynamicTexture nextTexture = next == null || next.image() == null || next.image().current == null ? getIconTexture() : next.image().current.getTexture();
        DynamicTexture transitionTexture = background.isTransitioning() ? nextTexture : currentTexture;
        if (currentTexture == null) {
            return SceneTextureSetup.NONE;
        }
        if (transitionTexture != null) {
            return SceneTextureSetup.doubleTexture(
                    new GpuTextureViewRef(currentTexture.getTextureView()),
                    new GpuTextureViewRef(transitionTexture.getTextureView())
            );
        }
        return SceneTextureSetup.single(new GpuTextureViewRef(currentTexture.getTextureView()));
    }

    private DynamicTexture getIconTexture() {
        if (icon == null) {
            icon = ImageUtils.loadBase64(MusicHud.ICON_BASE64);
        }
        return icon.getTexture();
    }
}
