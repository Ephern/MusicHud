package indi.etern.musichud.client.ui.render.warp;

import java.util.Objects;
import java.util.Random;

/** Builds a fitted procedural target lattice for the live background. */
public final class ProceduralWarpTargets {
    public static final int GRID_SIZE = 9;
    public static final WarpMeshInterpolation INTERPOLATION = WarpMeshInterpolation.B_SPLINE;
    public static final float ZOOM = 1f;

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
                    WarpPreset preset = WarpPresetLibrary.defaultPreset(WarpPresetType.NOISE_FLOW);
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
                    preset.setStream(true);
                    target = build(preset);
                    defaultTarget = target;
                }
            }
        }
        return target;
    }

    public static Target randomTarget(Random random) {
        Objects.requireNonNull(random, "random");
        WarpPreset preset = WarpPresetLibrary.defaultPreset(WarpPresetType.NOISE_FLOW);
        WarpPresetLibrary.randomize(preset, random);
        return build(preset);
    }

    private static Target build(WarpPreset preset) {
        float[] raw = WarpPresetGenerator.generateOffsets(preset, GRID_SIZE);
        WarpPresetFitter.Result result = WarpPresetFitter.fit(raw, GRID_SIZE,
                INTERPOLATION, ZOOM, WarpPresetFitter.SAFE_MIN_DET_RATIO);
        return new Target(result.offsets(), GRID_SIZE, INTERPOLATION, ZOOM);
    }
}
