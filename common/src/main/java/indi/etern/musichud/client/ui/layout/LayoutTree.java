package indi.etern.musichud.client.ui.layout;

import java.util.Collections;
import java.util.Map;

public final class LayoutTree {
    private final LayoutNode root;
    private final Map<String, LayoutNode> nodes;
    private boolean dirty = true;
    private float lastWidth = -1;
    private float lastHeight = -1;

    LayoutTree(LayoutNode root, Map<String, LayoutNode> nodes) {
        this.root = root;
        this.nodes = Collections.unmodifiableMap(nodes);
    }

    public LayoutNode root() {
        return root;
    }

    public LayoutNode node(String elementId) {
        return nodes.get(elementId);
    }

    public void invalidate() {
        dirty = true;
    }

    public void measureAndArrange(float width, float height) {
        if (!dirty && width == lastWidth && height == lastHeight) {
            return;
        }
        root.resetCollapse();
        root.measure(width, height);
        root.arrange(0, 0);
        lastWidth = width;
        lastHeight = height;
        dirty = false;
    }
}
