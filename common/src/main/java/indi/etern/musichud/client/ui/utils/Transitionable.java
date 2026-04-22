package indi.etern.musichud.client.ui.utils;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

public class Transitionable<T extends Mixable<T>> {
    private static int durationMs;
    @Getter
    public volatile float progress = 0.0f;
    @Getter
    private volatile boolean isTransitioning = false;
    private volatile long transitionStartTime = 0;
    @Getter
    private T current;
    @Getter
    private T next;

    @Setter
    private Consumer<T> onCompleteCallback;

    public Transitionable(T current, int durationMs) {
        this.current = current;
        Transitionable.durationMs = durationMs;
    }

    public void startTransition(T next) {
        if (current == null) {
            current = next;
        } else {
            this.transitionStartTime = System.currentTimeMillis();
            this.isTransitioning = true;
            this.progress = 0.0f;
            this.next = next;
        }
    }

    public void updateTransition() {
        if (!isTransitioning) return;

        long elapsed = System.currentTimeMillis() - transitionStartTime;
        this.progress = Mth.clamp((float) elapsed / durationMs, 0.0f, 1.0f);
        if (progress >= 1.0f) {
            progress = 0.0f;
            isTransitioning = false;
            if (onCompleteCallback != null) {
                onCompleteCallback.accept(next);
            }
            current = next;
        }
    }

    public T getMixed() {
        if (isTransitioning) {
            return current.mix(next, progress);
        } else {
            return current;
        }
    }
}