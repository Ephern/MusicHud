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

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a cover source into a canonical square texture.
 * <p>
 * The source is center-cropped to a square, then downsampled through a cached 9-tap
 * chain until it reaches the requested canonical size. Both the active and previous
 * cover therefore share exactly the same dimensions during crossfades.
 */
public final class SceneCover {
    private static final Logger LOGGER = MusicHud.getLogger(SceneCover.class);

    private SceneGpuContext owner;
    private DynamicTexture source;
    private int targetSize;
    private SceneGpuTexture target;
    private SceneGpuTexture[] intermediates = new SceneGpuTexture[0];

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

        if (target != null && owner == gpu && source == texture && targetSize == targetLongEdge) {
            return target;
        }

        release(gpu);
        owner = gpu;
        source = texture;
        targetSize = targetLongEdge;
        try {
            SceneTextureRef sourceRef = new GpuTextureViewRef(texture.getTextureView());
            int cropSize = Math.min(sourceWidth, sourceHeight);
            Rect sourceRect = centerSquare(sourceWidth, sourceHeight);
            if (cropSize <= targetLongEdge) {
                target = createTarget(gpu, targetLongEdge);
                resolvePass(gpu, sourceRef, sourceWidth, sourceHeight, sourceRect,
                        target, false);
                return target;
            }

            List<Integer> sizes = intermediateSizes(cropSize, targetLongEdge);
            intermediates = new SceneGpuTexture[sizes.size()];
            SceneTextureRef currentRef = sourceRef;
            int currentWidth = sourceWidth;
            int currentHeight = sourceHeight;
            Rect currentRect = sourceRect;
            for (int i = 0; i < sizes.size(); i++) {
                int size = sizes.get(i);
                SceneGpuTexture next = createTarget(gpu, size);
                intermediates[i] = next;
                resolvePass(gpu, currentRef, currentWidth, currentHeight, currentRect,
                        next, true);
                currentRef = next;
                currentWidth = size;
                currentHeight = size;
                currentRect = new Rect(0f, 0f, 1f, 1f);
            }

            target = createTarget(gpu, targetLongEdge);
            resolvePass(gpu, currentRef, currentWidth, currentHeight, currentRect,
                    target, true);
            releaseIntermediates(gpu);
            return target;
        } catch (RuntimeException failure) {
            LOGGER.debug("While resolving the background cover", failure);
            release(gpu);
            return null;
        }
    }

    private static List<Integer> intermediateSizes(int cropSize, int targetSize) {
        List<Integer> sizes = new ArrayList<>();
        int size = cropSize;
        while (size > targetSize * 2) {
            size = Math.max(targetSize, size / 2);
            if (size <= targetSize) {
                break;
            }
            sizes.add(size);
        }
        return sizes;
    }

    private static Rect centerSquare(int width, int height) {
        if (width == height) {
            return new Rect(0f, 0f, 1f, 1f);
        }
        if (width > height) {
            float inset = (width - height) * 0.5f / width;
            return new Rect(inset, 0f, 1f - inset, 1f);
        }
        float inset = (height - width) * 0.5f / height;
        return new Rect(0f, inset, 1f, 1f - inset);
    }

    private static SceneGpuTexture createTarget(SceneGpuContext gpu, int size) {
        return gpu.createRenderTarget("MusicHud canonical cover", size, size,
                SceneTextureFormat.RGBA16F, SceneSamplerState.linearClamp());
    }

    private static void resolvePass(SceneGpuContext gpu, SceneTextureRef source,
                                    int sourceWidth, int sourceHeight, Rect rect,
                                    SceneGpuTexture target, boolean downsample) {
        gpu.draw(new SceneDraw(target,
                gpu.pipeline(ScenePipelineKey.SCENE_COVER_RESOLVE, target.format()),
                List.of(new SceneSamplerBinding("Sampler0", source,
                        gpu.sampler(SceneSamplerState.linearClamp()))),
                List.of(new SceneUniformBinding(ResolveUniform.UBO_NAME,
                        new ResolveUniform(sourceWidth, sourceHeight,
                                rect.u0(), rect.v0(), rect.u1(), rect.v1(), downsample)))));
    }

    private void releaseIntermediates(SceneGpuContext gpu) {
        for (SceneGpuTexture intermediate : intermediates) {
            if (intermediate != null && owner == gpu) {
                gpu.release(intermediate);
            }
        }
        intermediates = new SceneGpuTexture[0];
    }

    private void release(SceneGpuContext gpu) {
        if (target != null && owner == gpu) {
            gpu.release(target);
        }
        releaseIntermediates(gpu);
        target = null;
        source = null;
        owner = null;
        targetSize = 0;
    }

    private record Rect(float u0, float v0, float u1, float v1) {
    }

    private record ResolveUniform(int sourceWidth, int sourceHeight,
                                  float u0, float v0, float u1, float v1,
                                  boolean downsample) implements SceneUniform {
        private static final String UBO_NAME = "MHCoverResolve";
        private static final int UBO_SIZE = Std140Sizes.calc()
                .putVec4().putVec4().putVec4().get();

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
            writer.putVec4(u0, v0, u1, v1);
            writer.putVec4(downsample ? 1f : 0f, 0f, 0f, 0f);
        }

        @Override
        public boolean shouldUseBuffer(SceneUniform lastBuffered) {
            return false;
        }
    }
}
