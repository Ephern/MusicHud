package indi.etern.musichud.client.ui.layout;

import java.util.List;

public record BoxSpec(FlexDirection direction, BoxStyleSpec style, List<LayoutSpec> children) implements LayoutSpec {
    public BoxSpec {
        children = List.copyOf(children);
    }
}
