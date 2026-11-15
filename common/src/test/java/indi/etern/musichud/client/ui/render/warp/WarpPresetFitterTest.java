package indi.etern.musichud.client.ui.render.warp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WarpPresetFitterTest {
    @Test
    void defaultNoiseFlowIsStrictlySafe() {
        assertStrictlySafe(defaultNoiseFlow(), 1.08f);
    }

    @Test
    void extremeNoiseFlowIsStillStrictlySafe() {
        WarpPreset preset = defaultNoiseFlow();
        preset.setValue(WarpParameter.GLOBAL, .5f);
        preset.setValue(WarpParameter.DETAIL, .4f);
        preset.setValue(WarpParameter.SCALE, 4.5f);
        preset.setValue(WarpParameter.OCTAVES, 4);
        preset.setValue(WarpParameter.ASYMMETRY, 1);
        assertStrictlySafe(preset, 1.08f);
    }

    @Test
    void potentialModeIsStrictlySafe() {
        WarpPreset preset = defaultNoiseFlow();
        preset.setStream(false);
        preset.setValue(WarpParameter.DETAIL, .35f);
        assertStrictlySafe(preset, 1.1f);
    }

    @Test
    void stretchRemainsStrictlySafe() {
        WarpPreset flow = defaultNoiseFlow();
        flow.setValue(WarpParameter.STRETCH, 1);
        flow.setValue(WarpParameter.DETAIL, .6f);
        flow.setValue(WarpParameter.COMPRESS, .8f);
        assertStrictlySafe(flow, 1.12f);
    }

    @Test
    void looserGuardAllowsMoreOfTheRawDeformation() {
        WarpPreset preset = defaultNoiseFlow();
        preset.setValue(WarpParameter.GLOBAL, .5f);
        preset.setValue(WarpParameter.DETAIL, .6f);
        preset.setValue(WarpParameter.COMPRESS, 1f);
        preset.setValue(WarpParameter.SCALE, 4.5f);
        preset.setValue(WarpParameter.OCTAVES, 4);
        preset.setValue(WarpParameter.ASYMMETRY, 1f);
        float[] raw = WarpPresetGenerator.generateOffsets(preset, 9);

        WarpPresetFitter.Result strict = WarpPresetFitter.fit(raw, 9,
                WarpMeshInterpolation.B_SPLINE, 1.08f, .25f);
        WarpPresetFitter.Result loose = WarpPresetFitter.fit(raw, 9,
                WarpMeshInterpolation.B_SPLINE, 1.08f, .05f);

        assertTrue(loose.fitScale() >= strict.fitScale());
        assertEquals(0, loose.metrics().foldedFraction(), 0f);
        assertTrue(loose.metrics().minDetRatio() >= .05f - 1e-4f);
    }

    private static WarpPreset defaultNoiseFlow() {
        return ProceduralWarpTargets.defaultPreset();
    }

    private static void assertStrictlySafe(WarpPreset preset, float zoom) {
        float[] raw = WarpPresetGenerator.generateOffsets(preset, 9);
        for (WarpMeshInterpolation interpolation : WarpMeshInterpolation.values()) {
            WarpPresetFitter.Result result = WarpPresetFitter.fit(raw, 9, interpolation, zoom);
            assertEquals(raw.length, result.offsets().length);
            assertTrue(result.fitScale() >= 0 && result.fitScale() <= 1);
            assertEquals(0, result.metrics().foldedFraction(), 0f);
            assertTrue(result.metrics().minDetRatio() >= WarpPresetFitter.SAFE_MIN_DET_RATIO - 1e-4f,
                    "Unsafe relative area for " + interpolation + ": " + result.metrics().minDetRatio());
        }
    }
}
