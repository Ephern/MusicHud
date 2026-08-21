package indi.etern.musichud.client.audio;

/**
 * Thrown when an OpenAL source has become invalid (AL_INVALID_NAME) during a
 * gain or playback operation; the orchestrator decides whether to rebuild.
 */
public class SourceInvalidException extends RuntimeException {
    public SourceInvalidException(String message) {
        super(message);
    }
}
