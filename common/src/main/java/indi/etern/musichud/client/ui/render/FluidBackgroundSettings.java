package indi.etern.musichud.client.ui.render;

public final class FluidBackgroundSettings {
    public static final int GUI_FLUID_BLUR_RADIUS = 512;
    public static final int HUD_FLUID_BLUR_RADIUS = 96;
    public static final float BACKGROUND_OVERALL_BRIGHTNESS = 0.65f;
    public static final float BACKGROUND_NATURAL_SATURATION = 1.75f;
    public static final float COVER_TARGET_LONG_EDGE_SCALE = 0.5f;
    public static final int COVER_TARGET_MIN_LONG_EDGE = 64;
    public static final int COVER_TARGET_MAX_LONG_EDGE = 640;
    public static final float COVER_TRANSITION_SECONDS = 0.5f;
    public static final int BLUR_MAX_LEVELS = 4;
    public static final float COVER_LAYER_OPACITY = 1.0f;
    public static final float HIGHLIGHT_DARKENING = 0.3f;
    public static final float HIGHLIGHT_KNEE = 0.0f;
    public static final float HIGHLIGHT_FULL = 0.75f;
    public static final double AUDIO_SCALE_AMOUNT = 0.5;
    public static final double AUDIO_LOW_WEIGHT = 2.0 / 3.0;
    public static final double AUDIO_MID_WEIGHT = 0.33;
    public static final float COVER_ROTATION_SPEED_DEGREES_PER_SECOND = 12f;
    public static final float COVER_DRIFT_X = 0.25f;
    public static final float COVER_DRIFT_Y = 0.15f;
    public static final float COVER_DRIFT_SPEED = 0.5f;
    public static final float COVER_ORBIT_DRIFT_X = 0.1f;
    public static final float COVER_ORBIT_DRIFT_Y = 0.05f;
    public static final float HUD_FLUID_RADIUS_REFERENCE_HEIGHT = 52f;
    public static final float HUD_FLUID_RADIUS_SOFTNESS = 64f;

    private FluidBackgroundSettings() {
    }
}
