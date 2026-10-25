package indi.etern.musichud.client.ui.render.warp;

import java.util.Objects;

/**
 * Samples the original analytic fields into a finite control lattice.
 * Offsets are normalized local units; the lattice is independent of the output mesh resolution.
 */
public final class WarpPresetGenerator {
    /** Soft bound for generated offsets; the fitter still removes folds in the evaluated curve. */
    public static final float OFFSET_LIMIT = .85f;
    public static final int MIN_GRID = 2;
    public static final int MAX_GRID = 16;

    private WarpPresetGenerator() {
    }

    public static float[] generateOffsets(WarpPreset preset, int gridSize) {
        Objects.requireNonNull(preset, "preset");
        if (gridSize < MIN_GRID || gridSize > MAX_GRID) {
            throw new IllegalArgumentException("Invalid warp control grid size");
        }
        WarpPreset effective = preset;
        if (preset.type() == WarpPresetType.NOISE || preset.type() == WarpPresetType.NOISE_FLOW) {
            // Keep noise below the control-lattice Nyquist limit; otherwise the interpolation
            // receives a field it cannot represent and local folds become unavoidable.
            float maxScale = Math.max(.5f, (gridSize - 1) * .5f);
            float scale = preset.value(WarpParameter.SCALE);
            if (scale > maxScale) {
                effective = preset.copy();
                effective.setValue(WarpParameter.SCALE, maxScale);
            }
        }
        WarpPresetField field = WarpPresetFields.of(effective.type());
        float[] offsets = new float[gridSize * gridSize * 2];
        float[] sample = new float[2];
        float asymmetry = Math.clamp(effective.value(WarpParameter.ASYMMETRY), -1, 1);
        for (int y = 0; y < gridSize; y++) {
            float v = y / (float) (gridSize - 1);
            for (int x = 0; x < gridSize; x++) {
                float u = x / (float) (gridSize - 1);
                field.sample(u, v, effective, sample);
                float xFactor = 1f + asymmetry * (u - .5f) * 2f;
                float yFactor = 1f + asymmetry * (v - .5f);
                int index = (y * gridSize + x) * 2;
                offsets[index] = softClamp(sample[0] * xFactor, OFFSET_LIMIT);
                offsets[index + 1] = softClamp(sample[1] * yFactor, OFFSET_LIMIT);
            }
        }
        return offsets;
    }

    private static float softClamp(float value, float limit) {
        if (!Float.isFinite(value)) {
            return 0;
        }
        return limit * (float) Math.tanh(value / limit);
    }
}
