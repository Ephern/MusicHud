package indi.etern.musichud;

import lombok.Getter;
import lombok.Setter;

/**
 * Capability flags that only live for the current launch. They are never persisted, so a later
 * launch with a compatible environment restores the user's configured behaviour.
 */
public final class RuntimeFlags {
    @Getter
    @Setter
    private static volatile boolean clientUiAvailable = true;

    private RuntimeFlags() {
    }
}
