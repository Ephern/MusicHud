package indi.etern.musichud.client.ui.render.warp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WarpPresetGeneratorTest {
    @Test
    void zeroAmplitudeProducesAnIdentityLattice() {
        for (WarpPresetType type : WarpPresetType.values()) {
            WarpPreset preset = WarpPresetLibrary.defaultPreset(type);
            if (type == WarpPresetType.NOISE_FLOW) {
                preset.setValue(WarpParameter.GLOBAL, 0);
                preset.setValue(WarpParameter.DETAIL, 0);
                preset.setValue(WarpParameter.STRETCH, 0);
            } else if (type == WarpPresetType.NOISE) {
                preset.setValue(WarpParameter.AMPLITUDE, 0);
                preset.setValue(WarpParameter.STRETCH, 0);
            } else {
                preset.setValue(WarpParameter.AMPLITUDE, 0);
            }
            float[] offsets = WarpPresetGenerator.generateOffsets(preset, 7);
            for (float offset : offsets) {
                assertEquals(0f, offset, 1e-6f);
            }
        }
    }

    @Test
    void generatorIsDeterministicForTheSameSeed() {
        WarpPreset preset = WarpPresetLibrary.defaultPreset(WarpPresetType.NOISE);
        preset.setSeed(42);
        float[] first = WarpPresetGenerator.generateOffsets(preset, 9);
        float[] second = WarpPresetGenerator.generateOffsets(preset, 9);
        assertArrayEquals(first, second, 0f);

        preset.setSeed(43);
        float[] third = WarpPresetGenerator.generateOffsets(preset, 9);
        assertFalse(java.util.Arrays.equals(first, third));
    }

    @Test
    void generatedOffsetsAreFiniteAndBounded() {
        for (WarpPresetType type : WarpPresetType.values()) {
            WarpPreset preset = WarpPresetLibrary.defaultPreset(type);
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
    }

    @Test
    void defaultPresetsActuallyDeform() {
        for (WarpPresetType type : WarpPresetType.values()) {
            WarpPreset preset = WarpPresetLibrary.defaultPreset(type);
            float[] offsets = WarpPresetGenerator.generateOffsets(preset, 9);
            float maximum = 0;
            for (float offset : offsets) {
                maximum = Math.max(maximum, Math.abs(offset));
            }
            assertTrue(maximum > 1e-4f, "Default preset should deform: " + type);
        }
    }

    @Test
    void noiseFlowStreamToggleChangesTheField() {
        WarpPreset preset = WarpPresetLibrary.defaultPreset(WarpPresetType.NOISE_FLOW);
        preset.setStream(true);
        float[] stream = WarpPresetGenerator.generateOffsets(preset, 9);
        preset.setStream(false);
        float[] potential = WarpPresetGenerator.generateOffsets(preset, 9);
        assertFalse(java.util.Arrays.equals(stream, potential));
    }

    @Test
    void noiseFlowCompressionBlendsTheStreamField() {
        WarpPreset preset = WarpPresetLibrary.defaultPreset(WarpPresetType.NOISE_FLOW);
        preset.setValue(WarpParameter.COMPRESS, 0);
        float[] pureStream = WarpPresetGenerator.generateOffsets(preset, 9);
        preset.setValue(WarpParameter.COMPRESS, 1);
        float[] compressedStream = WarpPresetGenerator.generateOffsets(preset, 9);
        assertFalse(java.util.Arrays.equals(pureStream, compressedStream));

        preset.setStream(false);
        float[] potential = WarpPresetGenerator.generateOffsets(preset, 9);
        assertFalse(java.util.Arrays.equals(compressedStream, potential));
    }

    @Test
    void stretchPushesControlPointsFartherOut() {
        for (WarpPresetType type : new WarpPresetType[]{
                WarpPresetType.NOISE, WarpPresetType.NOISE_FLOW}) {
            WarpPreset preset = WarpPresetLibrary.defaultPreset(type);
            preset.setValue(WarpParameter.STRETCH, 0);
            float[] base = WarpPresetGenerator.generateOffsets(preset, 9);
            preset.setValue(WarpParameter.STRETCH, 1);
            float[] stretched = WarpPresetGenerator.generateOffsets(preset, 9);

            assertFalse(java.util.Arrays.equals(base, stretched), type.toString());
            assertTrue(maxOffset(stretched) > maxOffset(base), type.toString());
        }
    }

    private static float maxOffset(float[] offsets) {
        float maximum = 0;
        for (float offset : offsets) {
            maximum = Math.max(maximum, Math.abs(offset));
        }
        return maximum;
    }

    @Test
    void phaseEvolvesTheNoiseFieldInsteadOfOnlyTranslatingIt() {
        WarpPreset preset = WarpPresetLibrary.defaultPreset(WarpPresetType.NOISE_FLOW);
        preset.setValue(WarpParameter.PHASE, 0);
        float[] first = WarpPresetGenerator.generateOffsets(preset, 9);
        preset.setValue(WarpParameter.PHASE, .8f);
        float[] second = WarpPresetGenerator.generateOffsets(preset, 9);
        assertFalse(java.util.Arrays.equals(first, second));
    }

    @Test
    void invalidGridSizesAreRejected() {
        WarpPreset preset = WarpPresetLibrary.defaultPreset(WarpPresetType.TILT);
        assertThrows(IllegalArgumentException.class,
                () -> WarpPresetGenerator.generateOffsets(preset, 1));
        assertThrows(IllegalArgumentException.class,
                () -> WarpPresetGenerator.generateOffsets(preset,
                        WarpPresetGenerator.MAX_GRID + 1));
    }
}
