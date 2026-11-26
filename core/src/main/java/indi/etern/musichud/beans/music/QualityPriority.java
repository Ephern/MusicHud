package indi.etern.musichud.beans.music;

import com.google.gson.reflect.TypeToken;
import indi.etern.musichud.utils.JsonUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Client-owned quality preference. {@code order} contains every selectable quality (display/drag
 * order); {@code disabled} marks the ones the runtime skips. The first enabled entry is preferred.
 */
public record QualityPriority(List<Quality> order, List<Quality> disabled, boolean allowUnblocked) {
    private static final List<Quality> DEFAULT_ENABLED =
            List.of(Quality.LOSSLESS, Quality.EX_HIGH, Quality.STANDARD);

    public QualityPriority {
        order = sanitizeOrder(order);
        List<Quality> normalizedDisabled = sanitizeDisabled(disabled, order);
        if (normalizedDisabled.size() >= order.size()) {
            normalizedDisabled = new ArrayList<>(normalizedDisabled);
            normalizedDisabled.remove(order.getFirst());
        }
        disabled = List.copyOf(normalizedDisabled);
    }

    public static QualityPriority defaults() {
        List<Quality> order = completeOrder(DEFAULT_ENABLED);
        List<Quality> disabled = new ArrayList<>(Quality.selectable());
        disabled.removeAll(DEFAULT_ENABLED);
        return new QualityPriority(order, disabled, true);
    }

    public static QualityPriority orderFromLegacy(Quality legacy) {
        List<Quality> enabled = new ArrayList<>();
        Quality first = legacy == null ? null : legacy.canonical();
        if (first != null && first != Quality.NONE) {
            enabled.add(first);
        }
        for (Quality quality : DEFAULT_ENABLED) {
            if (!enabled.contains(quality)) {
                enabled.add(quality);
            }
        }
        List<Quality> order = completeOrder(enabled);
        List<Quality> disabled = new ArrayList<>(Quality.selectable());
        disabled.removeAll(enabled);
        return new QualityPriority(order, disabled, true);
    }

    public List<Quality> enabledOrder() {
        List<Quality> enabled = new ArrayList<>(order.size());
        for (Quality quality : order) {
            if (!disabled.contains(quality)) {
                enabled.add(quality);
            }
        }
        return List.copyOf(enabled);
    }

    public Quality first() {
        return enabledOrder().getFirst();
    }

    public QualityPriority withOrder(List<Quality> newOrder) {
        return new QualityPriority(newOrder, disabled, allowUnblocked);
    }

    public QualityPriority withDisabled(List<Quality> newDisabled) {
        return new QualityPriority(order, newDisabled, allowUnblocked);
    }

    public QualityPriority withAllowUnblocked(boolean allow) {
        return new QualityPriority(order, disabled, allow);
    }

    public static String toJson(List<Quality> qualities) {
        return JsonUtil.gson.toJson(qualities);
    }

    /** @return an empty list for blank/invalid input, so the caller can fall back to the legacy key. */
    public static List<Quality> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Quality> parsed = JsonUtil.gson.fromJson(
                    json, new TypeToken<List<Quality>>() {
                    }.getType());
            return parsed == null ? List.of() : parsed;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<Quality> completeOrder(List<Quality> enabled) {
        LinkedHashSet<Quality> order = new LinkedHashSet<>();
        for (Quality quality : enabled) {
            if (quality != null && quality != Quality.NONE) {
                order.add(quality.canonical());
            }
        }
        for (Quality quality : Quality.selectable()) {
            order.add(quality.canonical());
        }
        return List.copyOf(order);
    }

    private static List<Quality> sanitizeOrder(List<Quality> input) {
        if (input == null || input.isEmpty()) {
            return completeOrder(DEFAULT_ENABLED);
        }
        LinkedHashSet<Quality> order = new LinkedHashSet<>();
        for (Quality quality : input) {
            if (quality == null || quality == Quality.NONE) {
                continue;
            }
            order.add(quality.canonical());
        }
        for (Quality quality : Quality.selectable()) {
            order.add(quality.canonical());
        }
        return List.copyOf(order);
    }

    private static List<Quality> sanitizeDisabled(List<Quality> input, List<Quality> order) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Quality> disabled = new LinkedHashSet<>();
        for (Quality quality : input) {
            if (quality == null || quality == Quality.NONE) {
                continue;
            }
            Quality canonical = quality.canonical();
            if (order.contains(canonical)) {
                disabled.add(canonical);
            }
        }
        return List.copyOf(disabled);
    }
}
