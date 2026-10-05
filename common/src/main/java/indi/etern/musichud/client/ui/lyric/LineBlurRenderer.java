package indi.etern.musichud.client.ui.lyric;

import icyllis.arc3d.core.Rect2f;
import icyllis.arc3d.core.SamplingOptions;
import icyllis.arc3d.sketch.BlendMode;
import icyllis.arc3d.sketch.Image;
import icyllis.arc3d.sketch.Paint;
import icyllis.arc3d.sketch.Surface;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.pipeline.ArcCanvas;
import org.jetbrains.annotations.NotNull;

/**
 * GPU-only single-line Gaussian blur used by the lyrics view.
 * <p>
 * The line subtree is rendered once into an offscreen render target (the "source"), then
 * blurred with a separable Gaussian: a horizontal pass into a scratch target, followed by a
 * vertical pass into the final target. Each pass is a weighted sum of offset copies of the
 * source image, accumulated with {@link BlendMode#PLUS}. The full-resolution result is finally
 * composited back onto the main canvas. No CPU pixel work and no read-back are involved.
 * <p>
 * Everything is cached per {@link LineBlurResources}: the source is only re-rendered when the
 * content signature or the target size changes, and the blur is only re-run when the radius or
 * the source changes.
 */
public final class LineBlurRenderer {
    /** Visible blur radius to Gaussian sigma. */
    private static final float SIGMA_FACTOR = 0.5f;
    /** Upper bound on the half kernel size (taps = 2 * k + 1) before downscaling kicks in. */
    private static final int MAX_HALF_TAPS = 14;
    private static final float MIN_DOWNSCALE = 0.125f;
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
        float t = Math.clamp((distance - 1) / 4f, 0f, 1f);
        return maxRadius * (0.3f + 0.7f * t);
    }

    /**
     * Maximum horizontal (and vertical) bleed, in local pixels, of a line blurred with the given
     * radius. Callers reserve this much layout room so the blurred tail is not clipped by a parent.
     */
    public static int maxPaddingFor(float radius) {
        if (radius <= 0f) {
            return 0;
        }
        float downscale = chooseDownscale(radius);
        float sigma = radius * SIGMA_FACTOR * downscale;
        int halfTaps = Math.max(1, (int) Math.ceil(3f * sigma));
        int padding = halfTaps + 2;
        return (int) Math.ceil(padding / downscale);
    }

    /**
     * Draws the blurred line if possible.
     *
     * @return true if the blurred content was drawn; false if the caller should fall back to a
     * sharp draw (not eligible, degenerate size, or offscreen allocation failed)
     */
    public static boolean drawBlurred(@NotNull ArcCanvas main,
                                      int viewWidth, int viewHeight,
                                      float radius, long contentStamp,
                                      @NotNull LineBlurResources res,
                                      @NotNull ContentDrawer drawer) {
        if (viewWidth <= 0 || viewHeight <= 0 || radius < MIN_RADIUS_PX) {
            return false;
        }

        float downscale = chooseDownscale(radius);
        float sigma = radius * SIGMA_FACTOR * downscale;
        int halfTaps = Math.max(1, (int) Math.ceil(3f * sigma));
        int padding = halfTaps + 2;
        int contentWidth = (int) Math.ceil(viewWidth * downscale);
        int contentHeight = (int) Math.ceil(viewHeight * downscale);
        int surfaceWidth = contentWidth + 2 * padding;
        int surfaceHeight = contentHeight + 2 * padding;
        if (surfaceWidth > 8192 || surfaceHeight > 8192 || surfaceWidth <= 0 || surfaceHeight <= 0) {
            return false;
        }

        boolean recreated = res.ensureSurfaces(surfaceWidth, surfaceHeight, downscale);
        if (!res.isReady()) {
            return false;
        }

        boolean contentChanged = recreated || res.cachedContentStamp != contentStamp;
        if (contentChanged) {
            renderSource(res, padding, downscale, drawer);
            res.cachedContentStamp = contentStamp;
        }
        if (contentChanged || res.cachedRadius != radius) {
            convolve(res, surfaceWidth, surfaceHeight, sigma, halfTaps);
            res.cachedRadius = radius;
        }
        if (res.finalImage == null) {
            return false;
        }

        float paddingLocal = padding / downscale;
        float dstRight = (surfaceWidth - padding) / downscale;
        float dstBottom = (surfaceHeight - padding) / downscale;

        icyllis.arc3d.sketch.Canvas nativeCanvas = main.getCanvas();
        nativeCanvas.save();
        nativeCanvas.drawImageRect(res.finalImage,
                new Rect2f(0, 0, surfaceWidth, surfaceHeight),
                new Rect2f(-paddingLocal, -paddingLocal, dstRight, dstBottom),
                SamplingOptions.LINEAR, null,
                icyllis.arc3d.sketch.Canvas.SRC_RECT_CONSTRAINT_FAST);
        nativeCanvas.restore();
        return true;
    }

    private static void renderSource(LineBlurResources res, int padding, float downscale,
                                     ContentDrawer drawer) {
        Surface surface = res.sourceSurface;
        surface.notifyWillChange();
        icyllis.arc3d.sketch.Canvas canvas = surface.getCanvas();
        canvas.clear(0);
        canvas.save();
        canvas.translate(padding, padding);
        canvas.scale(downscale, downscale);
        drawer.draw(new ArcCanvas(canvas));
        canvas.restore();
        res.replaceSourceImage(surface.makeImageSnapshot());
    }

    private static void convolve(LineBlurResources res, int surfaceWidth, int surfaceHeight,
                                 float sigma, int halfTaps) {
        if (res.sourceImage == null) {
            return;
        }
        float[] weights = gaussianWeights(sigma, halfTaps);
        Image horizontal = convolveAxis(res, res.sourceImage, res.scratchSurface,
                surfaceWidth, surfaceHeight, weights, halfTaps, true);
        if (horizontal == null) {
            return;
        }
        Image vertical = convolveAxis(res, horizontal, res.finalSurface,
                surfaceWidth, surfaceHeight, weights, halfTaps, false);
        horizontal.unref();
        res.replaceFinalImage(vertical);
    }

    private static Image convolveAxis(LineBlurResources res, Image source, Surface target,
                                      int surfaceWidth, int surfaceHeight,
                                      float[] weights, int halfTaps, boolean horizontal) {
        target.notifyWillChange();
        icyllis.arc3d.sketch.Canvas canvas = target.getCanvas();
        canvas.clear(0);
        Rect2f srcRect = new Rect2f(0, 0, surfaceWidth, surfaceHeight);
        Paint paint = res.tapPaint;
        paint.setBlendMode(BlendMode.PLUS);
        for (int i = -halfTaps; i <= halfTaps; i++) {
            float weight = weights[i + halfTaps];
            if (weight <= 0f) {
                continue;
            }
            paint.setAlpha(weight);
            Rect2f dstRect = horizontal
                    ? new Rect2f(i, 0, i + surfaceWidth, surfaceHeight)
                    : new Rect2f(0, i, surfaceWidth, i + surfaceHeight);
            canvas.drawImageRect(source, srcRect, dstRect, SamplingOptions.LINEAR, paint,
                    icyllis.arc3d.sketch.Canvas.SRC_RECT_CONSTRAINT_FAST);
        }
        paint.setBlendMode(null);
        return target.makeImageSnapshot();
    }

    private static float[] gaussianWeights(float sigma, int halfTaps) {
        float[] weights = new float[2 * halfTaps + 1];
        if (sigma <= 1e-4f) {
            weights[halfTaps] = 1f;
            return weights;
        }
        float twoSigmaSq = 2f * sigma * sigma;
        float sum = 0f;
        for (int i = -halfTaps; i <= halfTaps; i++) {
            float value = (float) Math.exp(-(i * i) / twoSigmaSq);
            weights[i + halfTaps] = value;
            sum += value;
        }
        float inv = 1f / sum;
        for (int i = 0; i < weights.length; i++) {
            weights[i] *= inv;
        }
        return weights;
    }

    private static float chooseDownscale(float radius) {
        float downscale = 1f;
        while (chooseHalfTaps(radius, downscale) > MAX_HALF_TAPS && downscale > MIN_DOWNSCALE) {
            downscale *= 0.5f;
        }
        return downscale;
    }

    private static int chooseHalfTaps(float radius, float downscale) {
        float sigma = radius * SIGMA_FACTOR * downscale;
        return (int) Math.ceil(3f * sigma);
    }
}
