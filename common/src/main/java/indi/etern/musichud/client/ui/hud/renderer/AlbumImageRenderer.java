package indi.etern.musichud.client.ui.hud.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundImages;
import indi.etern.musichud.client.ui.hud.metadata.DynamicStatusUniform;
import indi.etern.musichud.client.ui.hud.metadata.SceneRenderData;
import indi.etern.musichud.client.ui.render.RenderLayer;
import indi.etern.musichud.client.ui.render.RenderRuntime;
import indi.etern.musichud.client.ui.render.SceneElementRenderState;
import indi.etern.musichud.client.ui.render.SceneRenderContext;
import indi.etern.musichud.client.ui.render.SceneStyle;
import indi.etern.musichud.client.ui.render.gpu.ArtworkDownsampleCache;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuContext;
import indi.etern.musichud.client.ui.render.gpu.SceneGpuTexture;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.BlazeTextureRefs;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.TextureSampling;
import indi.etern.musichud.client.ui.render.pipeline.SceneRenderPipelines;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureSetup;
import indi.etern.musichud.client.utils.image.ImageTextureData;
import indi.etern.musichud.client.utils.image.ImageUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AlbumImageRenderer implements RenderLayer {
    private final ArtworkDownsampleCache artworkCache = new ArtworkDownsampleCache();
    private SceneRenderData currentData;
    private ImageTextureData icon;

    public void configure(SceneRenderData data) {
        this.currentData = data;
    }

    @Override
    public void render(SceneRenderContext context) {
        if (currentData == null) return;

        SceneStyle hudStyle = currentData.getHudStyle();
        SceneTextureSetup textureSetup = getTextureSetup(hudStyle);
        if (textureSetup == null) {
            return;
        }
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

    private @Nullable SceneTextureSetup getTextureSetup(SceneStyle hudStyle) {
        SceneGpuContext gpu;
        try {
            gpu = RenderRuntime.getInstance().gpu();
        } catch (RuntimeException unavailable) {
            return null;
        }
        var transition = currentData.getTransitionableBackground();
        SceneGpuTexture current = resample(gpu, transition.getCurrent(), hudStyle);
        if (current == null) {
            return null;
        }
        // The album pipeline always samples Sampler1, so it needs a second binding even without a
        // crossfade.
        SceneGpuTexture next = transition.isTransitioning()
                ? resample(gpu, transition.getNext(), hudStyle) : null;
        return SceneTextureSetup.doubleTexture(current, next != null ? next : current);
    }

    /** Resamples the artwork to the display size. */
    private @Nullable SceneGpuTexture resample(SceneGpuContext gpu, @Nullable BackgroundData data,
                                               SceneStyle hudStyle) {
        ImageTextureData source = sourceOf(data);
        if (source == null) {
            return null;
        }
        DynamicTexture texture = source.getTexture();
        if (texture == null) {
            return null;
        }
        NativeImage pixels = texture.getPixels();
        if (pixels == null) {
            return null;
        }
        TextureSampling.ensureLinear(texture);
        float guiScale = guiScale();
        return artworkCache.get(gpu, source, BlazeTextureRefs.of(texture),
                pixels.getWidth(), pixels.getHeight(),
                hudStyle.getWidth() * guiScale, hudStyle.getHeight() * guiScale);
    }

    private @Nullable ImageTextureData sourceOf(@Nullable BackgroundData data) {
        if (data != null && data.image() != null) {
            BackgroundImages images = data.image();
            if (images.source != null) {
                return images.source;
            }
            if (images.original != null) {
                return images.original;
            }
            if (images.current != null) {
                return images.current;
            }
        }
        return getIcon();
    }

    private @NotNull ImageTextureData getIcon() {
        if (icon == null) {
            icon = ImageUtils.loadBase64(ImageUtils.coverPlaceholder());
        }
        return icon;
    }

    private static float guiScale() {
        Window window = Minecraft.getInstance().getWindow();
        //noinspection ConstantValue
        return window == null ? 1f : (float) window.getGuiScale();
    }
}
