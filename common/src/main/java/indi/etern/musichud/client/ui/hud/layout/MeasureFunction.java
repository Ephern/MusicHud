package indi.etern.musichud.client.ui.hud.layout;

@FunctionalInterface
public interface MeasureFunction {
    Size measure(float availableWidth, float availableHeight);
}
