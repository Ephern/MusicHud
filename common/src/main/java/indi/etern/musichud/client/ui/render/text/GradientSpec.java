package indi.etern.musichud.client.ui.render.text;

import java.util.Arrays;

/** Horizontal multi-stop gradient over normalized text advance. */
public final class GradientSpec {
    private final float[] positions;
    private final int[] colors;

    private GradientSpec(float[] positions, int[] colors) {
        this.positions = positions;
        this.colors = colors;
    }

    public static GradientSpec twoStop(int startArgb, int endArgb) {
        return new GradientSpec(new float[]{0f, 1f}, new int[]{startArgb, endArgb});
    }

    public static GradientSpec flat(int argb) {
        return twoStop(argb, argb);
    }

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

    public static GradientSpec window(int beforeArgb, int afterArgb, float center, float halfWidth) {
        return of(
                new float[]{center - halfWidth, center + halfWidth},
                new int[]{beforeArgb, afterArgb}
        );
    }

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
