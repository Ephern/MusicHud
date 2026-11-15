package indi.etern.musichud.client.ui.layout;

public sealed interface LayoutSpec permits BoxSpec, LeafSpec {
    BoxStyleSpec style();
}
