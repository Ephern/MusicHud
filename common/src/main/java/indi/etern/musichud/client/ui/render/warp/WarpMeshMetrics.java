package indi.etern.musichud.client.ui.render.warp;

/** Signed-area metrics used by the strict fold guard and the debug heatmap. */
public final class WarpMeshMetrics {
    public record Metrics(float minDet, float minDetRatio, float foldedFraction,
                          float maxCompression, float maxDetRatio, float maxOffset) {
    }

    private WarpMeshMetrics() {
    }

    public static Metrics measure(WarpMesh mesh) {
        int columns = mesh.columns();
        int rows = mesh.rows();
        if (columns < 2 || rows < 2) {
            throw new IllegalArgumentException("Warp mesh is too small");
        }
        float[] positions = mesh.positions();
        float baseDet = identityDet(columns);
        float minimum = Float.POSITIVE_INFINITY;
        float maximum = Float.NEGATIVE_INFINITY;
        int folded = 0;
        float maximumOffset = 0;
        for (int y = 0; y < rows - 1; y++) {
            for (int x = 0; x < columns - 1; x++) {
                float det = cellDet(mesh, x, y);
                minimum = Math.min(minimum, det);
                maximum = Math.max(maximum, det);
                if (det <= 0) {
                    folded++;
                }
            }
        }
        float[] uv = mesh.uv();
        for (int i = 0; i < positions.length; i += 2) {
            maximumOffset = Math.max(maximumOffset,
                    (float) Math.hypot(positions[i] - uv[i], positions[i + 1] - uv[i + 1]));
        }
        int cells = (columns - 1) * (rows - 1);
        float ratio = minimum / baseDet;
        float maxRatio = maximum / baseDet;
        float compression = ratio <= 1e-6f ? Float.POSITIVE_INFINITY : 1f / ratio;
        return new Metrics(minimum, ratio, folded / (float) cells, compression,
                maxRatio, maximumOffset);
    }

    public static float cellDet(WarpMesh mesh, int x, int y) {
        int columns = mesh.columns();
        float[] positions = mesh.positions();
        int top = (y * columns + x) * 2;
        int right = top + 2;
        int bottom = top + columns * 2;
        float ex = positions[right] - positions[top];
        float ey = positions[right + 1] - positions[top + 1];
        float fx = positions[bottom] - positions[top];
        float fy = positions[bottom + 1] - positions[top + 1];
        return ex * fy - ey * fx;
    }

    public static float identityDet(int columns) {
        if (columns < 2) {
            throw new IllegalArgumentException("Invalid warp mesh columns");
        }
        float step = 1f / (columns - 1);
        return step * step;
    }
}
