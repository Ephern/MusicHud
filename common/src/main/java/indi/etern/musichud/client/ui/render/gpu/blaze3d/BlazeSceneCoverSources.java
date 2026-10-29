package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.blaze3d.platform.NativeImage;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundImages;
import indi.etern.musichud.client.ui.render.gpu.SceneCoverSource;
import indi.etern.musichud.client.utils.image.ImageTextureData;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.Nullable;

public final class BlazeSceneCoverSources {
    private BlazeSceneCoverSources() {
    }

    public static @Nullable SceneCoverSource of(@Nullable BackgroundImages images) {
        if (images == null) return null;
        ImageTextureData data = images.original != null ? images.original : images.current;
        if (data == null) return null;
        DynamicTexture texture = data.getTexture();
        if (texture == null) return null;
        NativeImage pixels = texture.getPixels();
        if (pixels == null) return null;
        return new SceneCoverSource(BlazeTextureRefs.of(texture),
                pixels.getWidth(), pixels.getHeight(), data);
    }
}
