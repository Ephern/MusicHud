package indi.etern.musichud.client.audio;

/**
 * Fade configuration for one direction of a playback task. Durations are deterministic per task so
 * every client fades identically and wall-clock synchronization stays intact.
 */
public record Fade(long durationMs, Easing easing) {
    public Fade {
        if (durationMs < 0) {
            throw new IllegalArgumentException("Fade duration cannot be negative: " + durationMs);
        }
    }

    /**
     * Create a fade with the default easing curve.
     */
    public static Fade of(long durationMs) {
        return new Fade(durationMs, Easing.EASE_IN_OUT_SINE);
    }
}
