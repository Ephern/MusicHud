package indi.etern.musichud.client.ui.render.warp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WarpMeshMetricsTest {
    @Test
    void identityMeshHasUnitRelativeArea() {
        float[] offsets = new float[9 * 9 * 2];
        WarpMesh mesh = WarpMeshEvaluator.evaluate(offsets, 9,
                WarpMeshInterpolation.B_SPLINE, 65);
        WarpMeshMetrics.Metrics metrics = WarpMeshMetrics.measure(mesh);
        assertEquals(0, metrics.foldedFraction(), 0f);
        assertEquals(1, metrics.minDetRatio(), 1e-5f);
        assertEquals(1, metrics.maxDetRatio(), 1e-5f);
        assertEquals(0, metrics.maxOffset(), 1e-6f);
    }

    @Test
    void zoomScalesAreaQuadratically() {
        float[] offsets = new float[9 * 9 * 2];
        WarpMesh mesh = WarpMeshEvaluator.evaluate(offsets, 9,
                WarpMeshInterpolation.B_SPLINE, 65, 1.2f);
        WarpMeshMetrics.Metrics metrics = WarpMeshMetrics.measure(mesh);
        assertEquals(1.44f, metrics.minDetRatio(), 1e-4f);
        assertEquals(1.44f, metrics.maxDetRatio(), 1e-4f);
    }

    @Test
    void cellDeterminantMatchesIdentityForUntransformedMesh() {
        float[] offsets = new float[5 * 5 * 2];
        WarpMesh mesh = WarpMeshEvaluator.evaluate(offsets, 5,
                WarpMeshInterpolation.CATMULL_ROM, 17);
        float expected = WarpMeshMetrics.identityDet(17);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                assertEquals(expected, WarpMeshMetrics.cellDet(mesh, x, y), 1e-9f);
            }
        }
    }
}
