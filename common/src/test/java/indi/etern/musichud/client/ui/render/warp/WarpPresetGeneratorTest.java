package indi.etern.musichud.client.ui.render.warp;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class WarpPresetGeneratorTest {
    @Test
    void zeroAmplitudeProducesAnIdentityLattice() {
        WarpPreset preset = ProceduralWarpTargets.defaultPreset();
        preset.setValue(WarpParameter.GLOBAL, 0);
        preset.setValue(WarpParameter.DETAIL, 0);
        preset.setValue(WarpParameter.STRETCH, 0);
        float[] offsets = WarpPresetGenerator.generateOffsets(preset, 7);
        for (float offset : offsets) {
            assertEquals(0f, offset, 1e-6f);
        }
    }

    @Test
    void generatorIsDeterministicForTheSameSeed() {
        WarpPreset preset = ProceduralWarpTargets.defaultPreset();
        preset.setSeed(42);
        float[] first = WarpPresetGenerator.generateOffsets(preset, 9);
        float[] second = WarpPresetGenerator.generateOffsets(preset, 9);
        assertArrayEquals(first, second, 0f);

        preset.setSeed(43);
        float[] third = WarpPresetGenerator.generateOffsets(preset, 9);
        assertFalse(Arrays.equals(first, third));
    }

    @Test
    void generatedOffsetsAreFiniteAndBounded() {
        WarpPreset preset = ProceduralWarpTargets.defaultPreset();
        for (int grid = WarpPresetGenerator.MIN_GRID;
             grid <= WarpPresetGenerator.MAX_GRID; grid++) {
            float[] offsets = WarpPresetGenerator.generateOffsets(preset, grid);
            assertEquals(grid * grid * 2, offsets.length);
            for (float offset : offsets) {
                assertTrue(Float.isFinite(offset));
                assertTrue(Math.abs(offset) <= WarpPresetGenerator.OFFSET_LIMIT + 1e-6f);
            }
        }
    }

    @Test
    void defaultPresetActuallyDeforms() {
        WarpPreset preset = ProceduralWarpTargets.defaultPreset();
        float[] offsets = WarpPresetGenerator.generateOffsets(preset, 9);
        assertTrue(maxOffset(offsets) > 1e-4f);
    }

    @Test
    void streamToggleChangesTheField() {
        WarpPreset preset = ProceduralWarpTargets.defaultPreset();
        preset.setStream(true);
        float[] stream = WarpPresetGenerator.generateOffsets(preset, 9);
        preset.setStream(false);
        float[] potential = WarpPresetGenerator.generateOffsets(preset, 9);
        assertFalse(Arrays.equals(stream, potential));
    }

    @Test
    void compressionBlendsTheStreamField() {
        WarpPreset preset = ProceduralWarpTargets.defaultPreset();
        preset.setValue(WarpParameter.COMPRESS, 0);
        float[] pureStream = WarpPresetGenerator.generateOffsets(preset, 9);
        preset.setValue(WarpParameter.COMPRESS, 1);
        float[] compressedStream = WarpPresetGenerator.generateOffsets(preset, 9);
        assertFalse(Arrays.equals(pureStream, compressedStream));

        preset.setStream(false);
        float[] potential = WarpPresetGenerator.generateOffsets(preset, 9);
        assertFalse(Arrays.equals(compressedStream, potential));
    }

    @Test
    void stretchPushesControlPointsFartherOut() {
        WarpPreset preset = ProceduralWarpTargets.defaultPreset();
        preset.setValue(WarpParameter.STRETCH, 0);
        float[] base = WarpPresetGenerator.generateOffsets(preset, 9);
        preset.setValue(WarpParameter.STRETCH, 1);
        float[] stretched = WarpPresetGenerator.generateOffsets(preset, 9);

        assertFalse(Arrays.equals(base, stretched));
        assertTrue(maxOffset(stretched) > maxOffset(base));
    }

    @Test
    void phaseEvolvesTheNoiseField() {
        WarpPreset preset = ProceduralWarpTargets.defaultPreset();
        preset.setValue(WarpParameter.PHASE, 0);
        float[] first = WarpPresetGenerator.generateOffsets(preset, 9);
        preset.setValue(WarpParameter.PHASE, .8f);
        float[] second = WarpPresetGenerator.generateOffsets(preset, 9);
        assertFalse(Arrays.equals(first, second));
    }

    @Test
    void invalidGridSizesAreRejected() {
        WarpPreset preset = ProceduralWarpTargets.defaultPreset();
        assertThrows(IllegalArgumentException.class,
                () -> WarpPresetGenerator.generateOffsets(preset, 1));
        assertThrows(IllegalArgumentException.class,
                () -> WarpPresetGenerator.generateOffsets(preset,
                        WarpPresetGenerator.MAX_GRID + 1));
    }

    private static float maxOffset(float[] offsets) {
        float maximum = 0;
        for (float offset : offsets) {
            maximum = Math.max(maximum, Math.abs(offset));
        }
        return maximum;
    }
}
