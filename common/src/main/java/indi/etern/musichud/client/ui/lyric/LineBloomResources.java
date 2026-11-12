package indi.etern.musichud.client.ui.lyric;

import indi.etern.musichud.client.ui.render.gpu.SceneBlurSlot;
import indi.etern.musichud.client.ui.render.gpu.SceneOffscreen;

/**
 * Offscreen alpha mask of one lyric row's long-note bloom. The mask contains only the
 * durable-phrase overlay, so the blurred result never picks up the row's normal text.
 */
public final class LineBloomResources {
    private static final float RADIUS_EPSILON = 0.05f;
    private static final float MIN_SIGMA = 0.2f;

    private final SceneOffscreen target = new SceneOffscreen("musichud-line-bloom-src", true);
    private int padding;

    private long cachedContentStamp = Long.MIN_VALUE;
    private float cachedSigma = Float.NaN;
    private boolean closed;

    public boolean prepare(int viewWidth, int viewHeight, int padding, float sigma,
                           long contentStamp,
                           LineBlurRenderer.ContentDrawer drawer, SceneBlurSlot slot) {
        if (closed || viewWidth < 1 || viewHeight < 1 || padding < 0) {
            return false;
        }
        int width = viewWidth + 2 * padding;
        int height = viewHeight + 2 * padding;
        boolean recreated = target.ensure(width, height);
        if (!target.isReady()) {
            return false;
        }
        this.padding = padding;
        boolean contentChanged = recreated || cachedContentStamp != contentStamp;
        boolean sigmaChanged = Float.isNaN(cachedSigma)
                || Math.abs(cachedSigma - sigma) >= RADIUS_EPSILON;
        if (contentChanged) {
            target.render(padding, padding, drawer::draw);
            cachedContentStamp = contentStamp;
        }
        if (contentChanged || sigmaChanged) {
            var image = SceneBlurSlot.imageOf(target.surface());
            if (image != null) {
                slot.request(image, 0f, 0f, 1f, 1f, width, height,
                        Math.max(sigma, MIN_SIGMA));
            }
            cachedSigma = sigma;
        }
        return true;
    }

    public int padding() {
        return padding;
    }

    public void invalidate() {
        cachedContentStamp = Long.MIN_VALUE;
        cachedSigma = Float.NaN;
    }

    public int width() {
        return target.width();
    }

    public int height() {
        return target.height();
    }

    public void release() {
        target.release();
        padding = 0;
        cachedContentStamp = Long.MIN_VALUE;
        cachedSigma = Float.NaN;
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        target.close();
        padding = 0;
        cachedContentStamp = Long.MIN_VALUE;
        cachedSigma = Float.NaN;
    }
}
