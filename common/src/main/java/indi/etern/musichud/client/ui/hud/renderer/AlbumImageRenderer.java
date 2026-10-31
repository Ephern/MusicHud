package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.render.*;
import indi.etern.musichud.client.ui.render.pipeline.*;
import indi.etern.musichud.client.ui.render.text.*;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.ui.hud.metadata.DynamicStatusUniform;
import indi.etern.musichud.client.ui.hud.metadata.SceneRenderData;
import indi.etern.musichud.client.ui.render.SceneStyle;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.BlazeTextureRefs;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.gl.GlTextureSampling;
import indi.etern.musichud.client.ui.render.pipeline.SceneRenderPipelines;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureSetup;
import indi.etern.musichud.client.utils.image.ImageTextureData;
import indi.etern.musichud.client.utils.image.ImageUtils;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.Window;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AlbumImageRenderer implements RenderLayer {
    private SceneRenderData currentData;
    private ImageTextureData icon;

    public void configure(SceneRenderData data) {
        this.currentData = data;
    }

    @Override
    public void render(SceneRenderContext context) {
        if (currentData == null) return;

        SceneStyle hudStyle = currentData.getHudStyle();
        SceneTextureSetup textureSetup = getMixedTextureSetup(hudStyle);

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

    private @NotNull SceneTextureSetup getMixedTextureSetup(SceneStyle hudStyle) {
        var background = currentData.getTransitionableBackground();
        BackgroundData next = background.getNext();
        BackgroundData current = background.getCurrent();
        DynamicTexture currentTexture = current == null || current.image() == null || current.image().current == null ? getIconTexture() : current.image().current.getTexture();
        DynamicTexture nextTexture = next == null || next.image() == null || next.image().current == null ? getIconTexture() : next.image().current.getTexture();
        DynamicTexture transitionTexture = background.isTransitioning() ? nextTexture : currentTexture;
        if (currentTexture == null) {
            return SceneTextureSetup.NONE;
        }
        enableTrilinearIfMinified(currentTexture, hudStyle);
        enableTrilinearIfMinified(transitionTexture, hudStyle);
        if (transitionTexture != null) {
            return SceneTextureSetup.doubleTexture(
                    BlazeTextureRefs.of(currentTexture),
                    BlazeTextureRefs.of(transitionTexture)
            );
        }
        return SceneTextureSetup.single(BlazeTextureRefs.of(currentTexture));
    }

    private DynamicTexture getIconTexture() {
        if (icon == null) {
            icon = ImageUtils.loadBase64(MusicHud.ICON_BASE64);
        }
        return icon.getTexture();
    }

    /** Enables trilinear sampling when the texture is minified into {@code hudStyle}. */
    private static void enableTrilinearIfMinified(@Nullable DynamicTexture texture, SceneStyle hudStyle) {
        if (texture == null) {
            return;
        }
        GpuTexture gpu = texture.getTexture();
        if (gpu == null) {
            return;
        }
        Window window = Minecraft.getInstance().getWindow();
        float destination = (float) (hudStyle.getWidth() * (window == null ? 1.0 : window.getGuiScale()));
        if (destination > 0f && gpu.getWidth(0) >= destination * 1.25f) {
            GlTextureSampling.enableTrilinear(texture);
        }
    }
}
