package indi.etern.musichud.utils;

import indi.etern.musichud.interfaces.Unregister;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Decouples the core network layer from the client upload service: the S2C completion
 * payload lives in {@code core} and cannot reference {@code common}, so it publishes here
 * and the client-side service subscribes.
 */
public final class CloudUploadUpdateNotifier {
    public record Completion(String songId, String resolvedTrackId, boolean success, String message) {
    }

    private static final List<Consumer<Completion>> LISTENERS = new CopyOnWriteArrayList<>();

    private CloudUploadUpdateNotifier() {
    }

    public static Unregister register(Consumer<Completion> listener) {
        LISTENERS.add(listener);
        return () -> LISTENERS.remove(listener);
    }

    public static void notifyCompleted(Completion completion) {
        for (Consumer<Completion> listener : LISTENERS) {
            listener.accept(completion);
        }
    }
}
