package indi.etern.musichud.client.ui.render.warp;

import java.util.Objects;

/**
 * Evaluates a control-offset lattice into a dense forward mesh using either cubic
 * Catmull-Rom interpolation or the standard cubic cardinal B-spline basis.
 */
public final class WarpMeshEvaluator {
    private WarpMeshEvaluator() {
    }

    public static WarpMesh evaluate(float[] offsets, int gridSize,
                                    WarpMeshInterpolation interpolation) {
        return evaluate(offsets, gridSize, interpolation, WarpMesh.DEFAULT_RESOLUTION);
    }

    public static WarpMesh evaluate(float[] offsets, int gridSize,
                                    WarpMeshInterpolation interpolation, int resolution) {
        return evaluate(offsets, gridSize, interpolation, resolution, 1f);
    }

    public static WarpMesh evaluate(float[] offsets, int gridSize,
                                    WarpMeshInterpolation interpolation, int resolution,
                                    float zoom) {
        Objects.requireNonNull(offsets, "offsets");
        Objects.requireNonNull(interpolation, "interpolation");
        if (gridSize < 2 || gridSize > WarpPresetGenerator.MAX_GRID) {
            throw new IllegalArgumentException("Invalid warp control grid size");
        }
        if (offsets.length != gridSize * gridSize * 2) {
            throw new IllegalArgumentException("Warp offsets do not match the control grid");
        }
        if (resolution < 2 || (long) resolution * resolution > Short.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid warp mesh resolution");
        }
        if (!Float.isFinite(zoom) || zoom <= 0) {
            throw new IllegalArgumentException("Invalid warp mesh zoom");
        }

        int vertexCount = resolution * resolution;
        float[] positions = new float[vertexCount * 2];
        float[] uv = new float[vertexCount * 2];
        float[] controlPoints = new float[gridSize * gridSize * 2];
        for (int i = 0; i < controlPoints.length / 2; i++) {
            int column = i % gridSize;
            int row = i / gridSize;
            float baseX = column / (float) (gridSize - 1);
            float baseY = row / (float) (gridSize - 1);
            controlPoints[i * 2] = .5f + (baseX + offsets[i * 2] - .5f) * zoom;
            controlPoints[i * 2 + 1] = .5f + (baseY + offsets[i * 2 + 1] - .5f) * zoom;
        }

        float[] wx = new float[4];
        float[] wy = new float[4];
        for (int y = 0; y < resolution; y++) {
            float v = y / (float) (resolution - 1);
            float gy = v * (gridSize - 1);
            int iy = (int) Math.floor(gy);
            weights(interpolation, gy - iy, wy);
            for (int x = 0; x < resolution; x++) {
                float u = x / (float) (resolution - 1);
                float gx = u * (gridSize - 1);
                int ix = (int) Math.floor(gx);
                weights(interpolation, gx - ix, wx);
                float dx = 0;
                float dy = 0;
                for (int j = 0; j < 4; j++) {
                    int sy = Math.clamp(iy + j - 1, 0, gridSize - 1);
                    for (int i = 0; i < 4; i++) {
                        int sx = Math.clamp(ix + i - 1, 0, gridSize - 1);
                        int source = (sy * gridSize + sx) * 2;
                        float weight = wx[i] * wy[j];
                        dx += offsets[source] * weight;
                        dy += offsets[source + 1] * weight;
                    }
                }
                int target = (y * resolution + x) * 2;
                uv[target] = u;
                uv[target + 1] = v;
                positions[target] = .5f + (u + dx - .5f) * zoom;
                positions[target + 1] = .5f + (v + dy - .5f) * zoom;
            }
        }

        short[] indices = new short[(resolution - 1) * (resolution - 1) * 6];
        int cursor = 0;
        for (int y = 0; y < resolution - 1; y++) {
            for (int x = 0; x < resolution - 1; x++) {
                int top = y * resolution + x;
                int bottom = top + resolution;
                indices[cursor++] = (short) top;
                indices[cursor++] = (short) (top + 1);
                indices[cursor++] = (short) bottom;
                indices[cursor++] = (short) (top + 1);
                indices[cursor++] = (short) (bottom + 1);
                indices[cursor++] = (short) bottom;
            }
        }
        return new WarpMesh(positions, uv, indices, controlPoints,
                resolution, resolution, gridSize);
    }

    private static void weights(WarpMeshInterpolation interpolation, float t, float[] out) {
        if (interpolation == WarpMeshInterpolation.CATMULL_ROM) {
            float t2 = t * t;
            float t3 = t2 * t;
            out[0] = -0.5f * t3 + t2 - 0.5f * t;
            out[1] = 1.5f * t3 - 2.5f * t2 + 1f;
            out[2] = -1.5f * t3 + 2f * t2 + 0.5f * t;
            out[3] = 0.5f * t3 - 0.5f * t2;
        } else {
            float inverse = 1f - t;
            float t2 = t * t;
            float t3 = t2 * t;
            out[0] = inverse * inverse * inverse / 6f;
            out[1] = (3f * t3 - 6f * t2 + 4f) / 6f;
            out[2] = (-3f * t3 + 3f * t2 + 3f * t + 1f) / 6f;
            out[3] = t3 / 6f;
        }
    }
}
