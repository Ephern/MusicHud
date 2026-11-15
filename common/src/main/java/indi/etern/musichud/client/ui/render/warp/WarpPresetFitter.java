package indi.etern.musichud.client.ui.render.warp;

import java.util.Objects;

/**
 * Strict fold guard: scales a generated offset field down until every evaluated cell keeps a
 * positive Jacobian determinant and a minimum relative area.
 */
public final class WarpPresetFitter {
    /**
     * A curve may compress a cell down to this relative area, but never invert it.
     * Control points themselves are allowed to cross as long as the evaluated curve stays valid.
     */
    public static final float SAFE_MIN_DET_RATIO = .05f;
    public static final float MIN_GUARD_RATIO = .01f;
    public static final float MAX_GUARD_RATIO = .25f;
    public static final int VALIDATION_RESOLUTION = WarpMesh.DEFAULT_RESOLUTION;

    public record Result(float[] offsets, float fitScale, WarpMeshMetrics.Metrics metrics) {
    }

    private WarpPresetFitter() {
    }

    public static Result fit(float[] rawOffsets, int gridSize,
                             WarpMeshInterpolation interpolation, float zoom) {
        return fit(rawOffsets, gridSize, interpolation, zoom, SAFE_MIN_DET_RATIO);
    }

    public static Result fit(float[] rawOffsets, int gridSize,
                             WarpMeshInterpolation interpolation, float zoom,
                             float minDetRatio) {
        Objects.requireNonNull(rawOffsets, "rawOffsets");
        Objects.requireNonNull(interpolation, "interpolation");
        if (!Float.isFinite(minDetRatio) || minDetRatio <= 0 || minDetRatio > 1) {
            throw new IllegalArgumentException("Invalid fold guard target");
        }
        WarpMeshMetrics.Metrics initial = metrics(rawOffsets, gridSize, interpolation,
                zoom, 1f);
        if (safe(initial, minDetRatio)) {
            return new Result(rawOffsets.clone(), 1f, initial);
        }

        float low = 0;
        float high = 1;
        for (int i = 0; i < 16; i++) {
            float mid = (low + high) * .5f;
            WarpMeshMetrics.Metrics sample = metrics(rawOffsets, gridSize, interpolation,
                    zoom, mid);
            if (safe(sample, minDetRatio)) {
                low = mid;
            } else {
                high = mid;
            }
        }
        float scale = low;
        float[] offsets = scaled(rawOffsets, scale);
        WarpMeshMetrics.Metrics metrics = metrics(offsets, gridSize, interpolation, zoom, 1f);
        return new Result(offsets, scale, metrics);
    }

    private static boolean safe(WarpMeshMetrics.Metrics metrics, float minDetRatio) {
        return metrics.foldedFraction() == 0 && metrics.minDetRatio() >= minDetRatio;
    }

    private static WarpMeshMetrics.Metrics metrics(float[] offsets, int gridSize,
                                                   WarpMeshInterpolation interpolation,
                                                   float zoom, float scale) {
        float[] source = scale == 1f ? offsets : scaled(offsets, scale);
        WarpMesh mesh = WarpMeshEvaluator.evaluate(source, gridSize, interpolation,
                VALIDATION_RESOLUTION, zoom);
        return WarpMeshMetrics.measure(mesh);
    }

    private static float[] scaled(float[] offsets, float scale) {
        float[] result = new float[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            result[i] = offsets[i] * scale;
        }
        return result;
    }
}
