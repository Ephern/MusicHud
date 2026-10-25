package indi.etern.musichud.client.ui.render.warp;

import java.util.List;
import java.util.Objects;

/** The macro parameter schema for one preset family. */
public record WarpPresetDefinition(WarpPresetType type, List<WarpParameterSpec> parameters) {
    public WarpPresetDefinition {
        Objects.requireNonNull(type, "type");
        parameters = List.copyOf(parameters);
        if (parameters.isEmpty()) {
            throw new IllegalArgumentException("Warp preset needs at least one parameter");
        }
    }
}
