package indi.etern.musichud.client.audio;

import lombok.Getter;
import lombok.Setter;

public enum SoundEngineState {
    LOADING, RUNNING, SHUTDOWN;

    @Setter
    @Getter
    @SuppressWarnings("NonFinalFieldInEnum")
    private static SoundEngineState current = SHUTDOWN;
}
