package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Sizes;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SceneBlur {
    private static final Logger LOGGER = MusicHud.getLogger(SceneBlur.class);
    private static final int MAX_LEVELS = 4;

    private SceneGpuContext owner;
    private int sourceWidth;
    private int sourceHeight;
    private int level;
    private SceneGpuTexture[] downsamples = new SceneGpuTexture[1];
    private SceneGpuTexture scratch;
    private SceneGpuTexture result;

    public @Nullable PendingBlur prepare(SceneGpuContext gpu, int width, int height, float sigma) {
        if (gpu == null || width < 1 || height < 1 || !(sigma > 0f) || !Float.isFinite(sigma)) {
            return null;
        }
        int level = 0;
        int levelWidth = width;
        int levelHeight = height;
        float sigmaLow = sigma;
        while (level < MAX_LEVELS && sigmaLow > 4f
                && levelWidth / 2 >= 8 && levelHeight / 2 >= 8) {
            level++;
            levelWidth /= 2;
            levelHeight /= 2;
            sigmaLow *= 0.5f;
        }
        if (!targets(gpu, width, height, level)) return null;
        return new PendingBlur(gpu, width, height, level, sigmaLow,
                downsamples, scratch, result);
    }

    private boolean targets(SceneGpuContext gpu, int width, int height, int level) {
        if (owner == gpu && sourceWidth == width && sourceHeight == height && this.level == level
                && result != null && scratch != null) {
            return true;
        }
        release();
        owner = gpu;
        sourceWidth = width;
        sourceHeight = height;
        this.level = level;
        try {
            downsamples = new SceneGpuTexture[level + 1];
            int w = width;
            int h = height;
            for (int i = 1; i <= level; i++) {
                w = Math.max(1, w / 2);
                h = Math.max(1, h / 2);
                downsamples[i] = target(gpu, w, h);
            }
            scratch = target(gpu, w, h);
            result = target(gpu, width, height);
            return true;
        } catch (RuntimeException failure) {
            LOGGER.debug("While creating scene blur targets", failure);
            release();
            return false;
        }
    }

    private static SceneGpuTexture target(SceneGpuContext gpu, int width, int height) {
        return gpu.createRenderTarget("MusicHud scene blur", width, height,
                SceneTextureFormat.RGBA8, SceneSamplerState.linearClamp());
    }

    private void release() {
        SceneGpuContext current = owner;
        if (current != null) {
            for (int i = 1; i < downsamples.length; i++) {
                if (downsamples[i] != null) current.release(downsamples[i]);
            }
            if (scratch != null) current.release(scratch);
            if (result != null) current.release(result);
        }
        downsamples = new SceneGpuTexture[1];
        scratch = null;
        result = null;
        owner = null;
    }

    public static final class PendingBlur {
        private final SceneGpuContext gpu;
        private final int sourceWidth;
        private final int sourceHeight;
        private final int level;
        private final float sigmaLow;
        private final SceneGpuTexture[] downsamples;
        private final SceneGpuTexture scratch;
        private final SceneGpuTexture result;

        private PendingBlur(SceneGpuContext gpu, int sourceWidth, int sourceHeight,
                            int level, float sigmaLow, SceneGpuTexture[] downsamples,
                            SceneGpuTexture scratch, SceneGpuTexture result) {
            this.gpu = gpu;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.level = level;
            this.sigmaLow = sigmaLow;
            this.downsamples = downsamples;
            this.scratch = scratch;
            this.result = result;
        }

        public SceneGpuTexture target() {
            return result;
        }

        public boolean apply(SceneGpuContext gpu, SceneGpuTexture source) {
            if (gpu != this.gpu || source == null
                    || source.width() != sourceWidth || source.height() != sourceHeight) {
                return false;
            }
            SceneGpuTexture current = source;
            for (int i = 1; i <= level; i++) {
                SceneGpuTexture next = downsamples[i];
                if (!pass(gpu, ScenePipelineKey.SCENE_BLUR_DOWNSAMPLE, current, next,
                        BlurUniform.downsample(current.width(), current.height()))) {
                    return fallback(gpu, source);
                }
                current = next;
            }

            SceneGpuTexture verticalTarget = level == 0 ? result : current;
            if (!pass(gpu, ScenePipelineKey.SCENE_BLUR_GAUSSIAN, current, scratch,
                    BlurUniform.gaussian(current.width(), current.height(), 1f, 0f, sigmaLow))) {
                return fallback(gpu, source);
            }
            if (!pass(gpu, ScenePipelineKey.SCENE_BLUR_GAUSSIAN, scratch, verticalTarget,
                    BlurUniform.gaussian(scratch.width(), scratch.height(), 0f, 1f, sigmaLow))) {
                return fallback(gpu, source);
            }
            if (level == 0) return true;

            for (int i = level - 1; i >= 1; i--) {
                SceneGpuTexture next = downsamples[i];
                if (!pass(gpu, ScenePipelineKey.SCENE_BLUR_UPSAMPLE, current, next,
                        BlurUniform.upsample(current.width(), current.height()))) {
                    return fallback(gpu, source);
                }
                current = next;
            }
            if (!pass(gpu, ScenePipelineKey.SCENE_BLUR_UPSAMPLE, current, result,
                    BlurUniform.upsample(current.width(), current.height()))) {
                return fallback(gpu, source);
            }
            return true;
        }

        private boolean fallback(SceneGpuContext gpu, SceneGpuTexture source) {
            return pass(gpu, ScenePipelineKey.SCENE_BLUR_COPY, source, result,
                    BlurUniform.copy(source.width(), source.height()));
        }

        private boolean pass(SceneGpuContext gpu, ScenePipelineKey key,
                             SceneGpuTexture source, SceneGpuTexture target, SceneUniform uniform) {
            try {
                gpu.draw(new SceneDraw(target, gpu.pipeline(key, target.format()),
                        List.of(new SceneSamplerBinding("Sampler0", source,
                                gpu.sampler(SceneSamplerState.linearClamp()))),
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
