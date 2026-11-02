package indi.etern.musichud.client.ui.render.gpu;

import icyllis.arc3d.core.ColorInfo;
import icyllis.arc3d.core.ColorSpaces;
import icyllis.arc3d.core.ImageInfo;
import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.core.SamplingOptions;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.granite.GraniteSurface;
import icyllis.arc3d.sketch.Image;
import icyllis.arc3d.sketch.Surface;
import icyllis.modernui.core.Core;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A reusable RGBA8 premultiplied arc3d target that a view subtree is drawn into. Wraps the
 * create/clear/translate/snapshot sequence so every offscreen isolation keeps its canvas state
 * balanced; an unbalanced translate leaks into the next frame because the surface canvas is reused.
 */
@SuppressWarnings("UnstableApiUsage")
public final class SceneOffscreen {
    public interface Content {
        void draw(@NotNull Canvas canvas);
    }

    private final String label;
    private Surface surface;
    private int width;
    private int height;

    public SceneOffscreen(@NotNull String label) {
        this.label = label;
    }

    public @Nullable Surface surface() {
        return surface;
    }

    public boolean isReady() {
        return surface != null;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /**
     * @return true when the target was (re)created, i.e. any cached content is stale
     */
    public boolean ensure(int width, int height) {
        if (surface != null && this.width == width && this.height == height) {
            return false;
        }
        release();
        if (width < 1 || height < 1) {
            return false;
        }
        try {
            surface = GraniteSurface.makeRenderTarget(Core.requireUiRecordingContext(),
                    ImageInfo.make(width, height,
                            ColorInfo.CT_RGBA_8888, ColorInfo.AT_PREMUL, ColorSpaces.SRGB),
                    false, Engine.SurfaceOrigin.kUpperLeft, label);
            this.width = width;
            this.height = height;
            return surface != null;
        } catch (RuntimeException failure) {
            release();
            return false;
        }
    }

    /** Draws the content into the target, translated by {@code (dx, dy)}. Does not snapshot. */
    public boolean render(float dx, float dy, @NotNull Content content) {
        if (surface == null) {
            return false;
        }
        surface.notifyWillChange();
        var recordCanvas = surface.getCanvas();
        recordCanvas.clear(0);
        Canvas canvas = new ArcCanvas(recordCanvas);
        canvas.save();
        canvas.translate(dx, dy);
        content.draw(canvas);
        canvas.restore();
        return true;
    }

    /** Same as {@link #render} but also snapshots the result. The caller owns the returned image. */
    public @Nullable Image renderSnapshot(float dx, float dy, @NotNull Content content) {
        if (!render(dx, dy, content)) {
            return null;
        }
        return surface.makeImageSnapshot();
    }

    /** Copies an image 1:1 into the target, e.g. to freeze a region that is blurred later. */
    public boolean renderImage(@Nullable Image source, int width, int height) {
        if (source == null) {
            return false;
        }
        return render(0f, 0f, canvas -> ((ArcCanvas) canvas).getCanvas().drawImageRect(source,
                new Rect2f(0, 0, width, height),
                new Rect2f(0, 0, width, height),
                SamplingOptions.LINEAR, null,
                icyllis.arc3d.sketch.Canvas.SRC_RECT_CONSTRAINT_FAST));
    }

    public void release() {
        if (surface != null) {
            surface.unref();
            surface = null;
        }
        width = 0;
        height = 0;
    }

    public void close() {
        release();
    }
}
