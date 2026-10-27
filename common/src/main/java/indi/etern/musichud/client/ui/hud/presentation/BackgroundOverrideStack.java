package indi.etern.musichud.client.ui.hud.presentation;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.utils.image.ImageTextureData;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public final class BackgroundOverrideStack {
    public static final class Layer {
        private BackgroundData data;

        private Layer(BackgroundData data) {
            this.data = data;
        }
    }

    private final Deque<Layer> layers = new ArrayDeque<>();
    private ImageTextureData topImage;
    private long topImageChangedAtMillis;

    public synchronized Layer push(BackgroundData data) {
        if (data == null) {
            return null;
        }
        Layer layer = new Layer(data);
        layers.push(layer);
        refreshTopImage();
        return layer;
    }

    public synchronized boolean update(Layer layer, BackgroundData data) {
        if (layer == null || data == null || !layers.contains(layer)) {
            return false;
        }
        layer.data = data;
        refreshTopImage();
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
        refreshTopImage();
    }

    public synchronized void clear() {
        layers.clear();
        refreshTopImage();
    }

    public synchronized BackgroundData getTop() {
        Layer top = layers.peek();
        return top == null ? null : top.data;
    }

    public synchronized long getTopImageChangedAtMillis() {
        return topImageChangedAtMillis;
    }

    private void refreshTopImage() {
        BackgroundData top = getTop();
        ImageTextureData image = top == null ? null : effectiveImage(top);
        if (!Objects.equals(topImage, image)) {
            topImage = image;
            topImageChangedAtMillis = MusicHud.getRunningMillis();
        }
    }

    private static ImageTextureData effectiveImage(BackgroundData data) {
        if (data == null || data.image() == null) {
            return null;
        }
        return data.image().original != null ? data.image().original : data.image().current;
    }
}
