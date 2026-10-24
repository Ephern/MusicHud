package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.CoverWarpSettings;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Applies an FFD control-grid warp to the composited cover layers.
 * <p>
 * The warp target is cached while the source, grid, offsets and transition state stay
 * unchanged. A 9x9 grid is the maximum; every pixel still evaluates only a 4x4
 * Catmull-Rom neighborhood. In normal rendering this pass runs after the three cover
 * layers have been composited, so all layers are warped together.
 */
public final class SceneCoverWarp {
    private static final Logger LOGGER = MusicHud.getLogger(SceneCoverWarp.class);
    private static final int MAX_CONTROL_POINTS = CoverWarpSettings.MAX_CONTROL_POINTS;

    private SceneGpuContext owner;
    private SceneGpuTexture source;
    private SceneGpuTexture target;
    private int width;
    private int height;
    private long revision = Long.MIN_VALUE;

    public SceneGpuTexture apply(SceneGpuContext gpu, SceneGpuTexture cover,
                                 CoverWarpSettings.Snapshot settings, boolean animated) {
        if (gpu == null || cover == null || settings == null || !settings.enabled()
                || settings.gridSize() < 2 || settings.strength() <= 1e-4f
                || !hasOffsets(settings.offsets())) {
            return cover;
        }
        boolean sizeChanged = target == null || owner != gpu
                || width != cover.width() || height != cover.height();
        if (sizeChanged) {
            release(gpu);
            try {
                target = gpu.createRenderTarget("MusicHud cover warp",
                        cover.width(), cover.height(),
                        SceneTextureFormat.RGBA8, SceneSamplerState.linearClamp());
                owner = gpu;
                width = cover.width();
                height = cover.height();
            } catch (RuntimeException failure) {
                LOGGER.debug("While creating the cover warp target", failure);
                release(gpu);
                return cover;
            }
        }
        if (!animated && source == cover && revision == settings.revision()) {
            return target;
        }
        try {
            gpu.draw(new SceneDraw(target,
                    gpu.pipeline(ScenePipelineKey.SCENE_COVER_WARP, target.format()),
                    List.of(new SceneSamplerBinding("Sampler0", cover,
                            gpu.sampler(SceneSamplerState.linearClamp()))),
                    List.of(new SceneUniformBinding(WarpUniform.UBO_NAME,
                            new WarpUniform(settings)))));
            source = cover;
            revision = settings.revision();
            return target;
        } catch (RuntimeException failure) {
            LOGGER.debug("While warping the background cover", failure);
            release(gpu);
            return cover;
        }
    }

    private void release(SceneGpuContext gpu) {
        if (target != null && owner == gpu) {
            gpu.release(target);
        }
        target = null;
        source = null;
        owner = null;
        width = 0;
        height = 0;
        revision = Long.MIN_VALUE;
    }

    private static boolean hasOffsets(float[] offsets) {
        for (float offset : offsets) {
            if (Math.abs(offset) > 1e-6f) {
                return true;
            }
        }
        return false;
    }

    private record WarpUniform(int gridSize, float strength, float[] offsets) implements SceneUniform {
        private static final String UBO_NAME = "MHCoverWarp";
        private static final int UBO_SIZE = (1 + MAX_CONTROL_POINTS) * 16;

        private WarpUniform(CoverWarpSettings.Snapshot settings) {
            this(settings.gridSize(), settings.strength(), settings.offsets());
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
            writer.putVec4(gridSize, 1f, strength, 0f);
            for (int i = 0; i < MAX_CONTROL_POINTS; i++) {
                int index = i * 2;
                float x = index < offsets.length ? offsets[index] : 0f;
                float y = index + 1 < offsets.length ? offsets[index + 1] : 0f;
                writer.putVec4(x, y, 0f, 0f);
            }
        }

        @Override
        public boolean shouldUseBuffer(SceneUniform lastBuffered) {
            return false;
        }
    }
}
