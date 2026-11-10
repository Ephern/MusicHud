package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Sizes;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class SceneCover {
    private static final Logger LOGGER = MusicHud.getLogger(SceneCover.class);
    private static final int[] NO_INTERMEDIATES = new int[0];

    private SceneGpuContext owner;
    private SceneCoverSource source;
    private int targetSize;
    private SceneGpuTexture target;
    private SceneGpuTexture[] intermediates = new SceneGpuTexture[0];
    /** Sizes of {@link #intermediates}; the targets are only rebuilt when this set changes. */
    private int[] intermediateSizeSet = NO_INTERMEDIATES;

    public @Nullable SceneGpuTexture prepare(SceneGpuContext gpu, SceneCoverSource image,
                                             int targetLongEdge) {
        if (gpu == null || image == null || targetLongEdge < 1) return null;

        int sourceWidth = image.width();
        int sourceHeight = image.height();
        if (sourceWidth < 1 || sourceHeight < 1) return null;

        if (target != null && owner == gpu && Objects.equals(source, image)
                && targetSize == targetLongEdge) {
            return target;
        }

        SceneTextureRef sourceRef = image.texture();
        if (sourceRef == null) return null;
        int cropSize = Math.min(sourceWidth, sourceHeight);
        Rect sourceRect = centerSquare(sourceWidth, sourceHeight);
        boolean downsample = cropSize > targetLongEdge;
        int[] sizes = downsample ? intermediateSizes(cropSize, targetLongEdge) : NO_INTERMEDIATES;
        try {
            // A new song only changes the pixels, not the layout: keep the targets whenever the
            // size set is unchanged, otherwise every track switch reallocates several megabytes.
            if (!ensureTargets(gpu, sizes, targetLongEdge)) return null;
            source = image;
            SceneTextureRef currentRef = sourceRef;
            int currentWidth = sourceWidth;
            int currentHeight = sourceHeight;
            Rect currentRect = sourceRect;
            for (int i = 0; i < sizes.length; i++) {
                SceneGpuTexture next = intermediates[i];
                resolvePass(gpu, currentRef, currentWidth, currentHeight, currentRect,
                        next, true);
                currentRef = next;
                currentWidth = sizes[i];
                currentHeight = sizes[i];
                currentRect = new Rect(0f, 0f, 1f, 1f);
            }
            resolvePass(gpu, currentRef, currentWidth, currentHeight, currentRect,
                    target, downsample);
            return target;
        } catch (RuntimeException failure) {
            LOGGER.debug("While resolving the background cover", failure);
            release(gpu);
            return null;
        }
    }

    /**
     * One bilinear step from at most twice the target keeps the aliasing of a blurred background
     * cover acceptable, while a chain walked down from the (often 1024–2048 px) source allocates
     * multi-megabyte RGBA16F intermediates on every track change. With the cap the size set no
     * longer depends on the source resolution, so it also stays cacheable across songs.
     */
    private static int[] intermediateSizes(int cropSize, int targetSize) {
        int maxIntermediate = targetSize * 2;
        if (cropSize <= maxIntermediate) {
            return NO_INTERMEDIATES;
        }
        return new int[]{maxIntermediate};
    }

    /** @return false when the targets could not be (re)created. */
    private boolean ensureTargets(SceneGpuContext gpu, int[] sizes, int targetLongEdge) {
        if (owner == gpu && target != null && targetSize == targetLongEdge
                && Arrays.equals(intermediateSizeSet, sizes)) {
            return true;
        }
        release(gpu);
        owner = gpu;
        targetSize = targetLongEdge;
        intermediateSizeSet = sizes;
        try {
            SceneGpuTexture[] next = new SceneGpuTexture[sizes.length];
            intermediates = next;
            for (int i = 0; i < sizes.length; i++) {
                next[i] = createTarget(gpu, sizes[i]);
            }
            target = createTarget(gpu, targetLongEdge);
            return true;
        } catch (RuntimeException failure) {
            LOGGER.debug("While creating the resolved cover targets", failure);
            release(gpu);
            return false;
        }
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
                SceneTextureFormat.RGBA16F);
    }

    private static void resolvePass(SceneGpuContext gpu, SceneTextureRef source,
                                    int sourceWidth, int sourceHeight, Rect rect,
                                    SceneGpuTexture target, boolean downsample) {
        gpu.draw(new SceneDraw(target,
                gpu.pipeline(ScenePipelineKey.SCENE_COVER_RESOLVE, target.actualFormat()),
                List.of(new SceneSamplerBinding("Sampler0", source)),
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
        intermediateSizeSet = NO_INTERMEDIATES;
    }

    /** Releases the resolved cover targets; they are re-created lazily on the next prepare. */
    public void release() {
        SceneGpuContext current = owner;
        if (current != null) {
            release(current);
        }
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
