package indi.etern.musichud.client.utils.ui;

import indi.etern.musichud.MusicHud;
import net.minecraft.util.Mth;

import java.util.Objects;

/**
 * A time-based crossfade between two {@link Mixable} values.
 * <p>
 * Progress is advanced lazily whenever the transition is queried. Callers therefore do not
 * need a render or tick loop to keep the timeline moving; a transition that started while
 * nothing was rendering still reaches its final state on the next query.
 */
public class Transitionable<T extends Mixable<T>> {
    private final int durationMs;
    private final Object lock = new Object();
    private float progress;
    private boolean transitioning;
    private long transitionStartTime;
    private T current;
    private T next;

    public Transitionable(T current, int durationMs) {
        this.current = current;
        this.durationMs = durationMs;
    }

    public void startTransition(T next) {
        if (next == null) {
            return;
        }
        synchronized (lock) {
            if (current == null) {
                current = next;
                return;
            }
            advance(MusicHud.getRunningMillis());
            if (transitioning && Objects.equals(this.next, next)) {
                return;
            }
            if (transitioning && this.next != null) {
                current = current.mix(this.next, progress);
            }
            if (!transitioning && Objects.equals(current, next)) {
                return;
            }
            this.next = next;
            this.transitionStartTime = MusicHud.getRunningMillis();
            this.progress = 0.0f;
            this.transitioning = true;
        }
    }

    public T getMixed() {
        synchronized (lock) {
            advance(MusicHud.getRunningMillis());
            if (transitioning && next != null) {
                return current.mix(next, progress);
            }
            return current;
        }
    }

    public float getProgress() {
        synchronized (lock) {
            advance(MusicHud.getRunningMillis());
            return progress;
        }
    }

    public T getCurrent() {
        synchronized (lock) {
            advance(MusicHud.getRunningMillis());
            return current;
        }
    }

    public T getNext() {
        synchronized (lock) {
            return next;
        }
    }

    public boolean isTransitioning() {
        synchronized (lock) {
            advance(MusicHud.getRunningMillis());
            return transitioning;
        }
    }

    public long getTransitionStartMillis() {
        synchronized (lock) {
            return transitionStartTime;
        }
    }

    private void advance(long nowMillis) {
        if (!transitioning) {
            return;
        }
        long elapsed = nowMillis - transitionStartTime;
        progress = Mth.clamp((float) elapsed / durationMs, 0.0f, 1.0f);
        if (progress >= 1.0f) {
            progress = 0.0f;
            current = next;
            transitioning = false;
        }
    }
}
