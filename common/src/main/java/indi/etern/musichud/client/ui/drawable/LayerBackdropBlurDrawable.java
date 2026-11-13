package indi.etern.musichud.client.ui.drawable;

import icyllis.arc3d.core.Rect2i;
import icyllis.arc3d.granite.GraniteSurface;
import icyllis.arc3d.sketch.Image;
import icyllis.arc3d.sketch.Surface;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import icyllis.modernui.view.View;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.gpu.MidFrameCommit;
import indi.etern.musichud.client.ui.render.gpu.SceneBlurPainter;
import indi.etern.musichud.client.ui.render.gpu.SceneBlurSlot;
import indi.etern.musichud.client.ui.render.gpu.SceneCanvasLayer;
import indi.etern.musichud.client.ui.render.gpu.SceneOffscreen;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Blurs the UI content already recorded underneath this drawable, clipped to its rounded bounds.
 * <p>
 * Unlike {@link BackdropBlurDrawable}, which samples the screen framebuffer, this reads the current
 * recording layer, so a panel floating over other UI blurs that UI instead of the world behind it.
 * The covered layer has to be recorded already, so the owning view must be drawn after it.
 */
@SuppressWarnings("UnstableApiUsage")
public final class LayerBackdropBlurDrawable extends Drawable {
    private static final Logger LOGGER = MusicHud.getLogger(LayerBackdropBlurDrawable.class);

    private final int cornerRadius;
    private final int blurRadius;
    private final SceneBlurSlot blurSlot = new SceneBlurSlot();
    private final SceneOffscreen sourceTarget = new SceneOffscreen("musichud-layer-blur-source");
    private final SceneBlurPainter painter = new SceneBlurPainter();
    /** Device-pixel blur region; only grows, so animated bounds do not rebuild the pyramid. */
    private int regionWidth;
    private int regionHeight;
    private View view;
    private View.OnAttachStateChangeListener attachListener;
    private float alpha = 1f;
    private boolean failed;
    private boolean released;

    public LayerBackdropBlurDrawable(int cornerRadius, int blurRadius) {
        this.cornerRadius = Math.max(0, cornerRadius);
        this.blurRadius = Math.max(0, blurRadius);
    }

    /**
     * Registers the blur pipeline while the view is attached. The view must already use this
     * drawable as part of its background.
     */
    public void attachTo(@NotNull View view) {
        this.view = view;
        attachListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                blurSlot.register();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                release();
            }
        };
        view.addOnAttachStateChangeListener(attachListener);
        if (view.isAttachedToWindow()) {
            blurSlot.register();
        }
    }

    public void release() {
        if (released) {
            return;
        }
        released = true;
        if (view != null && attachListener != null) {
            view.removeOnAttachStateChangeListener(attachListener);
        }
        view = null;
        attachListener = null;
        blurSlot.unregister();
        // Both are still referenced by the previous frames' recordings and by the render thread
        // that may be executing them right now, so they are retired instead of destroyed here.
        SceneBlurSlot.retire(sourceTarget::close);
        SceneBlurSlot.retire(painter::close);
    }

    @Override
    public void setAlpha(float alpha) {
        if (this.alpha != alpha) {
            this.alpha = alpha;
            invalidateSelf();
        }
    }

    @Override
    public float getAlpha() {
        return alpha;
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (!visible) {
            // Hidden panels keep their slot registered, so the result of the last visible frame
            // would otherwise be drawn again before a fresh one is ready.
            blurSlot.discardPublished();
        }
        return changed;
    }

    @Override
    public void draw(@NotNull Canvas canvas) {
        if (released || failed || blurRadius <= 0 || alpha <= 0.001f) {
            return;
        }
        var bounds = getBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0 || !(canvas instanceof ArcCanvas arc)) {
            return;
        }
        Surface layer = SceneCanvasLayer.resolve(arc.getCanvas());
        if (!(layer instanceof GraniteSurface)) {
            return;
        }
        Rect2i device = new Rect2i();
        arc.getMatrix().mapRect(bounds.left, bounds.top, bounds.right, bounds.bottom, device);
        if (device.width() <= 0 || device.height() <= 0) {
            return;
        }
        // The blur bleeds outside the bounds, so capture a padded region when the layer allows it.
        int padding = blurRadius + 2;
        int layerWidth = layer.getWidth();
        int layerHeight = layer.getHeight();
        int needWidth = Math.min(layerWidth, device.right() - device.left() + padding * 2);
        int needHeight = Math.min(layerHeight, device.bottom() - device.top() + padding * 2);
        // Grow-only: the blur pyramid is rebuilt for every distinct size, and an animated view
        // changes it every few frames, which churns hundreds of MB/s of render targets.
        if (regionWidth < needWidth || regionHeight < needHeight) {
            regionWidth = quantize(Math.max(regionWidth, needWidth));
            regionHeight = quantize(Math.max(regionHeight, needHeight));
        }
        regionWidth = Math.min(regionWidth, layerWidth);
        regionHeight = Math.min(regionHeight, layerHeight);
        if (regionWidth <= 0 || regionHeight <= 0) {
            return;
        }
        // The region keeps its size and only moves with the view, so the source target, the blur
        // pyramid and the snapshot image are allocated once. The painter maps it 1:1 from here.
        int left = Math.clamp(device.left() - padding, 0, layerWidth - regionWidth);
        int top = Math.clamp(device.top() - padding, 0, layerHeight - regionHeight);
        int right = left + regionWidth;
        int bottom = top + regionHeight;
        try {
            sourceTarget.ensure(regionWidth, regionHeight);
            if (!sourceTarget.isReady()) {
                return;
            }
            Image snapshot = SceneCanvasLayer.snapshot(layer, left, top, right, bottom);
            if (snapshot == null) {
                return;
            }
            try {
                sourceTarget.renderImage(snapshot, regionWidth, regionHeight);
            } finally {
                snapshot.unref();
            }
            var source = SceneBlurSlot.imageOf(sourceTarget.surface());
            if (source != null) {
                blurSlot.request(source, 0f, 0f, 1f, 1f, regionWidth, regionHeight,
                        blurRadius / 3f);
            }
            MidFrameCommit.commitIfPending();
            Image blurred = blurSlot.image();
            if (blurred != null) {
                painter.draw(arc, blurred, left, top,
                        device.left(), device.top(), device.right(), device.bottom(),
                        cornerRadius, Math.clamp(alpha, 0f, 1f));
            }
        } catch (Throwable failure) {
            failed = true;
            LOGGER.error("Disabling layer backdrop blur after an error", failure);
            blurSlot.shutdown();
        }
    }

    private static int quantize(int size) {
        return Math.max(64, ((size + 63) / 64) * 64);
    }
}
