package indi.etern.musichud.client.ui.render.text;

import java.util.Arrays;

/**
 * A horizontal, multi-stop linear gradient expressed over the normalized text advance
 * ({@code [0, 1]} maps to the whole drawn string, left to right).
 * <p>
 * Stops are kept sorted and strictly increasing. Sampling interpolates the ARGB channels
 * (including alpha) between the two surrounding stops; a caller supplied global alpha is
 * folded into the sampled color so the whole gradient can be faded in/out per frame.
 * <p>
 * The gradient is applied per glyph quad: the two left vertices sample at the glyph's
 * left edge and the two right vertices at its right edge, so the GPU linearly interpolates
 * the color <b>inside</b> a single character as well as between characters.
 */
public final class GradientSpec {
    private final float[] positions;
    private final int[] colors;

    private GradientSpec(float[] positions, int[] colors) {
        this.positions = positions;
        this.colors = colors;
    }

    /**
     * Two-stop gradient across the full text width.
     */
    public static GradientSpec twoStop(int startArgb, int endArgb) {
        return new GradientSpec(new float[]{0f, 1f}, new int[]{startArgb, endArgb});
    }

    /**
     * A flat single-color spec, used to route plain (non-gradient) text through the gradient
     * geometry path so it shares the same layout-local snapping and bilinear sampling.
     */
    public static GradientSpec flat(int argb) {
        return twoStop(argb, argb);
    }

    /**
     * Builds a gradient from stop positions and ARGB colors. Input arrays are copied, sorted and
     * de-duplicated; a stop colliding with an earlier one wins.
     * <p>
     * Positions are not restricted to {@code [0, 1]}: values outside the text span are allowed
     * so that a control point can deliberately slide completely off an edge (which {@link #sample}
     * resolves to the nearest endpoint color).
     */
    public static GradientSpec of(float[] positions, int[] colors) {
        if (positions.length != colors.length) {
            throw new IllegalArgumentException("positions and colors must have the same length");
        }
        if (positions.length == 0) {
            throw new IllegalArgumentException("a gradient needs at least one stop");
        }
        Integer[] order = new Integer[positions.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Float.compare(positions[a], positions[b]));

        float[] outPositions = new float[positions.length];
        int[] outColors = new int[colors.length];
        int size = 0;
        for (int index : order) {
            float position = positions[index];
            if (size > 0 && position <= outPositions[size - 1]) {
                // same position: later stop replaces the previous value
                if (position < outPositions[size - 1]) {
                    position = outPositions[size - 1];
                }
                outPositions[size - 1] = position;
                outColors[size - 1] = colors[index];
                continue;
            }
            outPositions[size] = position;
            outColors[size] = colors[index];
            size++;
        }
        return new GradientSpec(Arrays.copyOf(outPositions, size), Arrays.copyOf(outColors, size));
    }

    /**
     * A moving two-sided window used for karaoke sweeps: everything at or left of
     * {@code center - halfWidth} is {@code beforeArgb}, everything at or right of
     * {@code center + halfWidth} is {@code afterArgb}, and the band between them interpolates.
     * <p>
     * The window keeps its colors on both sides, so with {@code center} sliding outside
     * {@code [0, 1]} the window can travel completely off either edge of the text: a center of
     * {@code -halfWidth} yields pure {@code afterArgb} and {@code 1 + halfWidth} yields pure
     * {@code beforeArgb}, both reached continuously.
     *
     * @param center    window center in normalized text units, may lie outside {@code [0, 1]}
     * @param halfWidth half width of the smooth band in normalized units
     */
    public static GradientSpec window(int beforeArgb, int afterArgb, float center, float halfWidth) {
        return of(
                new float[]{center - halfWidth, center + halfWidth},
                new int[]{beforeArgb, afterArgb}
        );
    }

    /**
     * Samples the gradient at {@code t} (clamped to {@code [0, 1]}) and multiplies the alpha
     * channel by {@code alpha} (also clamped to {@code [0, 1]}). At or beyond the first and last
     * stop the corresponding endpoint color is returned, which is what lets control points
     * outside {@code [0, 1]} behave as a fully slid-out window.
     */
    public int sample(float t, float alpha) {
        float clamped = Math.clamp(t, 0f, 1f);
        int last = positions.length - 1;
        if (clamped <= positions[0]) {
            return applyAlpha(colors[0], alpha);
        }
        if (clamped >= positions[last]) {
            return applyAlpha(colors[last], alpha);
        }
        int index = 0;
        while (index < last - 1 && clamped > positions[index + 1]) {
            index++;
        }
        float p0 = positions[index];
        float p1 = positions[index + 1];
        float u = p1 > p0 ? (clamped - p0) / (p1 - p0) : 0f;
        return applyAlpha(lerpColor(colors[index], colors[index + 1], u), alpha);
    }

    /**
     * Samples with the given global alpha already folded in; convenience for single-color
     * fallbacks.
     */
    public int sample(float t) {
        return sample(t, 1f);
    }

    private static int lerpColor(int from, int to, float u) {
        int a = Math.round(((from >>> 24) & 0xFF) + u * (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)));
        int r = Math.round(((from >> 16) & 0xFF) + u * (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)));
        int g = Math.round(((from >> 8) & 0xFF) + u * (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)));
        int b = Math.round((from & 0xFF) + u * ((to & 0xFF) - (from & 0xFF)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int applyAlpha(int argb, float alpha) {
        int base = (argb >>> 24) & 0xFF;
        int scaled = Math.clamp((int) (base * alpha), 0, 255);
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }
}
