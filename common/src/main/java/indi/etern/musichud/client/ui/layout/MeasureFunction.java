package indi.etern.musichud.client.ui.layout;

@FunctionalInterface
public interface MeasureFunction {
    Size measure(float availableWidth, float availableHeight);
}
