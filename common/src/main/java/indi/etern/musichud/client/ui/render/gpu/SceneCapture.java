package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Sizes;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Reusable crop-and-resample capture of the main color target into an owned offscreen target.
 * <p>
 * The source crop is stored top-down in the target: target row {@code v = 0} contains the top
 * of the crop, matching the GUI texture UV convention used by the final display shader.
 */
public final class SceneCapture {
    private static final Logger LOGGER = MusicHud.getLogger(SceneCapture.class);

    private SceneGpuContext owner;
    private SceneGpuTexture target;
    private int targetWidth;
    private int targetHeight;

    public record Region(float left, float top, float width, float height) {
    }

    public @Nullable SceneGpuTexture capture(SceneGpuContext gpu,
                                             SceneGpuContext.MainColorSource source,
                                             Region region) {
        PendingCapture pending = prepare(gpu, source, region);
        if (pending == null || !pending.fill()) return null;
        return pending.target();
    }

    public @Nullable PendingCapture prepare(SceneGpuContext gpu,
                                            SceneGpuContext.MainColorSource source,
                                            Region region) {
        if (source == null || source.texture() == null || source.width() <= 0 || source.height() <= 0) {
            return null;
        }
        if (region.width() <= 0f || region.height() <= 0f) return null;
        int width = Math.max(1, (int) Math.ceil(region.width()));
        int height = Math.max(1, (int) Math.ceil(region.height()));
        SceneGpuTexture output = target(gpu, width, height);
        if (output == null) return null;
        try {
            gpu.clear(output, 0);
        } catch (RuntimeException failure) {
            LOGGER.debug("While clearing a scene capture target", failure);
            return null;
        }
        CaptureUniform uniform = new CaptureUniform(region.left(), region.top(),
                region.width(), region.height(), source.width(), source.height());
        return new PendingCapture(gpu, output, source.texture(), uniform);
    }

    private @Nullable SceneGpuTexture target(SceneGpuContext gpu, int width, int height) {
        if (target != null && owner == gpu && targetWidth == width && targetHeight == height) {
            return target;
        }
        if (target != null) {
            if (owner == gpu) gpu.release(target);
            target = null;
            owner = null;
            targetWidth = 0;
            targetHeight = 0;
        }
        try {
            target = gpu.createRenderTarget("MusicHud scene capture", width, height,
                    SceneTextureFormat.RGBA8, SceneSamplerState.linearClamp());
            owner = gpu;
            targetWidth = width;
            targetHeight = height;
            return target;
        } catch (RuntimeException failure) {
            LOGGER.debug("While creating a scene capture target of " + width + "x" + height, failure);
            target = null;
            owner = null;
            targetWidth = 0;
            targetHeight = 0;
            return null;
        }
    }

    public static final class PendingCapture {
        private final SceneGpuContext gpu;
        private final SceneGpuTexture target;
        private final SceneTextureRef source;
        private final CaptureUniform uniform;
        private boolean filled;

        private PendingCapture(SceneGpuContext gpu, SceneGpuTexture target,
                               SceneTextureRef source, CaptureUniform uniform) {
            this.gpu = gpu;
            this.target = target;
            this.source = source;
            this.uniform = uniform;
        }

        public SceneGpuTexture target() {
            return target;
        }

        public boolean fill() {
            if (filled) return true;
            try {
                gpu.draw(new SceneDraw(target, gpu.pipeline(ScenePipelineKey.SCENE_CAPTURE, target.format()),
                        List.of(new SceneSamplerBinding("Sampler0", source,
                                gpu.sampler(SceneSamplerState.linearClamp()))),
                        List.of(new SceneUniformBinding(CaptureUniform.UBO_NAME, uniform))));
                filled = true;
                return true;
            } catch (RuntimeException failure) {
                LOGGER.debug("While capturing the scene color target", failure);
                try {
                    gpu.clear(target, 0);
                } catch (RuntimeException ignored) {
                }
                return false;
            }
        }
    }

    private record CaptureUniform(float left, float top, float width, float height,
                                  int sourceWidth, int sourceHeight) implements SceneUniform {
        private static final String UBO_NAME = "MHCapture";
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
            writer.putVec4(left, top, width, height);
            writer.putVec4(sourceWidth, sourceHeight, 0f, 0f);
        }

        @Override
        public boolean shouldUseBuffer(SceneUniform lastBuffered) {
            return false;
        }
    }
}
