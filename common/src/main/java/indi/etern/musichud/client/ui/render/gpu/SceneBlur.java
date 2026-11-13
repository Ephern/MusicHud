package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.FluidBackgroundSettings;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Sizes;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class SceneBlur {
    private static final Logger LOGGER = MusicHud.getLogger(SceneBlur.class);
    private static final int MAX_LEVELS = FluidBackgroundSettings.BLUR_MAX_LEVELS;

    private final SceneTextureFormat format;

    private SceneGpuContext owner;
    private int sourceWidth;
    private int sourceHeight;
    /**
     * Deepest level allocated for the current size. Levels are kept when the used level shrinks, so
     * a radius animation that walks through levels does not rebuild the pyramid every frame.
     */
    private int pyramidLevel;
    private SceneGpuTexture[] downsamples = new SceneGpuTexture[1];
    /** Gaussian intermediate; one per level because each level has its own size. */
    private SceneGpuTexture[] scratches = new SceneGpuTexture[1];
    private SceneGpuTexture result;
    private int resultWidth;
    private int resultHeight;

    public SceneBlur() {
        this(SceneTextureFormat.RGBA16F);
    }

    public SceneBlur(SceneTextureFormat format) {
        this.format = format;
    }

    /**
     * Pyramid level a blur of this size and sigma will use. Consumers key their target cache on it,
     * because a level change rebuilds the pyramid; the result texture is preserved.
     */
    public static int levelFor(int width, int height, float sigma) {
        if (width < 1 || height < 1 || !(sigma > 0f) || !Float.isFinite(sigma)) {
            return 0;
        }
        int level = 0;
        int levelWidth = width;
        int levelHeight = height;
        float sigmaLow = sigma;
        while (level < MAX_LEVELS && sigmaLow > 4f
                && levelWidth / 2 >= 16 && levelHeight / 2 >= 16) {
            level++;
            levelWidth /= 2;
            levelHeight /= 2;
            sigmaLow *= 0.5f;
        }
        return level;
    }

    public @Nullable PendingBlur prepare(SceneGpuContext gpu, int width, int height, float sigma) {
        return prepare(gpu, width, height, sigma, 1);
    }

    /**
     * @param resultScale power-of-two divisor applied to the result size; the pyramid runs that many
     *                    levels deeper, so a wide blur costs a fraction of the memory while the
     *                    on-screen radius stays the same
     */
    public @Nullable PendingBlur prepare(SceneGpuContext gpu, int width, int height, float sigma,
                                         int resultScale) {
        if (gpu == null || width < 1 || height < 1 || !(sigma > 0f) || !Float.isFinite(sigma)) {
            return null;
        }
        int scale = Integer.highestOneBit(Math.max(1, resultScale));
        int scaleLevel = Integer.numberOfTrailingZeros(scale);
        int resultWidth = Math.max(1, (width + scale - 1) / scale);
        int resultHeight = Math.max(1, (height + scale - 1) / scale);
        int level = scaleLevel + levelFor(resultWidth, resultHeight, sigma / scale);
        if (!targets(gpu, width, height, level)) return null;
        SceneGpuTexture scratch = scratch(gpu, level);
        if (scratch == null) return null;
        SceneGpuTexture result = result(gpu, resultWidth, resultHeight);
        if (result == null) return null;
        float sigmaLow = sigma;
        for (int i = 0; i < level; i++) {
            sigmaLow *= 0.5f;
        }
        return new PendingBlur(gpu, width, height, level, scaleLevel, sigmaLow,
                downsamples, scratch, result);
    }

    private boolean targets(SceneGpuContext gpu, int width, int height, int level) {
        boolean sameTarget = owner == gpu && sourceWidth == width && sourceHeight == height;
        if (!sameTarget) {
            releaseForResize();
            owner = gpu;
            sourceWidth = width;
            sourceHeight = height;
        }
        if (pyramidLevel >= level && downsamples.length > level) {
            return true;
        }
        int targetLevel = Math.max(level, pyramidLevel);
        try {
            if (downsamples.length <= targetLevel) {
                downsamples = Arrays.copyOf(downsamples, targetLevel + 1);
                scratches = Arrays.copyOf(scratches, targetLevel + 1);
            }
            int w = width;
            int h = height;
            for (int i = 1; i <= targetLevel; i++) {
                w = Math.max(1, w / 2);
                h = Math.max(1, h / 2);
                if (downsamples[i] == null) {
                    downsamples[i] = target(gpu, w, h);
                }
            }
            pyramidLevel = targetLevel;
            return true;
        } catch (RuntimeException failure) {
            LOGGER.debug("While creating scene blur targets", failure);
            release();
            return false;
        }
    }

    /** The result texture handed to consumers; smaller than the source when a scale is requested. */
    private @Nullable SceneGpuTexture result(SceneGpuContext gpu, int width, int height) {
        if (result != null && resultWidth == width && resultHeight == height) {
            return result;
        }
        releaseResult();
        try {
            result = target(gpu, width, height);
            resultWidth = width;
            resultHeight = height;
            return result;
        } catch (RuntimeException failure) {
            LOGGER.debug("While creating the scene blur result target", failure);
            resultWidth = 0;
            resultHeight = 0;
            return null;
        }
    }

    private void releaseResult() {
        SceneGpuContext current = owner;
        if (current != null && result != null) {
            SceneGpuTexture previous = result;
            SceneRetirement.retire(() -> current.release(previous));
        }
        result = null;
        resultWidth = 0;
        resultHeight = 0;
    }

    /** The gaussian intermediate for one level; levels are created once and kept. */
    private @Nullable SceneGpuTexture scratch(SceneGpuContext gpu, int level) {
        SceneGpuTexture existing = scratches[level];
        if (existing != null) {
            return existing;
        }
        int w = sourceWidth;
        int h = sourceHeight;
        for (int i = 0; i < level; i++) {
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
        }
        try {
            SceneGpuTexture created = target(gpu, w, h);
            scratches[level] = created;
            return created;
        } catch (RuntimeException failure) {
            LOGGER.debug("While creating the scene blur scratch target", failure);
            return null;
        }
    }

    /**
     * Releases the pyramid and the result; the owner changed size, so nothing can be reused.
     */
    private void releaseForResize() {
        releaseLevelTargets();
        releaseResult();
    }

    private void releaseLevelTargets() {
        SceneGpuContext current = owner;
        if (current != null) {
            for (int i = 1; i < downsamples.length; i++) {
                if (downsamples[i] != null) current.release(downsamples[i]);
            }
            for (SceneGpuTexture scratch : scratches) {
                if (scratch != null) current.release(scratch);
            }
        }
        downsamples = new SceneGpuTexture[1];
        scratches = new SceneGpuTexture[1];
        pyramidLevel = 0;
    }

    private SceneGpuTexture target(SceneGpuContext gpu, int width, int height) {
        return gpu.createRenderTarget("MusicHud scene blur", width, height,
                format);
    }

    public void release() {
        releaseLevelTargets();
        releaseResult();
        owner = null;
    }

    public static final class PendingBlur {
        private final SceneGpuContext gpu;
        private final int sourceWidth;
        private final int sourceHeight;
        private final int level;
        /** Pyramid levels that only exist to bring the source down to the result size. */
        private final int scaleLevel;
        private final float sigmaLow;
        private final SceneGpuTexture[] downsamples;
        private final SceneGpuTexture scratch;
        private final SceneGpuTexture result;

        private PendingBlur(SceneGpuContext gpu, int sourceWidth, int sourceHeight,
                            int level, int scaleLevel, float sigmaLow,
                            SceneGpuTexture[] downsamples,
                            SceneGpuTexture scratch, SceneGpuTexture result) {
            this.gpu = gpu;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.level = level;
            this.scaleLevel = scaleLevel;
            this.sigmaLow = sigmaLow;
            this.downsamples = downsamples;
            this.scratch = scratch;
            this.result = result;
        }

        public SceneGpuTexture target() {
            return result;
        }

        public boolean apply(SceneGpuContext gpu, SceneGpuTexture source) {
            if (source == null) {
                return false;
            }
            return apply(gpu, source, source.width(), source.height());
        }

        /**
         * @param source the texture to read; it may be a foreign (interop) texture, so the size is
         *               passed explicitly instead of read back from it
         */
        public boolean apply(SceneGpuContext gpu, SceneTextureRef source, int width, int height) {
            if (gpu != this.gpu || source == null
                    || width != sourceWidth || height != sourceHeight) {
                return false;
            }
            SceneTextureRef current = source;
            int currentWidth = width;
            int currentHeight = height;
            for (int i = 1; i <= level; i++) {
                SceneGpuTexture next = downsamples[i];
                if (!pass(gpu, ScenePipelineKey.SCENE_BLUR_DOWNSAMPLE, current, next,
                        BlurUniform.downsample(currentWidth, currentHeight))) {
                    return fallback(gpu, source, width, height);
                }
                current = next;
                currentWidth = next.width();
                currentHeight = next.height();
            }

            SceneGpuTexture verticalTarget = level == scaleLevel ? result : downsamples[level];
            if (!pass(gpu, ScenePipelineKey.SCENE_BLUR_GAUSSIAN, current, scratch,
                    BlurUniform.gaussian(currentWidth, currentHeight, 1f, 0f, sigmaLow))) {
                return fallback(gpu, source, width, height);
            }
            if (!pass(gpu, ScenePipelineKey.SCENE_BLUR_GAUSSIAN, scratch, verticalTarget,
                    BlurUniform.gaussian(scratch.width(), scratch.height(), 0f, 1f, sigmaLow))) {
                return fallback(gpu, source, width, height);
            }
            if (level == scaleLevel) return true;

            current = verticalTarget;
            currentWidth = verticalTarget.width();
            currentHeight = verticalTarget.height();
            for (int i = level - 1; i > scaleLevel; i--) {
                SceneGpuTexture next = downsamples[i];
                if (!pass(gpu, ScenePipelineKey.SCENE_BLUR_UPSAMPLE, current, next,
                        BlurUniform.upsample(currentWidth, currentHeight))) {
                    return fallback(gpu, source, width, height);
                }
                current = next;
                currentWidth = next.width();
                currentHeight = next.height();
            }
            if (!pass(gpu, ScenePipelineKey.SCENE_BLUR_UPSAMPLE, current, result,
                    BlurUniform.upsample(currentWidth, currentHeight))) {
                return fallback(gpu, source, width, height);
            }
            return true;
        }

        private boolean fallback(SceneGpuContext gpu, SceneTextureRef source,
                                 int width, int height) {
            return pass(gpu, ScenePipelineKey.SCENE_BLUR_COPY, source, result,
                    BlurUniform.copy(width, height));
        }

        private boolean pass(SceneGpuContext gpu, ScenePipelineKey key,
                             SceneTextureRef source, SceneGpuTexture target, SceneUniform uniform) {
            try {
                gpu.draw(new SceneDraw(target, gpu.pipeline(key, target.actualFormat()),
                        List.of(new SceneSamplerBinding("Sampler0", source)),
                        List.of(new SceneUniformBinding(BlurUniform.UBO_NAME, uniform))));
                return true;
            } catch (RuntimeException failure) {
                LOGGER.debug("While running scene blur pass " + key, failure);
                return false;
            }
        }
    }

    private static final class BlurUniform implements SceneUniform {
        private static final String UBO_NAME = "MHBlur";
        private static final int UBO_SIZE = Std140Sizes.calc()
                .putVec4().putVec4().get();
        private final int sourceWidth;
        private final int sourceHeight;
        private final float directionX;
        private final float directionY;
        private final float sigma;
        private final int radius;

        private BlurUniform(int sourceWidth, int sourceHeight,
                            float directionX, float directionY, float sigma, int radius) {
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.directionX = directionX;
            this.directionY = directionY;
            this.sigma = sigma;
            this.radius = radius;
        }

        private static BlurUniform downsample(int sourceWidth, int sourceHeight) {
            return new BlurUniform(sourceWidth, sourceHeight, 0f, 0f, 0f, 0);
        }

        private static BlurUniform upsample(int sourceWidth, int sourceHeight) {
            return new BlurUniform(sourceWidth, sourceHeight, 0f, 0f, 0f, 0);
        }

        private static BlurUniform copy(int sourceWidth, int sourceHeight) {
            return new BlurUniform(sourceWidth, sourceHeight, 0f, 0f, 0f, 0);
        }

        private static BlurUniform gaussian(int sourceWidth, int sourceHeight,
                                            float directionX, float directionY, float sigma) {
            float s = Math.max(0.25f, sigma);
            int radius = Math.clamp((int) Math.ceil(s * 3.0), 1, 64);
            return new BlurUniform(sourceWidth, sourceHeight, directionX, directionY, s, radius);
        }

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
            writer.putVec4(directionX, directionY, sigma, radius);
        }

        @Override
        public boolean shouldUseBuffer(SceneUniform lastBuffered) {
            return false;
        }
    }
}
