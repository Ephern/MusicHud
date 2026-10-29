package indi.etern.musichud.client.ui.render.warp;

import lombok.Getter;
import lombok.Setter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class WarpPreset {
    private final EnumMap<WarpParameter, Float> values = new EnumMap<>(WarpParameter.class);
    @Setter
    private int seed;
    @Getter
    @Setter
    private boolean stream = true;

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
        WarpPreset copy = new WarpPreset();
        copy.values.putAll(values);
        copy.seed = seed;
        copy.stream = stream;
        return copy;
    }

    public Map<WarpParameter, Float> values() {
        return Map.copyOf(values);
    }
}
