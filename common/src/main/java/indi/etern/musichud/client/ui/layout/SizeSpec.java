package indi.etern.musichud.client.ui.layout;

public record SizeSpec(Mode mode, float value) {
    public enum Mode {
        AUTO,
        FIXED,
        PERCENT,
        CONTENT,
        FILL
    }

    public static final SizeSpec AUTO = new SizeSpec(Mode.AUTO, 0);
    public static final SizeSpec CONTENT = new SizeSpec(Mode.CONTENT, 0);
    public static final SizeSpec FILL = new SizeSpec(Mode.FILL, 0);

    public static SizeSpec fixed(float value) {
        return new SizeSpec(Mode.FIXED, value);
    }

    public static SizeSpec percent(float fraction) {
        return new SizeSpec(Mode.PERCENT, fraction);
    }
}
