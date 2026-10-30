package indi.etern.musichud.client.util;

import net.minecraft.client.KeyMapping;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Turns the click stream the game produces for a held key back into one action per press.
 * <p>
 * {@code KeyboardHandler} runs {@code KeyMapping.click} for every key event whose action is not
 * release, and GLFW repeats presses while a key is held, so {@code while (consumeClick())} fires
 * roughly 30 times per second for a held binding. Bindings that toggle a state or advance a queue
 * must act once per gesture, which is what this guard provides: the action runs on the first press
 * and is suppressed until the key has been released again.
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
