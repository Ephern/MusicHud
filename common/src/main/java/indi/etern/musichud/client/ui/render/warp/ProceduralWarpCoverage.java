package indi.etern.musichud.client.ui.render.warp;

/** Pushes the target border outside the screen so a fold-free target cannot leave black gaps. */
public final class ProceduralWarpCoverage {
    static final float MARGIN = .02f;
    static final float EDGE_BAND = .12f;
    static final float MAX_COVERAGE_SCALE = 16f;

    private ProceduralWarpCoverage() {
    }

    public static WarpMesh ensureCoverage(WarpMesh mesh) {
        float scale = requiredCoverageScale(mesh);
        if (scale <= 1.0001f) {
            return mesh;
        }
        float[] local = scaledPositions(mesh, scale, true);
        WarpMesh localMesh = replacePositions(mesh, local);
        if (safe(localMesh)) {
            return localMesh;
        }
        float[] uniform = scaledPositions(mesh, scale, false);
        return replacePositions(mesh, uniform);
    }

    private static float requiredCoverageScale(WarpMesh mesh) {
        int columns = mesh.columns();
        int rows = mesh.rows();
        float[] positions = mesh.positions();
        float scale = 1f;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                if (!isBoundary(x, y, columns, rows)) {
                    continue;
                }
                int index = (y * columns + x) * 2;
                float required = requiredPointScale(positions[index], positions[index + 1]);
                if (Float.isFinite(required)) {
                    scale = Math.max(scale, required);
                }
            }
        }
        return Math.min(scale, MAX_COVERAGE_SCALE);
    }

    private static float requiredPointScale(float x, float y) {
        float dx = x - .5f;
        float dy = y - .5f;
        float sx = dx < 0 ? (-MARGIN - .5f) / dx : dx > 0 ? (1f + MARGIN - .5f) / dx
                : Float.POSITIVE_INFINITY;
        float sy = dy < 0 ? (-MARGIN - .5f) / dy : dy > 0 ? (1f + MARGIN - .5f) / dy
                : Float.POSITIVE_INFINITY;
        float required = Math.min(sx, sy);
        if (required <= 1f) {
            return 1f;
        }
        return required;
    }

    private static float[] scaledPositions(WarpMesh mesh, float maxScale, boolean edgeBlend) {
        float[] source = mesh.positions();
        float[] uv = mesh.uv();
        float[] result = new float[source.length];
        for (int i = 0; i < source.length; i += 2) {
            float weight = edgeBlend ? edgeWeight(uv[i], uv[i + 1]) : 1f;
            float scale = 1f + (maxScale - 1f) * weight;
            result[i] = .5f + (source[i] - .5f) * scale;
            result[i + 1] = .5f + (source[i + 1] - .5f) * scale;
        }
        return result;
    }

    private static float edgeWeight(float u, float v) {
        float distance = Math.min(Math.min(u, 1f - u), Math.min(v, 1f - v));
        if (distance >= EDGE_BAND) {
            return 0;
        }
        float t = 1f - distance / EDGE_BAND;
        return t * t * (3f - 2f * t);
    }

    private static boolean safe(WarpMesh mesh) {
        WarpMeshMetrics.Metrics metrics = WarpMeshMetrics.measure(mesh);
        return metrics.foldedFraction() == 0
                && metrics.minDetRatio() >= WarpPresetFitter.SAFE_MIN_DET_RATIO - 1e-4f;
    }

    private static boolean isBoundary(int x, int y, int columns, int rows) {
        return x == 0 || y == 0 || x == columns - 1 || y == rows - 1;
    }

    private static WarpMesh replacePositions(WarpMesh mesh, float[] positions) {
        return new WarpMesh(positions, mesh.uv(), mesh.indices(), mesh.controlPoints(),
                mesh.columns(), mesh.rows(), mesh.gridSize());
    }
}
