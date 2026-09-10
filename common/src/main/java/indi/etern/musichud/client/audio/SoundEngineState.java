package indi.etern.musichud.client.audio;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicLong;

public enum SoundEngineState {
    LOADING, RUNNING, SHUTDOWN;

    @Setter
    @Getter
    @SuppressWarnings("NonFinalFieldInEnum")
    private static volatile SoundEngineState current = SHUTDOWN;

    /**
     * Bumped every time the SoundEngine is reloaded (F3+T), which destroys and
     * recreates the OpenAL context. Any source/buffer created before the bump
     * belongs to the old context and must be treated as invalid, even if its
     * numeric id happens to be valid (reused) in the new context.
     */
    private static final AtomicLong reloadGeneration = new AtomicLong();

    public static long getReloadGeneration() {
        return reloadGeneration.get();
    }

    public static void onReload() {
        reloadGeneration.incrementAndGet();
    }
}
