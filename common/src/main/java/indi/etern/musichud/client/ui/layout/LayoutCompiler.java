package indi.etern.musichud.client.ui.layout;

import java.util.HashMap;
import java.util.Map;

public final class LayoutCompiler {
    private LayoutCompiler() {
    }

    public static LayoutTree compile(LayoutSpec spec, LayoutSlotRegistry slots) {
        Map<String, LayoutNode> nodes = new HashMap<>();
        LayoutNode root = compile(spec, slots, nodes);
        return new LayoutTree(root, nodes);
    }

    private static LayoutNode compile(LayoutSpec spec, LayoutSlotRegistry slots,
                                      Map<String, LayoutNode> nodes) {
        if (spec instanceof BoxSpec(FlexDirection direction, BoxStyleSpec style, java.util.List<LayoutSpec> children)) {
            FlexBox node = direction == FlexDirection.ROW ? new HBox() : new VBox();
            node.apply(style);
            for (LayoutSpec child : children) {
                node.add(compile(child, slots, nodes));
            }
            return node;
        }
        LeafSpec leaf = (LeafSpec) spec;
        LayoutSlotRegistry.Slot slot = slots.slot(leaf.elementId());
        LeafNode node = new LeafNode(slot == null ? null : slot.measure());
        node.apply(leaf.style());
        if (slot != null) {
            node.target = slot.target();
        }
        nodes.put(leaf.elementId(), node);
        return node;
    }
}
