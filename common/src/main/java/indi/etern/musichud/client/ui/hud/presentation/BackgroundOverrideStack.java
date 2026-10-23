package indi.etern.musichud.client.ui.hud.presentation;

import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;

import java.util.ArrayDeque;
import java.util.Deque;

public final class BackgroundOverrideStack {
    public static final class Layer {
        private BackgroundData data;

        private Layer(BackgroundData data) {
            this.data = data;
        }
    }

    private final Deque<Layer> layers = new ArrayDeque<>();

    public synchronized Layer push(BackgroundData data) {
        if (data == null) {
            return null;
        }
        Layer layer = new Layer(data);
        layers.push(layer);
        return layer;
    }

    public synchronized boolean update(Layer layer, BackgroundData data) {
        if (layer == null || data == null || !layers.contains(layer)) {
            return false;
        }
        layer.data = data;
        return true;
    }

    public synchronized void pop(Layer layer) {
        if (layer == null) {
            return;
        }
        if (layers.peek() == layer) {
            layers.pop();
        } else {
            layers.remove(layer);
        }
    }

    public synchronized void clear() {
        layers.clear();
    }

    public synchronized BackgroundData getTop() {
        Layer top = layers.peek();
        return top == null ? null : top.data;
    }
}
