package indi.etern.musichud.client.ui.components.modals;

import icyllis.arc3d.core.Matrix4;
import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.core.Rect2i;
import icyllis.arc3d.core.SamplingOptions;
import icyllis.arc3d.granite.GraniteSurface;
import icyllis.arc3d.sketch.Image;
import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.Surface;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.gpu.MidFrameCommit;
import indi.etern.musichud.client.ui.render.gpu.SceneBlurPainter;
import indi.etern.musichud.client.ui.render.gpu.SceneBlurSlot;
import indi.etern.musichud.client.ui.render.gpu.SceneCanvasLayer;
import indi.etern.musichud.client.ui.render.gpu.SceneOffscreen;
import org.apache.logging.log4j.Logger;

/**
 * Mutable content of the modal popup: the dim layer plus the card. The card is rendered into its own
 * surface, so the backdrop can be blurred and composited between the dim and the card.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ModalOverlay extends FrameLayout {
    private static final Logger LOGGER = MusicHud.getLogger(ModalOverlay.class);
    private static final Matrix4 IDENTITY = new Matrix4();
    private static final int SHADOW_MARGIN_DP = 16;

    private final View dimView;
    private final View card;
    private final int dimColor;
    private final int cornerRadiusPx;
    private final int blurRadiusPx;
    private final int shadowMarginPx;
    private final SceneBlurSlot blurSlot = new SceneBlurSlot();
    private final SceneOffscreen cardTarget = new SceneOffscreen("musichud-modal-card");
    private final SceneOffscreen backdropTarget = new SceneOffscreen("musichud-modal-backdrop");
    private final SceneBlurPainter blurPainter = new SceneBlurPainter();
    private final Paint dimPaint = new Paint();
    private boolean failed;

    public ModalOverlay(Context context, View dimView, View card, int dimColor,
                        int cornerRadiusPx, int blurRadiusPx) {
        super(context);
        this.dimView = dimView;
        this.card = card;
        this.dimColor = dimColor;
        this.cornerRadiusPx = Math.max(0, cornerRadiusPx);
        this.blurRadiusPx = Math.max(0, blurRadiusPx);
        this.shadowMarginPx = dp(SHADOW_MARGIN_DP);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        blurSlot.register();
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseResources();
        super.onDetachedFromWindow();
    }

    private void releaseResources() {
        blurSlot.unregister();
        cardTarget.close();
        backdropTarget.close();
        blurPainter.close();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (failed || blurRadiusPx <= 0 || blurSlot.isDisabled() || !(canvas instanceof ArcCanvas arc)
                || card.getWidth() <= 0 || card.getHeight() <= 0
                || !arc.getMatrix().isIdentity()) {
            super.dispatchDraw(canvas);
            return;
        }
        Surface layer = SceneCanvasLayer.resolve(arc.getCanvas());
        if (!(layer instanceof GraniteSurface)) {
            super.dispatchDraw(canvas);
            return;
        }
        int margin = shadowMarginPx;
        cardTarget.ensure(card.getWidth() + 2 * margin, card.getHeight() + 2 * margin);
        if (!cardTarget.isReady()) {
            super.dispatchDraw(canvas);
            return;
        }
        try {
            drawDim(arc);
            Image cardImage = cardTarget.renderSnapshot(margin - card.getLeft(),
                    margin - card.getTop(), c -> drawChild(c, card, 0));
            if (cardImage == null) {
                super.dispatchDraw(canvas);
                return;
            }
            try {
                drawComposite(arc, layer, cardImage);
            } finally {
                cardImage.unref();
            }
        } catch (Throwable failure) {
            failed = true;
            LOGGER.error("Disabling modal backdrop blur after an error", failure);
            blurSlot.shutdown();
            super.dispatchDraw(canvas);
        }
    }

    /** The scrim is a uniform colour, so it is drawn directly instead of through the child path. */
    private void drawDim(ArcCanvas arc) {
        float viewAlpha = dimView.getAlpha();
        if (dimView.getVisibility() != View.VISIBLE || viewAlpha <= 0.001f) {
            return;
        }
        int alpha = Math.clamp(Math.round(((dimColor >>> 24) & 0xFF) * viewAlpha), 0, 255);
        if (alpha == 0) {
            return;
        }
        dimPaint.setColor((alpha << 24) | (dimColor & 0xFFFFFF));
        dimPaint.setShader(null);
        dimPaint.setBlendMode(null);
        var nativeCanvas = arc.getCanvas();
        nativeCanvas.save();
        nativeCanvas.setMatrix(IDENTITY);
        nativeCanvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);
        nativeCanvas.restore();
    }

    private void drawComposite(ArcCanvas arc, Surface layer, Image cardImage) {
        Rect2i device = new Rect2i();
        arc.getMatrix().mapRect(card.getLeft(), card.getTop(), card.getRight(), card.getBottom(),
                device);
        if (device.width() <= 0 || device.height() <= 0) {
            return;
        }
        int padding = blurRadiusPx + 2;
        int snapLeft = Math.max(0, device.left() - padding);
        int snapTop = Math.max(0, device.top() - padding);
        int snapRight = Math.min(layer.getWidth(), device.right() + padding);
        int snapBottom = Math.min(layer.getHeight(), device.bottom() + padding);
        int snapWidth = snapRight - snapLeft;
        int snapHeight = snapBottom - snapTop;
        if (snapWidth > 0 && snapHeight > 0) {
            // Copy the layer below the card into our own target: the card is composited later in this
            // same recording, so the backdrop must be captured before it.
            backdropTarget.ensure(snapWidth, snapHeight);
            if (backdropTarget.isReady()) {
                Image snapshot = SceneCanvasLayer.snapshot(layer,
                        snapLeft, snapTop, snapRight, snapBottom);
                if (snapshot != null) {
                    try {
                        backdropTarget.renderImage(snapshot, snapWidth, snapHeight);
                    } finally {
                        snapshot.unref();
                    }
                    var source = SceneBlurSlot.imageOf(backdropTarget.surface());
                    if (source != null) {
                        blurSlot.request(source, 0f, 0f, 1f, 1f, snapWidth, snapHeight,
                                blurRadiusPx / 3f);
                    }
                }
            }
            // The blur runs between the partial and main submissions, so the composite below sees
            // this frame's result.
            MidFrameCommit.commitIfPending();
            Image blurred = blurSlot.image();
            if (blurred != null) {
                // The backdrop is part of the card, so it has to fade with it; otherwise it pops in
                // at full strength while the card is still transparent.
                blurPainter.draw(arc, blurred, snapLeft, snapTop,
                        device.left(), device.top(), device.right(), device.bottom(),
                        cornerRadiusPx, Math.clamp(card.getAlpha(), 0f, 1f));
            }
        }
        var nativeCanvas = arc.getCanvas();
        nativeCanvas.save();
        nativeCanvas.setMatrix(IDENTITY);
        nativeCanvas.drawImageRect(cardImage,
                new Rect2f(0, 0, cardImage.getWidth(), cardImage.getHeight()),
                new Rect2f(device.left() - shadowMarginPx, device.top() - shadowMarginPx,
                        device.left() - shadowMarginPx + cardImage.getWidth(),
                        device.top() - shadowMarginPx + cardImage.getHeight()),
                SamplingOptions.LINEAR, null,
                icyllis.arc3d.sketch.Canvas.SRC_RECT_CONSTRAINT_FAST);
        nativeCanvas.restore();
    }
}
