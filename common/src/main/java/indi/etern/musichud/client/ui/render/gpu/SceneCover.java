package indi.etern.musichud.client.ui.render.gpu;

import com.mojang.blaze3d.platform.NativeImage;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.pipeline.GpuTextureViewRef;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Sizes;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;
import indi.etern.musichud.client.utils.image.ImageTextureData;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Reusable downsampled copy of the current background cover.
 * <p>
 * The source texture is resampled with the existing 9-tap blur downsample pass and cached until
 * the cover texture or the requested size changes, so the cost is paid only on cover switches
 * or layout changes.
 */
public final class SceneCover {
    private static final Logger LOGGER = MusicHud.getLogger(SceneCover.class);

    private SceneGpuContext owner;
    private DynamicTexture source;
    private int targetWidth;
    private int targetHeight;
    private SceneGpuTexture target;

    public @Nullable SceneGpuTexture prepare(SceneGpuContext gpu, ImageTextureData image,
                                             int targetLongEdge) {
        if (gpu == null || image == null || targetLongEdge < 1) return null;

        DynamicTexture texture;
        int sourceWidth;
        int sourceHeight;
        try {
            texture = image.getTexture();
            NativeImage pixels = texture.getPixels();
            if (pixels == null) return null;
            sourceWidth = pixels.getWidth();
            sourceHeight = pixels.getHeight();
        } catch (RuntimeException failure) {
            LOGGER.debug("While reading the background cover image", failure);
            return null;
        }
        if (sourceWidth < 1 || sourceHeight < 1) return null;

        int sourceLong = Math.max(sourceWidth, sourceHeight);
        int sourceShort = Math.min(sourceWidth, sourceHeight);
        int longEdge = Math.min(targetLongEdge, sourceLong);
        if (longEdge < 1) return null;
        int shortEdge = Math.max(1, Math.round((float) longEdge * sourceShort / sourceLong));
        boolean landscape = sourceWidth >= sourceHeight;
        int width = landscape ? longEdge : shortEdge;
        int height = landscape ? shortEdge : longEdge;

        if (target != null && owner == gpu && source == texture
                && targetWidth == width && targetHeight == height) {
            return target;
        }

        release(gpu);
        try {
            target = gpu.createRenderTarget("MusicHud background cover", width, height,
                    SceneTextureFormat.RGBA8, SceneSamplerState.linearClamp());
            owner = gpu;
            source = texture;
            targetWidth = width;
            targetHeight = height;

            SceneTextureRef sourceRef = new GpuTextureViewRef(texture.getTextureView());
            gpu.draw(new SceneDraw(target,
                    gpu.pipeline(ScenePipelineKey.SCENE_BLUR_DOWNSAMPLE, target.format()),
                    List.of(new SceneSamplerBinding("Sampler0", sourceRef,
                            gpu.sampler(SceneSamplerState.linearClamp()))),
                    List.of(new SceneUniformBinding(DownsampleUniform.UBO_NAME,
                            new DownsampleUniform(sourceWidth, sourceHeight)))));
            return target;
        } catch (RuntimeException failure) {
            LOGGER.debug("While downsampling the background cover", failure);
            release(gpu);
            return null;
        }
    }

    private void release(SceneGpuContext gpu) {
        if (target != null && owner == gpu) {
            gpu.release(target);
        }
        target = null;
        source = null;
        owner = null;
        targetWidth = 0;
        targetHeight = 0;
    }

    private record DownsampleUniform(int sourceWidth, int sourceHeight) implements SceneUniform {
        private static final String UBO_NAME = "MHBlur";
        private static final int UBO_SIZE = Std140Sizes.calc().putVec4().putVec4().get();

        @Override
        public String getUBOName() {
            return UBO_NAME;
        }

        @Override
        public int getUBOSize() {
            return UBO_SIZE;
        }

        @Override
        public void write(Std140Writer writer) {
            writer.putVec4(1f / sourceWidth, 1f / sourceHeight, sourceWidth, sourceHeight);
            writer.putVec4(0f, 0f, 0f, 0f);
        }

        @Override
        public boolean shouldUseBuffer(SceneUniform lastBuffered) {
            return false;
        }
    }
}
