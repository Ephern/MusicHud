package indi.etern.musichud.client.audio;

import lombok.Getter;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public enum SoundEngineState {
    LOADING, RUNNING, SHUTDOWN;

    /**
     * ReentrantLock/Condition instead of a monitor: {@code Condition.await()}
     * parks via {@link java.util.concurrent.locks.LockSupport}, so it does not
     * pin the carrier of a virtual thread the way {@code Object.wait()} does.
     */
    private static final ReentrantLock STATE_LOCK = new ReentrantLock();
    private static final Condition STATE_CHANGED = STATE_LOCK.newCondition();

    @Getter
    private static volatile SoundEngineState current = SHUTDOWN;

    /**
     * Bumped every time the SoundEngine is reloaded (F3+T), which destroys and
     * recreates the OpenAL context. Any source/buffer created before the bump
     * belongs to the old context and must be treated as invalid, even if its
     * numeric id happens to be valid (reused) in the new context.
     */
    private static final AtomicLong reloadGeneration = new AtomicLong();

    public static void setCurrent(SoundEngineState state) {
        STATE_LOCK.lock();
        try {
            current = state;
            STATE_CHANGED.signalAll();
        } finally {
            STATE_LOCK.unlock();
        }
    }

    /** Block until the engine is no longer reloading (interruptible). */
    public static void awaitNotLoading() throws InterruptedException {
        STATE_LOCK.lock();
        try {
            while (current == LOADING) {
                STATE_CHANGED.await();
            }
        } finally {
            STATE_LOCK.unlock();
        }
    }

    public static long getReloadGeneration() {
        return reloadGeneration.get();
    }

    public static void onReload() {
        reloadGeneration.incrementAndGet();
    }
}
