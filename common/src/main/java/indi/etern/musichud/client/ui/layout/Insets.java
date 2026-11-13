package indi.etern.musichud.client.ui.layout;

public record Insets(float left, float top, float right, float bottom) {
    public static final Insets NONE = new Insets(0, 0, 0, 0);

    public static Insets all(float value) {
        return new Insets(value, value, value, value);
    }
}
