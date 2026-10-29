package indi.etern.musichud.client.ui.render.warp;

import java.util.Objects;
import java.util.Random;

public final class ProceduralWarpTargets {
    public static final int GRID_SIZE = 9;
    public static final WarpMeshInterpolation INTERPOLATION = WarpMeshInterpolation.B_SPLINE;
    public static final float ZOOM = 1f;

    private static final int DEFAULT_SEED = 0x5EED;

    public record Target(float[] offsets, int gridSize, WarpMeshInterpolation interpolation,
                         float zoom) {
    }

    private static volatile Target defaultTarget;

    private ProceduralWarpTargets() {
    }

    public static Target defaultTarget() {
        Target target = defaultTarget;
        if (target == null) {
            synchronized (ProceduralWarpTargets.class) {
                target = defaultTarget;
                if (target == null) {
                    target = build(defaultPreset());
                    defaultTarget = target;
                }
            }
        }
        return target;
    }

    public static Target randomTarget(Random random) {
        Objects.requireNonNull(random, "random");
        WarpPreset preset = new WarpPreset();
        randomize(preset, random);
        return build(preset);
    }

    public static WarpPreset defaultPreset() {
        WarpPreset preset = new WarpPreset();
        preset.setSeed(DEFAULT_SEED);
        preset.setStream(true);
        preset.setValue(WarpParameter.GLOBAL, .1f);
        preset.setValue(WarpParameter.DETAIL, .425f);
        preset.setValue(WarpParameter.COMPRESS, .9f);
        preset.setValue(WarpParameter.STRETCH, .75f);
        preset.setValue(WarpParameter.SCALE, 3.5f);
        preset.setValue(WarpParameter.OCTAVES, 3);
        preset.setValue(WarpParameter.ANGLE, 45);
        preset.setValue(WarpParameter.PHASE, .5f);
        preset.setValue(WarpParameter.ASYMMETRY, 0f);
        preset.setValue(WarpParameter.PERSPECTIVE, 1f);
        preset.setValue(WarpParameter.ZOOM, 1f);
        return preset;
    }

    public static void randomize(WarpPreset preset, Random random) {
        preset.setValue(WarpParameter.GLOBAL, randomValue(random, 0f, .2f, .01f));
        preset.setValue(WarpParameter.DETAIL, randomValue(random, .25f, .6f, .01f));
        preset.setValue(WarpParameter.COMPRESS, randomValue(random, .8f, 1f, .01f));
        preset.setValue(WarpParameter.STRETCH, randomValue(random, .5f, 1f, .01f));
        preset.setValue(WarpParameter.SCALE, randomValue(random, 3f, 4f, .05f));
        preset.setValue(WarpParameter.OCTAVES, 2 + random.nextInt(3));
        preset.setValue(WarpParameter.ANGLE, random.nextInt(91));
        preset.setValue(WarpParameter.PHASE, randomValue(random, 0f, 1f, .01f));
        preset.setValue(WarpParameter.ASYMMETRY, randomValue(random, -1f, 1f, .01f));
        preset.setValue(WarpParameter.PERSPECTIVE, 1f);
        preset.setValue(WarpParameter.ZOOM, 1f);
        preset.setStream(true);
        preset.setSeed(random.nextInt());
    }

    private static Target build(WarpPreset preset) {
        float[] raw = WarpPresetGenerator.generateOffsets(preset, GRID_SIZE);
        WarpPresetFitter.Result result = WarpPresetFitter.fit(raw, GRID_SIZE,
                INTERPOLATION, ZOOM, WarpPresetFitter.SAFE_MIN_DET_RATIO);
        return new Target(result.offsets(), GRID_SIZE, INTERPOLATION, ZOOM);
    }

    private static float randomValue(Random random, float min, float max, float step) {
        int steps = Math.max(1, Math.round((max - min) / step));
        float value = min + random.nextInt(steps + 1) * step;
        return Math.clamp(value, min, max);
    }
}
