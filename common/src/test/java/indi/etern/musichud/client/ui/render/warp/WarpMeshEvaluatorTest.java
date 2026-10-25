package indi.etern.musichud.client.ui.render.warp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WarpMeshEvaluatorTest {
    @Test
    void zeroOffsetsProduceTheIdentityMesh() {
        int grid = 5;
        int resolution = 17;
        float[] offsets = new float[grid * grid * 2];
        WarpMesh mesh = WarpMeshEvaluator.evaluate(offsets, grid,
                WarpMeshInterpolation.B_SPLINE, resolution);
        assertEquals(resolution, mesh.columns());
        assertEquals(resolution, mesh.rows());
        assertEquals(resolution * resolution * 2, mesh.positions().length);
        assertEquals((resolution - 1) * (resolution - 1) * 6, mesh.indices().length);
        for (int i = 0; i < mesh.positions().length; i++) {
            assertEquals(mesh.uv()[i], mesh.positions()[i], 1e-6f);
        }
        for (int y = 0; y < grid; y++) {
            for (int x = 0; x < grid; x++) {
                int index = (y * grid + x) * 2;
                assertEquals(x / (float) (grid - 1), mesh.controlPoints()[index], 1e-6f);
                assertEquals(y / (float) (grid - 1), mesh.controlPoints()[index + 1], 1e-6f);
            }
        }
    }

    @Test
    void bothInterpolationsStayFiniteForDefaultPresets() {
        for (WarpPresetType type : WarpPresetType.values()) {
            WarpPreset preset = WarpPresetLibrary.defaultPreset(type);
            for (int grid = 3; grid <= 9; grid++) {
                float[] offsets = WarpPresetGenerator.generateOffsets(preset, grid);
                for (WarpMeshInterpolation interpolation : WarpMeshInterpolation.values()) {
                    WarpMesh mesh = WarpMeshEvaluator.evaluate(offsets, grid,
                            interpolation, WarpMesh.DEFAULT_RESOLUTION);
                    assertEquals(WarpMesh.DEFAULT_RESOLUTION, mesh.columns());
                    assertEquals(offsets.length, mesh.controlPoints().length);
                    for (int i = 0; i < mesh.positions().length; i++) {
                        assertTrue(Float.isFinite(mesh.positions()[i]));
                    }
                    for (short index : mesh.indices()) {
                        assertTrue(index >= 0 && index < mesh.columns() * mesh.rows());
                    }
                }
            }
        }
    }

    @Test
    void zoomScalesPositionsAwayFromCenterButKeepsUv() {
        float[] offsets = new float[5 * 5 * 2];
        WarpMesh mesh = WarpMeshEvaluator.evaluate(offsets, 5,
                WarpMeshInterpolation.B_SPLINE, 17, 1.2f);
        int center = ((17 / 2) * 17 + (17 / 2)) * 2;
        assertEquals(.5f, mesh.positions()[center], 1e-6f);
        assertEquals(.5f, mesh.positions()[center + 1], 1e-6f);
        assertEquals(0, mesh.uv()[0], 1e-6f);
        assertEquals(1, mesh.uv()[(17 - 1) * 2], 1e-6f);
        assertEquals(.5f + (0 - .5f) * 1.2f, mesh.positions()[0], 1e-6f);
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> WarpMeshEvaluator.evaluate(new float[0], 3,
                        WarpMeshInterpolation.B_SPLINE, 17));
        assertThrows(IllegalArgumentException.class,
                () -> WarpMeshEvaluator.evaluate(new float[18], 3,
                        WarpMeshInterpolation.B_SPLINE, 1));
    }
}
