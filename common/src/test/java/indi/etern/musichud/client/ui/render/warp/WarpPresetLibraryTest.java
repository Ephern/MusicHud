package indi.etern.musichud.client.ui.render.warp;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarpPresetLibraryTest {
    @Test
    void noiseFlowRandomizeUsesThePreferredRanges() {
        Random random = new Random(0x5EED);
        for (int i = 0; i < 300; i++) {
            WarpPreset preset = WarpPresetLibrary.defaultPreset(WarpPresetType.NOISE_FLOW);
            WarpPresetLibrary.randomize(preset, random);

            assertBetween(preset, WarpParameter.GLOBAL, 0f, .2f);
            assertBetween(preset, WarpParameter.DETAIL, .25f, .6f);
            assertBetween(preset, WarpParameter.COMPRESS, .8f, 1f);
            assertBetween(preset, WarpParameter.STRETCH, .5f, 1f);
            assertBetween(preset, WarpParameter.SCALE, 3f, 4f);
            assertBetween(preset, WarpParameter.PHASE, 0f, 1f);
            assertBetween(preset, WarpParameter.ASYMMETRY, -1f, 1f);
            assertTrue(preset.value(WarpParameter.OCTAVES) >= 2f
                    && preset.value(WarpParameter.OCTAVES) <= 4f);
            assertTrue(preset.value(WarpParameter.ANGLE) >= 0f
                    && preset.value(WarpParameter.ANGLE) <= 90f);
            assertEquals(1f, preset.value(WarpParameter.PERSPECTIVE));
            assertEquals(1f, preset.value(WarpParameter.ZOOM));
            assertTrue(preset.isStream());
        }
    }

    private static void assertBetween(WarpPreset preset, WarpParameter parameter,
                                      float min, float max) {
        float value = preset.value(parameter);
        assertTrue(value >= min && value <= max,
                parameter + " out of range: " + value + " not in [" + min + ", " + max + "]");
    }
}
