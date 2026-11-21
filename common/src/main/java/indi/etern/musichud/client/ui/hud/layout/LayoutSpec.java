package indi.etern.musichud.client.ui.hud.layout;

public sealed interface LayoutSpec permits BoxSpec, LeafSpec {
    BoxStyleSpec style();
}
