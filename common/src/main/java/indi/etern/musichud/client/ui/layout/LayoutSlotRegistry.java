package indi.etern.musichud.client.ui.layout;

import java.util.HashMap;
import java.util.Map;

public final class LayoutSlotRegistry {
    private final Map<String, Slot> slots = new HashMap<>();

    public void register(String elementId, LayoutTarget target) {
        register(elementId, target, null);
    }

    public void register(String elementId, LayoutTarget target, MeasureFunction measure) {
        if (slots.putIfAbsent(elementId, new Slot(target, measure)) != null) {
            throw new IllegalArgumentException("Duplicate layout element: " + elementId);
        }
    }

    public Slot slot(String elementId) {
        return slots.get(elementId);
    }

    public record Slot(LayoutTarget target, MeasureFunction measure) {
    }
}
