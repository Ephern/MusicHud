package indi.etern.musichud.client.ui.render.warp;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProceduralWarpCoverageTest {
    @Test
    void randomTargetsCoverTheScreenBorderWithoutFolds() {
        Random random = new Random(0x5EED);
        for (int i = 0; i < 40; i++) {
            ProceduralWarpTargets.Target target = ProceduralWarpTargets.randomTarget(random);
            WarpMesh source = WarpMeshEvaluator.evaluate(target.offsets(), target.gridSize(),
                    target.interpolation(), WarpMesh.DEFAULT_RESOLUTION, target.zoom());
            WarpMesh covered = ProceduralWarpCoverage.ensureCoverage(source);
            WarpMeshMetrics.Metrics metrics = WarpMeshMetrics.measure(covered);
            assertEquals(0, metrics.foldedFraction(), 0f);
            assertTrue(metrics.minDetRatio() >= WarpPresetFitter.SAFE_MIN_DET_RATIO - 1e-4f);
            assertBorderOutside(covered);
        }
    }

    @Test
    void identityMeshIsPushedJustOutsideTheScreen() {
        WarpMesh source = WarpMeshEvaluator.evaluate(new float[9 * 9 * 2], 9,
                indi.etern.musichud.client.ui.render.warp.WarpMeshInterpolation.B_SPLINE,
                WarpMesh.DEFAULT_RESOLUTION);
        WarpMesh covered = ProceduralWarpCoverage.ensureCoverage(source);
        assertBorderOutside(covered);
    }

    private static void assertBorderOutside(WarpMesh mesh) {
        int columns = mesh.columns();
        int rows = mesh.rows();
        float[] positions = mesh.positions();
        float[] uv = mesh.uv();
        float margin = ProceduralWarpCoverage.MARGIN - 1e-4f;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                if (x != 0 && y != 0 && x != columns - 1 && y != rows - 1) {
                    continue;
                }
                int index = (y * columns + x) * 2;
                assertTrue(uv[index] >= 0 && uv[index] <= 1);
                float px = positions[index];
                float py = positions[index + 1];
                assertTrue(px <= -margin || px >= 1 + margin
                                || py <= -margin || py >= 1 + margin,
                        "border vertex inside screen: " + px + ", " + py);
            }
        }
    }
}
