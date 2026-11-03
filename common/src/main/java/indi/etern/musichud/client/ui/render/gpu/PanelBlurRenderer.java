package indi.etern.musichud.client.ui.render.gpu;

import icyllis.arc3d.core.Rect2i;
import indi.etern.musichud.MusicHud;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Blurs the main render target under the HUD config side panel.
 * <p>
 * The UI records before this runs, so the panel samples the previous frame's result. Targets are
 * therefore retired for a couple of frames instead of being released when the region changes.
 */
public final class PanelBlurRenderer {
    private static final Logger LOGGER = MusicHud.getLogger(PanelBlurRenderer.class);
    private static final int RETIRE_FRAMES = 2;

    private Pipeline pipeline;
    private final List<Retired> retired = new ArrayList<>();
    private long frame;

    public void render(SceneGpuContext gpu, SceneGpuContext.MainColorSource source) {
        HudPanelBackdrop.Region region = HudPanelBackdrop.request();
        if (gpu == null || source == null || region == null) {
            return;
        }
        frame++;
        purgeRetired();
        try {
            int padding = (int) Math.ceil(region.sigma() * 3f) + 2;
            int left = Math.max(0, region.left() - padding);
            int top = Math.max(0, region.top() - padding);
            int right = Math.min(source.width(), region.left() + region.width() + padding);
            int bottom = Math.min(source.height(), region.top() + region.height() + padding);
            int width = right - left;
            int height = bottom - top;
            if (width < 1 || height < 1) {
                return;
            }
            Pipeline current = usePipeline(width, height, region.sigma());
            SceneGpuTexture captured = current.capture.capture(gpu, source,
                    new SceneCapture.Region(left, top, width, height), 1);
            if (captured == null) {
                return;
            }
            SceneBlur.PendingBlur pending = current.blur.prepare(gpu,
                    captured.width(), captured.height(), region.sigma());
            if (pending == null || !pending.apply(gpu, captured)) {
                return;
            }
            HudPanelBackdrop.publish(pending.target(), new Rect2i(left, top, right, bottom));
        } catch (RuntimeException failure) {
            LOGGER.debug("While blurring the HUD config panel backdrop", failure);
        }
    }

    private Pipeline usePipeline(int width, int height, float sigma) {
        int level = SceneBlur.levelFor(width, height, sigma);
        Pipeline current = pipeline;
        if (current != null && current.width == width && current.height == height
                && current.level == level) {
            return current;
        }
        if (current != null) {
            retired.add(new Retired(frame, current));
        }
        pipeline = new Pipeline(width, height, level);
        return pipeline;
    }

    private void purgeRetired() {
        for (Iterator<Retired> iterator = retired.iterator(); iterator.hasNext(); ) {
            Retired entry = iterator.next();
            if (frame - entry.frame >= RETIRE_FRAMES) {
                entry.pipeline.release();
                iterator.remove();
            }
        }
    }

    public void release() {
        if (pipeline != null) {
            pipeline.release();
            pipeline = null;
        }
        for (Retired entry : retired) {
            entry.pipeline.release();
        }
        retired.clear();
    }

    private static final class Pipeline {
        final int width;
        final int height;
        final int level;
        final SceneCapture capture = new SceneCapture();
        final SceneBlur blur = new SceneBlur(SceneTextureFormat.RGBA8);

        Pipeline(int width, int height, int level) {
            this.width = width;
            this.height = height;
            this.level = level;
        }

        void release() {
            capture.release();
            blur.release();
        }
    }

    private record Retired(long frame, Pipeline pipeline) {
    }
}
