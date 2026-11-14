package indi.etern.musichud.client.ui.render.warp;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ProceduralWarpTargetsTest {
    @Test
    void defaultAndRandomTargetsAreFittedNineGridTargets() {
        assertSafe(ProceduralWarpTargets.defaultTarget());
        Random random = new Random(0x5EED);
        float[] first = ProceduralWarpTargets.randomTarget(random).offsets();
        float[] second = ProceduralWarpTargets.randomTarget(random).offsets();
        assertFalse(Arrays.equals(first, second));
        assertSafe(new ProceduralWarpTargets.Target(first, 9,
                WarpMeshInterpolation.B_SPLINE, 1f));
    }

    private static void assertSafe(ProceduralWarpTargets.Target target) {
        assertEquals(9, target.gridSize());
        assertEquals(WarpMeshInterpolation.B_SPLINE, target.interpolation());
        assertEquals(1f, target.zoom());
        assertEquals(target.gridSize() * target.gridSize() * 2, target.offsets().length);
        WarpMesh mesh = WarpMeshEvaluator.evaluate(target.offsets(), target.gridSize(),
                target.interpolation(), WarpMesh.DEFAULT_RESOLUTION, target.zoom());
        WarpMeshMetrics.Metrics metrics = WarpMeshMetrics.measure(mesh);
        assertEquals(0, metrics.foldedFraction(), 0f);
        assertTrue(metrics.minDetRatio() >= WarpPresetFitter.SAFE_MIN_DET_RATIO - 1e-4f);
    }
}
