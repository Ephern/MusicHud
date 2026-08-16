package indi.etern.musichud.client.audio;

/**
 * Fade configuration for one direction of a playback task.
 * <p>
 * Durations are compile-time constants / deterministically derived per task so that
 * every client applies the same fade and keeps wall-clock synchronization intact
 * (fade slightly shifts the audible portion of a song, which must match across peers).
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
