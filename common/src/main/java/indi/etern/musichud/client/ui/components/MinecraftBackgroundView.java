package indi.etern.musichud.client.ui.components;

import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.core.SamplingOptions;
import icyllis.arc3d.sketch.Image;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import icyllis.modernui.view.View;
import indi.etern.musichud.client.ui.render.gpu.MinecraftBackgroundTexture;
import org.jetbrains.annotations.NotNull;

/**
 * Draws the baked Minecraft background as the bottom-most view of the ModernUI layer.
 */
public final class MinecraftBackgroundView extends View {
    // The bake target is written by a Blaze3D pass; arc3d may see it with the opposite Y origin.
    private static final boolean FLIP_Y = false;

    public MinecraftBackgroundView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        MinecraftBackgroundTexture.registerConsumer();
    }

    @Override
    protected void onDetachedFromWindow() {
        MinecraftBackgroundTexture.unregisterConsumer();
        super.onDetachedFromWindow();
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    protected void onDraw(@NotNull Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width > 0 && height > 0 && canvas instanceof ArcCanvas arc) {
            Image image = MinecraftBackgroundTexture.image();
            if (image != null) {
                var nativeCanvas = arc.getCanvas();
                nativeCanvas.save();
                if (FLIP_Y) {
                    nativeCanvas.translate(0, height);
                    nativeCanvas.scale(1, -1);
                }
                nativeCanvas.drawImageRect(image,
                        new Rect2f(0, 0, image.getWidth(), image.getHeight()),
                        new Rect2f(0, 0, width, height),
                        SamplingOptions.LINEAR, null,
                        icyllis.arc3d.sketch.Canvas.SRC_RECT_CONSTRAINT_FAST);
                nativeCanvas.restore();
            }
        }
        // Keep the animated background updating even when the rest of the UI is idle.
        invalidate();
    }
}
