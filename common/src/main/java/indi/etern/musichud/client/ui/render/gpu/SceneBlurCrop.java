package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Sizes;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Crops the inner HUD rectangle out of a padded, blurred scene capture so downstream
 * passes can keep using full [0,1] UVs. Off-screen parts clamp to the captured edge.
 */
public final class SceneBlurCrop {
    private static final Logger LOGGER = MusicHud.getLogger(SceneBlurCrop.class);

    private SceneGpuContext owner;
    private int width;
    private int height;
    private SceneGpuTexture target;

    public @Nullable SceneGpuTexture apply(SceneGpuContext gpu, SceneGpuTexture source,
                                           float u0, float v0, float u1, float v1,
                                           int width, int height) {
        if (gpu == null || source == null || width < 1 || height < 1) {
            return null;
        }
        if (source.width() == width && source.height() == height
                && u0 <= 0f && v0 <= 0f && u1 >= 1f && v1 >= 1f) {
            return source;
        }
        if (target == null || owner != gpu || this.width != width || this.height != height) {
            release(gpu);
            try {
                target = gpu.createRenderTarget("MusicHud blur crop", width, height,
                        SceneTextureFormat.RGBA16F, SceneSamplerState.linearClamp());
                owner = gpu;
                this.width = width;
                this.height = height;
            } catch (RuntimeException failure) {
                LOGGER.debug("While creating the blur crop target", failure);
                release(gpu);
                return null;
            }
        }
        try {
            gpu.draw(new SceneDraw(target,
                    gpu.pipeline(ScenePipelineKey.SCENE_BLUR_CROP, target.format()),
                    List.of(new SceneSamplerBinding("Sampler0", source,
                            gpu.sampler(SceneSamplerState.linearClamp()))),
                    List.of(new SceneUniformBinding(CropUniform.UBO_NAME,
                            new CropUniform(u0, v0, u1, v1)))));
            return target;
        } catch (RuntimeException failure) {
            LOGGER.debug("While cropping the blurred scene", failure);
            release(gpu);
            return null;
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

    private record CropUniform(float u0, float v0, float u1, float v1) implements SceneUniform {
        private static final String UBO_NAME = "MHCrop";
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
            writer.putVec4(u0, v0, u1, v1);
        }

        @Override
        public boolean shouldUseBuffer(SceneUniform lastBuffered) {
            return false;
        }
    }
}
