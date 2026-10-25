package indi.etern.musichud.client.ui.render.warp;

import lombok.Getter;
import lombok.Setter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Mutable macro preset state shared by the generator and the debug controls. */
public final class WarpPreset {
    private final WarpPresetType type;
    private final EnumMap<WarpParameter, Float> values = new EnumMap<>(WarpParameter.class);
    @Setter
    private int seed;
    @Getter
    @Setter
    private boolean stream = true;

    public WarpPreset(WarpPresetType type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public WarpPresetType type() {
        return type;
    }

    public int seed() {
        return seed;
    }

    public float value(WarpParameter parameter) {
        Float value = values.get(parameter);
        return value == null ? 0f : value;
    }

    public void setValue(WarpParameter parameter, float value) {
        if (!Float.isFinite(value)) {
            return;
        }
        values.put(Objects.requireNonNull(parameter, "parameter"), value);
    }

    public WarpPreset copy() {
        WarpPreset copy = new WarpPreset(type);
        copy.values.putAll(values);
        copy.seed = seed;
        copy.stream = stream;
        return copy;
    }

    public Map<WarpParameter, Float> values() {
        return Map.copyOf(values);
    }
}
