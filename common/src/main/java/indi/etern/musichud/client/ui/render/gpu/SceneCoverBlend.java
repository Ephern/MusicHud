package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.pipeline.SceneUniform;
import indi.etern.musichud.client.ui.render.pipeline.Std140Sizes;
import indi.etern.musichud.client.ui.render.pipeline.Std140Writer;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SceneCoverBlend {
    private static final Logger LOGGER = MusicHud.getLogger(SceneCoverBlend.class);

    private SceneGpuContext owner;
    private int width;
    private int height;
    private SceneGpuTexture target;
    /** Copy of {@link #target} from the previous frame, so a running fade can be restarted mid-way. */
    private SceneGpuTexture frozen;
    private SceneGpuContext frozenOwner;
    private int frozenWidth;
    private int frozenHeight;

    public @Nullable SceneGpuTexture apply(SceneGpuContext gpu, SceneGpuTexture previous,
                                           SceneGpuTexture current, float mix) {
        if (gpu == null || previous == null || current == null
                || current.width() < 1 || current.height() < 1) {
            return null;
        }
        if (target == null || owner != gpu || width != current.width() || height != current.height()) {
            release(gpu);
            try {
                target = gpu.createRenderTarget("MusicHud cover blend",
                        current.width(), current.height(),
                        SceneTextureFormat.RGBA16F);
                owner = gpu;
                width = current.width();
                height = current.height();
            } catch (RuntimeException failure) {
                LOGGER.debug("While creating the cover blend target", failure);
                release(gpu);
                return null;
            }
        }
        try {
            gpu.draw(new SceneDraw(target,
                    gpu.pipeline(ScenePipelineKey.SCENE_COVER_BLEND, target.actualFormat()),
                    List.of(
                            new SceneSamplerBinding("Sampler0", previous),
                            new SceneSamplerBinding("Sampler1", current)),
                    List.of(new SceneUniformBinding(BlendUniform.UBO_NAME,
                            new BlendUniform(mix)))));
            return target;
        } catch (RuntimeException failure) {
            LOGGER.debug("While blending the background cover", failure);
            release(gpu);
            return null;
        }
    }

    /**
     * Copies the mix produced by the previous frame into a separate texture. A crossfade that is
     * interrupted mid-way by another cover change restarts from exactly what is on screen, which is
     * what keeps the switch free of a visible jump.
     */
    public @Nullable SceneGpuTexture freeze(SceneGpuContext gpu) {
        if (gpu == null || target == null || owner != gpu || width < 1 || height < 1) {
            return null;
        }
        if (frozen == null || frozenOwner != gpu || frozenWidth != width || frozenHeight != height) {
            releaseFrozen();
            try {
                frozen = gpu.createRenderTarget("MusicHud cover mix", width, height,
                        SceneTextureFormat.RGBA16F);
                frozenOwner = gpu;
                frozenWidth = width;
                frozenHeight = height;
            } catch (RuntimeException failure) {
                LOGGER.debug("While creating the frozen cover mix", failure);
                releaseFrozen();
                return null;
            }
        }
        if (!SceneCover.copy(gpu, target, width, height, frozen)) {
            releaseFrozen();
            return null;
        }
        return frozen;
    }

    /** Releases the blend target; it is re-created lazily on the next blend. */
    public void release() {
        SceneGpuContext current = owner;
        if (current != null) {
            release(current);
        }
        releaseFrozen();
    }

    private void releaseFrozen() {
        if (frozen != null && frozenOwner != null) {
            SceneRetirement.retire(frozenOwner, frozen);
        }
        frozen = null;
        frozenOwner = null;
        frozenWidth = 0;
        frozenHeight = 0;
    }

    private void release(SceneGpuContext gpu) {
        if (target != null && owner == gpu) {
            SceneRetirement.retire(gpu, target);
        }
        target = null;
        owner = null;
        width = 0;
        height = 0;
    }

    private record BlendUniform(float mix) implements SceneUniform {
        private static final String UBO_NAME = "MHCoverBlend";
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
            writer.putVec4(mix, 0f, 0f, 0f);
        }

        @Override
        public boolean shouldUseBuffer(SceneUniform lastBuffered) {
            return false;
        }
    }
}
