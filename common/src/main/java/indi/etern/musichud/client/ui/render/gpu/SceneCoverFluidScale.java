package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Sizes;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Applies the audio-reactive zoom to the final fluid layer, after blur and warp.
 * The pass is skipped entirely when the scale is effectively 1.
 */
public final class SceneCoverFluidScale {
    private static final Logger LOGGER = MusicHud.getLogger(SceneCoverFluidScale.class);

    private SceneGpuContext owner;
    private int width;
    private int height;
    private SceneGpuTexture target;

    public SceneGpuTexture apply(SceneGpuContext gpu, SceneGpuTexture source, float scale) {
        if (gpu == null || source == null || !(scale > 1.0001f)) {
            return source;
        }
        if (target == null || owner != gpu
                || width != source.width() || height != source.height()) {
            release(gpu);
            try {
                target = gpu.createRenderTarget("MusicHud fluid scale",
                        source.width(), source.height(),
                        SceneTextureFormat.RGBA8, SceneSamplerState.linearClamp());
                owner = gpu;
                width = source.width();
                height = source.height();
            } catch (RuntimeException failure) {
                LOGGER.debug("While creating the fluid scale target", failure);
                release(gpu);
                return source;
            }
        }
        try {
            gpu.draw(new SceneDraw(target,
                    gpu.pipeline(ScenePipelineKey.SCENE_COVER_FLUID_SCALE, target.format()),
                    List.of(new SceneSamplerBinding("Sampler0", source,
                            gpu.sampler(SceneSamplerState.linearClamp()))),
                    List.of(new SceneUniformBinding(ScaleUniform.UBO_NAME,
                            new ScaleUniform(scale)))));
            return target;
        } catch (RuntimeException failure) {
            LOGGER.debug("While scaling the fluid layer", failure);
            release(gpu);
            return source;
        }
    }

    private void release(SceneGpuContext gpu) {
        if (target != null && owner == gpu) {
            gpu.release(target);
        }
        target = null;
        owner = null;
        width = 0;
        height = 0;
    }

    private record ScaleUniform(float scale) implements SceneUniform {
        private static final String UBO_NAME = "MHFluidScale";
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
            writer.putVec4(scale, 0f, 0f, 0f);
        }

        @Override
        public boolean shouldUseBuffer(SceneUniform lastBuffered) {
            return false;
        }
    }
}
