package indi.etern.musichud.client.ui.lyric;

import icyllis.arc3d.opengl.GLTexture;
import indi.etern.musichud.client.ui.render.gpu.SceneBlurSlot;
import indi.etern.musichud.client.ui.render.gpu.SceneOffscreen;

/**
 * Offscreen source target of one lyric row, plus the cached state that decides when it must be
 * re-rendered and re-blurred. The blurred result itself lives in {@link SceneBlurSlot}.
 */
public final class LineBlurResources {
    private final SceneOffscreen target = new SceneOffscreen("musichud-line-blur-src");
    private int padding;

    /** Signature of the rendered content; {@link Long#MIN_VALUE} forces a re-render. */
    private long cachedContentStamp = Long.MIN_VALUE;
    /** Radius the cached source was rendered with; NaN forces a re-render. */
    private float cachedRadius = Float.NaN;

    private boolean closed;

    /**
     * Renders the row content if it changed and asks the render thread to blur it.
     *
     * @return true when the offscreen target and the deferred blur are ready for this frame
     */
    public boolean prepare(int viewWidth, int viewHeight, int padding, float radius,
                           long contentStamp,
                           LineBlurRenderer.ContentDrawer drawer, SceneBlurSlot slot) {
        if (closed || viewWidth < 1 || viewHeight < 1 || padding < 0) {
            return false;
        }
        // The padding is fixed at the row's maximum radius so the target size stays stable while the
        // radius spring animates; only sigma changes.
        int width = viewWidth + 2 * padding;
        int height = viewHeight + 2 * padding;
        boolean recreated = target.ensure(width, height);
        if (!target.isReady()) {
            return false;
        }
        this.padding = padding;
        boolean contentChanged = recreated || cachedContentStamp != contentStamp;
        boolean radiusChanged = cachedRadius != radius;
        if (contentChanged) {
            target.render(padding, padding, drawer::draw);
            cachedContentStamp = contentStamp;
        }
        if (contentChanged || radiusChanged) {
            GLTexture texture = SceneBlurSlot.textureOf(target.surface());
            if (texture != null) {
                slot.request(texture, 0f, 0f, 1f, 1f, width, height,
                        LineBlurRenderer.sigmaFor(radius));
            }
            cachedRadius = radius;
        }
        return true;
    }

    public int padding() {
        return padding;
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
        cachedRadius = Float.NaN;
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        target.close();
        padding = 0;
        cachedContentStamp = Long.MIN_VALUE;
        cachedRadius = Float.NaN;
    }
}
