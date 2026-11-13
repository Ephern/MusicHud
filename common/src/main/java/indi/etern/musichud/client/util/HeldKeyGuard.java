package indi.etern.musichud.client.util;

import net.minecraft.client.KeyMapping;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Turns the click stream the game produces for a held key into one action per press: GLFW repeats
 * presses while a key is held, so a toggle or queue-advancing binding would otherwise fire ~30 times
 * per second.
 */
public final class HeldKeyGuard {
    private final Map<KeyMapping, Boolean> held = Collections.synchronizedMap(new IdentityHashMap<>());

    /**
     * Runs {@code action} unless the key is still down from the gesture that already ran it.
     *
     * @param releasing whether the binding is currently up; the marker is only dropped after this
     *                  tick's clicks were consumed, so a repeat that was queued in the same tick as
     *                  the release cannot be mistaken for a fresh press
     */
    public void run(KeyMapping mapping, boolean releasing, Consumer<KeyMapping> action) {
        while (mapping.consumeClick()) {
            if (held.putIfAbsent(mapping, Boolean.TRUE) == null) {
                action.accept(mapping);
            }
        }
        if (releasing) {
            held.remove(mapping);
        }
    }
}
