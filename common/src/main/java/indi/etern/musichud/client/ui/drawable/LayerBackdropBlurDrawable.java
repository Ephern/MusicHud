package indi.etern.musichud.client.ui.drawable;

import icyllis.arc3d.core.Rect2i;
import icyllis.arc3d.granite.GraniteSurface;
import icyllis.arc3d.sketch.Image;
import icyllis.arc3d.sketch.NWayCanvas;
import icyllis.arc3d.sketch.Surface;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import icyllis.modernui.view.View;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.gpu.SceneBlurPainter;
import indi.etern.musichud.client.ui.render.gpu.SceneBlurSlot;
import indi.etern.musichud.client.ui.render.gpu.SceneOffscreen;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

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
    /** {@code NWayCanvas.mList}; the recording target behind a filtered canvas. */
    private static final Field NWAY_CHILDREN = lookupNwayChildren();
    /** Largest opacity difference between the captured backdrop and the current frame. */
    private static final float ALPHA_TOLERANCE = 0.1f;

    private final int cornerRadius;
    private final int blurRadius;
    private final SceneBlurSlot blurSlot = new SceneBlurSlot();
    private final SceneOffscreen sourceTarget = new SceneOffscreen("musichud-layer-blur-source");
    private final SceneBlurPainter painter = new SceneBlurPainter();

    private View view;
    private View.OnAttachStateChangeListener attachListener;
    private boolean failed;
    private boolean released;
    /** Opacity the last captured backdrop was drawn at. */
    private float capturedAlpha = 1f;

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
        sourceTarget.close();
        painter.close();
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (visible && changed) {
            // After being hidden the published texture is from the previous visible session; a
            // fresh appearance must not composite it (its colours belong to a different frame).
            blurSlot.invalidatePublished();
        }
        return changed;
    }

    @Override
    public void draw(@NotNull Canvas canvas) {
        if (released || failed || blurRadius <= 0) {
            return;
        }
        var bounds = getBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0 || !(canvas instanceof ArcCanvas arc)) {
            return;
        }
        Surface layer = resolveLayer(arc.getCanvas());
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
        int left = Math.max(0, device.left() - padding);
        int top = Math.max(0, device.top() - padding);
        int right = Math.min(layer.getWidth(), device.right() + padding);
        int bottom = Math.min(layer.getHeight(), device.bottom() + padding);
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) {
            return;
        }
        float alpha = effectiveAlpha();
        try {
            sourceTarget.ensure(width, height);
            if (!sourceTarget.isReady()) {
                return;
            }
            Image snapshot = layer.makeImageSnapshot(new Rect2i(left, top, right, bottom));
            if (snapshot == null) {
                return;
            }
            try {
                sourceTarget.renderImage(snapshot, width, height);
            } finally {
                snapshot.unref();
            }
            var source = SceneBlurSlot.imageOf(sourceTarget.surface());
            if (source != null) {
                blurSlot.request(source, 0f, 0f, 1f, 1f, width, height, blurRadius / 3f);
            }
            // The published result is from the previous frame's capture. Reusing it across a fast
            // opacity ramp composites colours that belong to a (nearly) invisible backdrop.
            Image blurred = blurSlot.image();
            boolean usable = blurred != null && Math.abs(alpha - capturedAlpha) <= ALPHA_TOLERANCE;
            capturedAlpha = alpha;
            if (usable) {
                painter.draw(arc, blurred, left, top,
                        device.left(), device.top(), device.right(), device.bottom(),
                        cornerRadius, Math.clamp(getAlpha(), 0f, 1f));
            }
        } catch (Throwable failure) {
            failed = true;
            LOGGER.error("Disabling layer backdrop blur after an error", failure);
            blurSlot.shutdown();
        }
    }

    /**
     * Combined opacity of the owning view and its ancestors. Any ancestor below 1 makes the canvas
     * an alpha filter, so the captured backdrop is drawn at this opacity.
     */
    private float effectiveAlpha() {
        float alpha = 1f;
        View current = view;
        while (current != null) {
            alpha *= current.getAlpha();
            var parent = current.getParent();
            current = parent instanceof View parentView ? parentView : null;
        }
        return alpha;
    }

    /**
     * Resolves the surface that owns the recording. An alpha-animated ancestor wraps the canvas in
     * an {@code AlphaFilterCanvas}, which owns no surface; the recording target is the single
     * proxied canvas inside it.
     */
    private static @Nullable Surface resolveLayer(icyllis.arc3d.sketch.Canvas canvas) {
        Surface surface = canvas.getSurface();
        if (surface != null) {
            return surface;
        }
        if (NWAY_CHILDREN == null || !(canvas instanceof NWayCanvas way)) {
            return null;
        }
        try {
            icyllis.arc3d.sketch.Canvas[] children = (icyllis.arc3d.sketch.Canvas[]) NWAY_CHILDREN.get(way);
            if (children != null && children.length > 0 && children[0] != null) {
                return resolveLayer(children[0]);
            }
        } catch (Throwable ignored) {
            // Falls back to the tint-only draw.
        }
        return null;
    }

    private static @Nullable Field lookupNwayChildren() {
        try {
            Field field = NWayCanvas.class.getDeclaredField("mList");
            field.setAccessible(true);
            return field;
        } catch (Throwable failure) {
            LOGGER.warn("Layer backdrop blur disabled while canvases are alpha-filtered", failure);
            return null;
        }
    }
}
