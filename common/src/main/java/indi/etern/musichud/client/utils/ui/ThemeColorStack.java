package indi.etern.musichud.client.utils.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public final class ThemeColorStack {
    public static final class Layer {
        private ThemedColors colors;

        private Layer(ThemedColors colors) {
            this.colors = colors;
        }
    }

    private final ThemeColorSource source;
    private final Deque<Layer> layers = new ArrayDeque<>();
    private final Layer base = new Layer(ColorExtractor.getDefaultColors());

    public ThemeColorStack(int durationMs) {
        this.source = new ThemeColorSource(durationMs);
    }

    public ThemeColorStack() {
        this(500);
    }

    public synchronized void setBase(ThemedColors colors) {
        base.colors = colors != null ? colors : ColorExtractor.getDefaultColors();
        if (layers.isEmpty()) {
            source.switchTo(base.colors);
        }
    }

    public synchronized Layer push(ThemedColors colors) {
        if (colors == null) {
            return null;
        }
        Layer layer = new Layer(colors);
        layers.push(layer);
        source.switchTo(colors);
        return layer;
    }

    public synchronized boolean update(Layer layer, ThemedColors colors) {
        if (layer == null || colors == null || !layers.contains(layer)) {
            return false;
        }
        if (layers.peek() == layer && !Objects.equals(layer.colors, colors)) {
            source.switchTo(colors);
        }
        layer.colors = colors;
        return true;
    }

    public synchronized void pop(Layer layer) {
        if (layer == null) {
            return;
        }
        if (layers.peek() == layer) {
            layers.pop();
            showTop();
        } else {
            layers.remove(layer);
        }
    }

    public synchronized void clearLayers() {
        if (layers.isEmpty()) {
            return;
        }
        layers.clear();
        showTop();
    }

    public void update() {
        source.update();
    }

    public ThemedColors getMixed() {
        return source.getMixed();
    }

    private void showTop() {
        Layer top = layers.peek();
        source.switchTo(top != null ? top.colors : base.colors);
    }
}
