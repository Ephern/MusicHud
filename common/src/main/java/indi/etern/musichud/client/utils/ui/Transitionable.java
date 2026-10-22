package indi.etern.musichud.client.utils.ui;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.Mth;

import java.util.Objects;
import java.util.function.Consumer;

public class Transitionable<T extends Mixable<T>> {
    private final int durationMs;
    @Getter
    public volatile float progress = 0.0f;
    @Getter
    private volatile boolean isTransitioning = false;
    private volatile long transitionStartTime = 0;
    @Getter
    private volatile T current;
    @Getter
    private volatile T next;

    @Setter
    private Consumer<T> onCompleteCallback;

    public Transitionable(T current, int durationMs) {
        this.current = current;
        this.durationMs = durationMs;
    }

    public synchronized void startTransition(T next) {
        if (next == null) {
            return;
        }
        if (current == null) {
            current = next;
            return;
        }
        if (isTransitioning && Objects.equals(this.next, next)) {
            return;
        }
        if (isTransitioning && this.next != null) {
            current = current.mix(this.next, progress);
        }
        if (!isTransitioning && Objects.equals(current, next)) {
            return;
        }
        this.next = next;
        this.transitionStartTime = System.currentTimeMillis();
        this.progress = 0.0f;
        this.isTransitioning = true;
    }

    public synchronized void updateTransition() {
        if (!isTransitioning) {
            return;
        }

        long elapsed = System.currentTimeMillis() - transitionStartTime;
        this.progress = Mth.clamp((float) elapsed / durationMs, 0.0f, 1.0f);
        if (progress >= 1.0f) {
            progress = 0.0f;
            current = next;
            isTransitioning = false;
            if (onCompleteCallback != null) {
                onCompleteCallback.accept(current);
            }
        }
    }

    public synchronized T getMixed() {
        if (isTransitioning && next != null) {
            return current.mix(next, progress);
        }
        return current;
    }
}