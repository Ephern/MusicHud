package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.MusicHud;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Owns the texture the sampled backdrop element samples, and the copy that fills it.
 * <p>
 * The GUI is extracted before the world is drawn, so the chain that composes the backdrop is deferred
 * (see {@link SceneGpuContext#deferBackdrop}) and only writes this texture while the GUI is submitted.
 */
public final class DeferredBackdrop {
    private static final Logger LOGGER = MusicHud.getLogger(DeferredBackdrop.class);

    private SceneGpuContext owner;
    private SceneGpuTexture output;
    private int width;
    private int height;

    /** Returns the stable texture the background element samples, creating it when the size changes. */
    public @Nullable SceneGpuTexture reserve(SceneGpuContext gpu, int width, int height) {
        if (width < 1 || height < 1) return null;
        if (output != null && owner == gpu && this.width == width && this.height == height) {
            return output;
        }
        release();
        try {
            output = gpu.createRenderTarget("MusicHud backdrop", width, height,
                    SceneTextureFormat.RGBA8);
            owner = gpu;
            this.width = width;
            this.height = height;
            try {
                gpu.clear(output, 0);
            } catch (RuntimeException ignored) {
            }
            return output;
        } catch (RuntimeException failure) {
            LOGGER.debug("While creating the backdrop target", failure);
            release();
            return null;
        }
    }

    /** Copies the chain result into the published texture, keeping its rows and alpha. */
    public void copy(SceneGpuContext gpu, SceneGpuTexture source) {
        if (output == null || owner != gpu || source == null) return;
        gpu.draw(new SceneDraw(output,
                gpu.pipeline(ScenePipelineKey.SCENE_COPY, output.actualFormat()),
                List.of(new SceneSamplerBinding("Sampler0", source)),
                List.of()));
    }

    public void release() {
        SceneGpuContext previousOwner = owner;
        SceneGpuTexture previousOutput = output;
        output = null;
        owner = null;
        width = 0;
        height = 0;
        if (previousOutput != null && previousOwner != null) {
            SceneRetirement.retire(() -> previousOwner.release(previousOutput));
        }
    }
}
