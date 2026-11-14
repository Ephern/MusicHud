package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Sizes;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** GPU resample of an artwork to the display size, cached per size. */
public final class ArtworkDownsampleCache implements AutoCloseable {
    private static final Logger LOGGER = MusicHud.getLogger(ArtworkDownsampleCache.class);
    private static final int CAPACITY = 6;
    private static final int STEP_CAPACITY = 8;
    private static final int SIZE_STEP = 4;

    private final Map<Key, SceneGpuTexture> targets = new LinkedHashMap<>(8, 0.75f, true);
    private final Map<Long, SceneGpuTexture> steps = new LinkedHashMap<>(8, 0.75f, true);
    private SceneGpuContext owner;
    private boolean closed;

    public @Nullable SceneGpuTexture get(SceneGpuContext gpu, @Nullable Object sourceKey,
                                         @Nullable SceneTextureRef source,
                                         int sourceWidth, int sourceHeight,
                                         float targetWidth, float targetHeight) {
        if (closed || gpu == null || sourceKey == null || source == null
                || sourceWidth < 1 || sourceHeight < 1) {
            return null;
        }
        int width = quantize(targetWidth);
        int height = quantize(targetHeight);
        if (width < 1 || height < 1) {
            return null;
        }
        if (owner != gpu) {
            releaseAll();
            owner = gpu;
        }
        Key key = new Key(sourceKey, width, height);
        SceneGpuTexture cached = targets.get(key);
        if (cached != null) {
            return cached;
        }
        SceneGpuTexture result = downsample(gpu, source, sourceWidth, sourceHeight, width, height);
        if (result == null) {
            return null;
        }
        targets.put(key, result);
        trimTargets();
        return result;
    }

    private @Nullable SceneGpuTexture downsample(SceneGpuContext gpu, SceneTextureRef source,
                                                 int sourceWidth, int sourceHeight,
                                                 int width, int height) {
        SceneTextureRef current = source;
        int currentWidth = sourceWidth;
        int currentHeight = sourceHeight;
        try {
            // Halving chain: each step is a 2x2 box, so the result stays an area filter.
            while (currentWidth > width * 2 || currentHeight > height * 2) {
                int nextWidth = Math.max(1, currentWidth / 2);
                int nextHeight = Math.max(1, currentHeight / 2);
                SceneGpuTexture step = step(gpu, nextWidth, nextHeight);
                if (step == null) {
                    return null;
                }
                if (!pass(gpu, step, current, currentWidth, currentHeight, nextWidth, nextHeight)) {
                    return null;
                }
                current = step;
                currentWidth = nextWidth;
                currentHeight = nextHeight;
            }
            SceneGpuTexture result = gpu.createRenderTarget("MusicHud artwork", width, height,
                    SceneTextureFormat.RGBA8);
            if (!pass(gpu, result, current, currentWidth, currentHeight, width, height)) {
                gpu.release(result);
                return null;
            }
            trimSteps();
            return result;
        } catch (RuntimeException failure) {
            LOGGER.debug("While resampling the artwork", failure);
            return null;
        }
    }

    private static boolean pass(SceneGpuContext gpu, SceneGpuTexture target, SceneTextureRef source,
                                int sourceWidth, int sourceHeight, int width, int height) {
        float spanX = Math.max(1f, sourceWidth / (float) width);
        float spanY = Math.max(1f, sourceHeight / (float) height);
        try {
            gpu.draw(new SceneDraw(target,
                    gpu.pipeline(ScenePipelineKey.SCENE_ARTWORK_DOWNSAMPLE, target.actualFormat()),
                    List.of(new SceneSamplerBinding("Sampler0", source)),
                    List.of(new SceneUniformBinding(DownsampleUniform.UBO_NAME,
                            new DownsampleUniform(1f / sourceWidth, 1f / sourceHeight, spanX, spanY)))));
            return true;
        } catch (RuntimeException failure) {
            LOGGER.debug("While running an artwork downsample pass", failure);
            return false;
        }
    }

    private @Nullable SceneGpuTexture step(SceneGpuContext gpu, int width, int height) {
        long key = ((long) width << 32) | (height & 0xFFFFFFFFL);
        SceneGpuTexture cached = steps.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            SceneGpuTexture created = gpu.createRenderTarget("MusicHud artwork step", width, height,
                    SceneTextureFormat.RGBA8);
            steps.put(key, created);
            return created;
        } catch (RuntimeException failure) {
            LOGGER.debug("While allocating an artwork downsample step", failure);
            return null;
        }
    }

    private void trimTargets() {
        while (targets.size() > CAPACITY) {
            SceneGpuTexture oldest = removeOldest(targets);
            if (oldest == null) {
                return;
            }
            if (owner != null) {
                SceneRetirement.retire(owner, oldest);
            }
        }
    }

    private void trimSteps() {
        while (steps.size() > STEP_CAPACITY) {
            SceneGpuTexture oldest = removeOldest(steps);
            if (oldest == null) {
                return;
            }
            if (owner != null) {
                SceneRetirement.retire(owner, oldest);
            }
        }
    }

    private static @Nullable SceneGpuTexture removeOldest(Map<?, SceneGpuTexture> map) {
        Iterator<SceneGpuTexture> iterator = map.values().iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        SceneGpuTexture oldest = iterator.next();
        iterator.remove();
        return oldest;
    }

    private void releaseAll() {
        SceneGpuContext context = owner;
        for (SceneGpuTexture texture : targets.values()) {
            if (context != null) {
                SceneRetirement.retire(context, texture);
            }
        }
        targets.clear();
        for (SceneGpuTexture texture : steps.values()) {
            if (context != null) {
                SceneRetirement.retire(context, texture);
            }
        }
        steps.clear();
        owner = null;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        releaseAll();
        closed = true;
    }

    private static int quantize(float size) {
        int value = Math.round(size);
        if (value <= 0) {
            return 0;
        }
        return Math.max(1, (value + SIZE_STEP - 1) / SIZE_STEP * SIZE_STEP);
    }

    private record Key(Object source, int width, int height) {
    }

    private record DownsampleUniform(float invSourceWidth, float invSourceHeight,
                                     float spanX, float spanY) implements SceneUniform {
        private static final String UBO_NAME = "MHArtworkDownsample";
        private static final int UBO_SIZE = Std140Sizes.calc().putVec4().get();

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
            writer.putVec4(invSourceWidth, invSourceHeight, spanX, spanY);
        }

        @Override
        public boolean shouldUseBuffer(SceneUniform lastBuffered) {
            return false;
        }
    }
}
