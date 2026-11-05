package indi.etern.musichud.client.ui.drawable;

import icyllis.arc3d.core.Rect2i;
import icyllis.arc3d.sketch.Image;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import icyllis.modernui.view.View;
import indi.etern.musichud.client.ui.render.gpu.HudPanelBackdrop;
import indi.etern.musichud.client.ui.render.gpu.SceneBlurPainter;
import indi.etern.musichud.client.ui.render.gpu.SceneBlurSlot;
import org.jetbrains.annotations.NotNull;

/**
 * Blurs whatever is behind this drawable (the main render target), clipped to its rounded bounds.
 * Meant to sit under a translucent panel that supplies the tint.
 */
@SuppressWarnings("UnstableApiUsage")
public final class BackdropBlurDrawable extends Drawable {
    private final int cornerRadius;
    private final int blurRadius;
    private final SceneBlurPainter painter = new SceneBlurPainter();

    private View view;
    private View.OnAttachStateChangeListener attachListener;
    private boolean released;

    public BackdropBlurDrawable(int cornerRadius, int blurRadius) {
        this.cornerRadius = Math.max(0, cornerRadius);
        this.blurRadius = Math.max(0, blurRadius);
    }

    /** Releases the blur when the view detaches. The view must already use this drawable. */
    public void attachTo(@NotNull View view) {
        this.view = view;
        attachListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                release();
            }
        };
        view.addOnAttachStateChangeListener(attachListener);
    }

    @Override
    public void draw(@NotNull Canvas canvas) {
        if (released || blurRadius <= 0 || !(canvas instanceof ArcCanvas arc)) {
            return;
        }
        var bounds = getBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        Rect2i device = new Rect2i();
        arc.getMatrix().mapRect(bounds.left, bounds.top, bounds.right, bounds.bottom, device);
        if (device.width() <= 0 || device.height() <= 0) {
            return;
        }
        float sigma = blurRadius / 3f;
        HudPanelBackdrop.request(device.left(), device.top(),
                device.width(), device.height(), cornerRadius, sigma);
        Image blurred = HudPanelBackdrop.image();
        Rect2i covered = HudPanelBackdrop.covered();
        if (blurred == null || covered == null) {
            return;
        }
        painter.draw(arc, blurred, covered.left(), covered.top(),
                device.left(), device.top(), device.right(), device.bottom(),
                cornerRadius, Math.clamp(getAlpha(), 0f, 1f));
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        invalidateSelf();
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
        SceneBlurSlot.retire(painter::close);
        HudPanelBackdrop.clear();
    }
}
