package indi.etern.musichud.client.audio;

/**
 * Lifecycle state of a single playback task.
 */
public enum PlaybackState {
    /** Task created, waiting for its start gate to open. */
    PENDING,
    /** Decoder/stream being opened. */
    LOADING,
    /** Audio buffered insufficient for playback. */
    BUFFERING,
    /** Audible, gain ramping from 0 to target. */
    FADING_IN,
    /** Audible at full gain. */
    PLAYING,
    /** Audible, gain ramping down to 0 before teardown. */
    FADING_OUT,
    /** Downloader retrying after an error. */
    RETRYING,
    /** Fatal error. */
    ERROR,
    /** Playback fully ended, resources released. */
    FINISHED
}
