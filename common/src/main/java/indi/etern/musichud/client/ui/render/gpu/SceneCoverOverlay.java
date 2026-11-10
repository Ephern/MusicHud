package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Sizes;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SceneCoverOverlay {
    private static final Logger LOGGER = MusicHud.getLogger(SceneCoverOverlay.class);

    private SceneGpuContext owner;
    private int width;
    private int height;
    private SceneGpuTexture target;

    public @Nullable SceneGpuTexture apply(SceneGpuContext gpu, SceneGpuTexture scene,
                                           SceneGpuTexture layers, float opacity,
                                           float backgroundBlackOverlay,
                                           SceneUniform adjust, float fluidScale) {
        if (gpu == null || scene == null || layers == null || adjust == null
                || scene.width() < 1 || scene.height() < 1) {
            return null;
        }
        if (target == null || owner != gpu
                || width != scene.width() || height != scene.height()) {
            release(gpu);
            try {
                target = gpu.createRenderTarget("MusicHud cover overlay",
                        scene.width(), scene.height(),
                        SceneTextureFormat.RGBA8);
                owner = gpu;
                width = scene.width();
                height = scene.height();
            } catch (RuntimeException failure) {
                LOGGER.debug("While creating the cover overlay target", failure);
                release(gpu);
                return null;
            }
        }
        try {
            gpu.draw(new SceneDraw(target,
                    gpu.pipeline(ScenePipelineKey.SCENE_COVER_OVERLAY, target.actualFormat()),
                    List.of(
                            new SceneSamplerBinding("Sampler0", scene),
                            new SceneSamplerBinding("Sampler1", layers)),
                    List.of(
                            new SceneUniformBinding(OverlayUniform.UBO_NAME,
                                    new OverlayUniform(opacity, backgroundBlackOverlay, fluidScale)),
                            new SceneUniformBinding(adjust.getUBOName(), adjust))));
            return target;
        } catch (RuntimeException failure) {
            LOGGER.debug("While overlaying the cover layers", failure);
            release(gpu);
            return null;
        }
    }

    /** Releases the overlay target; it is re-created lazily on the next composite. */
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
        target = null;
        owner = null;
        width = 0;
        height = 0;
    }

    private record OverlayUniform(float opacity, float backgroundBlackOverlay,
                                  float fluidScale) implements SceneUniform {
        private static final String UBO_NAME = "MHOverlay";
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
            writer.putVec4(opacity, backgroundBlackOverlay, fluidScale, 0f);
        }

        @Override
        public boolean shouldUseBuffer(SceneUniform lastBuffered) {
            return false;
        }
    }
}
