package indi.etern.musichud.client.ui.render.warp;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Original parameter schemas and defaults. The fields use generic analytic deformation
 * primitives and do not contain control-point arrays recovered from another implementation.
 */
public final class WarpPresetLibrary {
    private static final int DEFAULT_SEED = 0x5EED;
    private static final Map<WarpPresetType, WarpPresetDefinition> DEFINITIONS =
            new EnumMap<>(WarpPresetType.class);

    static {
        define(WarpPresetType.TILT,
                spec(WarpParameter.CENTER_X, 0, 1, .01f, .5f),
                spec(WarpParameter.CENTER_Y, 0, 1, .01f, .5f),
                spec(WarpParameter.ANGLE, -90, 90, 1, -25),
                spec(WarpParameter.AMPLITUDE, 0, .5f, .01f, .24f),
                spec(WarpParameter.PERSPECTIVE, 0, 1, .01f, .55f),
                spec(WarpParameter.ASYMMETRY, -1, 1, .01f, .18f));
        define(WarpPresetType.BULGE,
                spec(WarpParameter.CENTER_X, 0, 1, .01f, .55f),
                spec(WarpParameter.CENTER_Y, 0, 1, .01f, .45f),
                spec(WarpParameter.RADIUS, .1f, .8f, .01f, .5f),
                spec(WarpParameter.AMPLITUDE, 0, .5f, .01f, .28f),
                spec(WarpParameter.ANISOTROPY, .3f, 2, .01f, 1.25f),
                spec(WarpParameter.ASYMMETRY, -1, 1, .01f, .12f));
        define(WarpPresetType.SWIRL,
                spec(WarpParameter.CENTER_X, 0, 1, .01f, .5f),
                spec(WarpParameter.CENTER_Y, 0, 1, .01f, .5f),
                spec(WarpParameter.RADIUS, .1f, .9f, .01f, .55f),
                spec(WarpParameter.TWIST, -1.5f, 1.5f, .05f, .8f),
                spec(WarpParameter.AMPLITUDE, 0, .4f, .01f, .3f),
                spec(WarpParameter.ASYMMETRY, -1, 1, .01f, .1f));
        define(WarpPresetType.RIPPLE,
                spec(WarpParameter.ANGLE, -90, 90, 1, 20),
                spec(WarpParameter.WAVELENGTH, .1f, 1, .01f, .55f),
                spec(WarpParameter.AMPLITUDE, 0, .3f, .01f, .16f),
                spec(WarpParameter.DECAY, 0, 4, .1f, 1.8f),
                spec(WarpParameter.PHASE, 0, 1, .01f, .2f),
                spec(WarpParameter.ASYMMETRY, -1, 1, .01f, .08f));
        define(WarpPresetType.PINCH,
                spec(WarpParameter.CENTER_X, 0, 1, .01f, .5f),
                spec(WarpParameter.CENTER_Y, 0, 1, .01f, .5f),
                spec(WarpParameter.ANGLE, -90, 90, 1, 90),
                spec(WarpParameter.RADIUS, .1f, .9f, .01f, .45f),
                spec(WarpParameter.AMPLITUDE, 0, .5f, .01f, .4f),
                spec(WarpParameter.SQUEEZE, -.8f, .8f, .01f, .4f),
                spec(WarpParameter.ASYMMETRY, -1, 1, .01f, .1f));
        define(WarpPresetType.NOISE,
                spec(WarpParameter.SCALE, .5f, 8, .05f, 3.2f),
                spec(WarpParameter.AMPLITUDE, 0, .5f, .01f, .2f),
                spec(WarpParameter.OCTAVES, 1, 4, 1, 2),
                spec(WarpParameter.PHASE, 0, 1, .01f, .3f),
                spec(WarpParameter.STRETCH, 0, 1f, .01f, .4f),
                spec(WarpParameter.ASYMMETRY, -1, 1, .01f, .1f));
        define(WarpPresetType.NOISE_FLOW,
                spec(WarpParameter.GLOBAL, 0, .5f, .01f, .22f),
                spec(WarpParameter.DETAIL, 0, .6f, .01f, .28f),
                spec(WarpParameter.COMPRESS, 0, 1f, .01f, .6f),
                spec(WarpParameter.STRETCH, 0, 1f, .01f, .4f),
                spec(WarpParameter.SCALE, .5f, 8f, .05f, 2.8f),
                spec(WarpParameter.OCTAVES, 1, 4, 1, 2),
                spec(WarpParameter.ANGLE, -90, 90, 1, -18),
                spec(WarpParameter.PHASE, 0, 1, .01f, .25f),
                spec(WarpParameter.PERSPECTIVE, 0, 1, .01f, .55f),
                spec(WarpParameter.ASYMMETRY, -1, 1, .01f, .08f),
                spec(WarpParameter.ZOOM, 1, 1.3f, .01f, 1.12f));
    }

    private WarpPresetLibrary() {
    }

    public static WarpPresetDefinition definition(WarpPresetType type) {
        return Objects.requireNonNull(DEFINITIONS.get(type), "Unknown warp preset type");
    }

    public static WarpPreset defaultPreset(WarpPresetType type) {
        WarpPreset preset = new WarpPreset(type);
        for (WarpParameterSpec spec : definition(type).parameters()) {
            preset.setValue(spec.parameter(), spec.defaultValue());
        }
        preset.setSeed(DEFAULT_SEED);
        preset.setStream(true);
        return preset;
    }

    public static void randomize(WarpPreset preset) {
        randomize(preset, ThreadLocalRandom.current());
    }

    public static void randomize(WarpPreset preset, Random random) {
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(random, "random");
        if (preset.type() == WarpPresetType.NOISE_FLOW) {
            randomizeNoiseFlow(preset, random);
        } else {
            randomizeFromDefinition(preset, random);
        }
        preset.setSeed(random.nextInt());
    }

    private static void randomizeFromDefinition(WarpPreset preset, Random random) {
        for (WarpParameterSpec spec : definition(preset.type()).parameters()) {
            preset.setValue(spec.parameter(),
                    randomValue(random, spec.min(), spec.max(), spec.step()));
        }
    }

    private static void randomizeNoiseFlow(WarpPreset preset, Random random) {
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
    }

    private static float randomValue(Random random, float min, float max, float step) {
        int steps = Math.max(1, Math.round((max - min) / step));
        float value = min + random.nextInt(steps + 1) * step;
        return Math.clamp(value, min, max);
    }

    public static List<WarpPresetType> types() {
        return List.of(WarpPresetType.values());
    }

    private static void define(WarpPresetType type, WarpParameterSpec... specs) {
        DEFINITIONS.put(type, new WarpPresetDefinition(type, List.of(specs)));
    }

    private static WarpParameterSpec spec(WarpParameter parameter, float min, float max,
                                          float step, float defaultValue) {
        return new WarpParameterSpec(parameter, min, max, step, defaultValue);
    }
}
