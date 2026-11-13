package indi.etern.musichud.client.ui.lyric;

import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.core.SamplingOptions;
import icyllis.arc3d.sketch.Image;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Offscreen source and composite of a single lyric row. The Gaussian itself is deferred to the
 * render thread ({@code SceneBlur}) so it runs as one shader pass per axis instead of per-tap draws.
 */
@SuppressWarnings("UnstableApiUsage")
public final class LineBlurRenderer {
    /** Visible blur radius to Gaussian sigma. */
    private static final float SIGMA_FACTOR = 0.5f;
    /** Below this radius the line is drawn sharply. */
    private static final float MIN_RADIUS_PX = 0.5f;

    @FunctionalInterface
    public interface ContentDrawer {
        void draw(@NotNull Canvas canvas);
    }

    private LineBlurRenderer() {
    }

    /**
     * Maps a row distance from the highlighted line to a blur radius.
     * Distance 0 is the highlighted line (sharp); the radius grows with distance (visible from
     * distance 1) and saturates at {@code MAX_BLUR_FALLOFF_LINES = 5} rows.
     */
    public static float radiusForDistance(int distance, float maxRadius) {
        if (distance <= 0) {
            return 0f;
        }
        float t = clamp((distance - 1) / 4f);
        return maxRadius * (0.3f + 0.7f * t);
    }

    public static float sigmaFor(float radius) {
        return radius * SIGMA_FACTOR;
    }

    /**
     * Maximum horizontal (and vertical) bleed, in local pixels, of a line blurred with the given
     * radius. Callers reserve this much layout room so the blurred tail is not clipped by a parent.
     */
    public static int maxPaddingFor(float radius) {
        if (radius <= 0f) {
            return 0;
        }
        return (int) Math.ceil(3f * sigmaFor(radius)) + 2;
    }

    public static boolean isBlurrable(float radius) {
        return radius >= MIN_RADIUS_PX;
    }

    /** Draws the blurred row back, mapped to the row's local coordinates. */
    public static void drawResult(@NotNull ArcCanvas main, @NotNull Image image,
                                  int surfaceWidth, int surfaceHeight, int padding) {
        drawResult(main, image, surfaceWidth, surfaceHeight, padding, null);
    }

    /** Same as {@link #drawResult(ArcCanvas, Image, int, int, int)}, with an optional paint. */
    public static void drawResult(@NotNull ArcCanvas main, @NotNull Image image,
                                  int surfaceWidth, int surfaceHeight, int padding,
                                  @Nullable Paint paint) {
        main.getCanvas().drawImageRect(image,
                new Rect2f(0, 0, image.getWidth(), image.getHeight()),
                new Rect2f(-padding, -padding, surfaceWidth - padding, surfaceHeight - padding),
                SamplingOptions.LINEAR, paint != null ? paint.getNativePaint() : null,
                icyllis.arc3d.sketch.Canvas.SRC_RECT_CONSTRAINT_FAST);
    }

    private static float clamp(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }
}
