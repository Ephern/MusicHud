package indi.etern.musichud.client.audio;

import indi.etern.musichud.MusicHud;
import lombok.Getter;
import org.apache.logging.log4j.Logger;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Orchestrator of {@link PlaybackTask}s: maintains the global playback status,
 * the currently audible task and the pending (preloading) task, and coordinates
 * cross-fade transitions between them.
 * <p>
 * Switch semantics: when a new task is submitted while another is audible, the
 * transition duration is {@code max(outgoing.fadeOut, incoming.fadeIn)} and BOTH
 * directions ramp over that duration. The incoming task starts downloading
 * (preloading) immediately, overlapping the outgoing fade-out; it begins
 * fading in only after the outgoing task has fully faded out.
 */
public class StreamAudioPlayer {
    /** Default fade duration for the fade-in node of a task. */
    public static final long DEFAULT_FADE_IN_MS = 1000;
    /** Default fade duration for the fade-out node of a task. */
    public static final long DEFAULT_FADE_OUT_MS = 1000;
    private static final Logger LOGGER = MusicHud.getLogger(StreamAudioPlayer.class);
    private static volatile StreamAudioPlayer instance = null;
    private final AtomicReference<Status> status = new AtomicReference<>(Status.IDLE);
    @Getter
    private final Set<Consumer<Status>> statusChangeListener = new HashSet<>();
    private PlaybackTask currentTask;
    private PlaybackTask pendingTask;

    public static StreamAudioPlayer getInstance() {
        if (instance == null) {
            synchronized (StreamAudioPlayer.class) {
                if (instance == null) {
                    instance = new StreamAudioPlayer();
                }
            }
        }
        return instance;
    }

    public Status getStatus() {
        return status.get();
    }

    private void setStatus(Status status) {
        if (this.status.get() != status) {
            this.status.set(status);
            statusChangeListener.forEach(c -> c.accept(status));
        }
    }

    /**
     * Submit a playback task. If another task is currently audible, a cross-fade
     * transition is scheduled; otherwise the task starts as soon as its audio is
     * buffered. The returned future completes with the effective wall-clock start
     * time when this task's fade-in actually begins (or exceptionally if the task
     * is superseded before it started).
     */
    public synchronized CompletableFuture<ZonedDateTime> play(PlaybackTask task) {
        task.addStateListener(state -> onTaskStateChanged(task, state));
        task.finishFuture().whenComplete((unused, throwable) -> onTaskFinished(task));
        task.submitThreads();

        PlaybackTask current = currentTask;
        if (current == null || !current.isAudible()) {
            if (pendingTask != null && pendingTask != task) {
                pendingTask.cancel();
            }
            pendingTask = null;
            if (current != null) {
                current.cancel();
            }
            currentTask = task;
            task.openGate();
        } else {
            if (pendingTask != null && pendingTask != task) {
                pendingTask.cancel();
            }
            pendingTask = task;
            long transitionMs = Math.max(current.fadeOut().durationMs(), task.fadeIn().durationMs());
            LOGGER.debug("Cross-fading: {} ms (out fadeOut={} ms, in fadeIn={} ms)",
                    transitionMs, current.fadeOut().durationMs(), task.fadeIn().durationMs());
            task.applyTransition(transitionMs);
            task.openGate();
            task.startFuture().whenComplete((wallStart, throwable) -> {
                if (throwable != null) return;
                synchronized (StreamAudioPlayer.this) {
                    if (pendingTask == task && currentTask == current && current.isAudible()) {
                        current.beginFadeOut(transitionMs);
                    }
                }
            });
        }
        return task.startFuture();
    }

    public synchronized CompletableFuture<Void> stop() {
        if (pendingTask != null) {
            pendingTask.cancel();
            pendingTask = null;
        }
        PlaybackTask current = currentTask;
        if (current == null) {
            setStatus(Status.IDLE);
            return CompletableFuture.completedFuture(null);
        }
        if (!current.isAudible()) {
            current.cancel();
            currentTask = null;
            setStatus(Status.IDLE);
            return CompletableFuture.completedFuture(null);
        }
        current.beginFadeOut(current.fadeOut().durationMs());
        return current.finishFuture();
    }

    private synchronized void onTaskStateChanged(PlaybackTask task, PlaybackState state) {
        if (task != currentTask) return;
        Status newStatus = switch (state) {
            case PENDING, LOADING, BUFFERING -> Status.BUFFERING;
            case FADING_IN, PLAYING, FADING_OUT -> Status.PLAYING;
            case RETRYING -> Status.RETRYING;
            case ERROR -> Status.ERROR;
            case FINISHED -> Status.IDLE;
        };
        setStatus(newStatus);
    }

    private void onTaskFinished(PlaybackTask finished) {
        synchronized (this) {
            if (currentTask != finished) return;
            currentTask = null;
            PlaybackTask next = pendingTask;
            if (next == null) {
                setStatus(Status.IDLE);
            } else {
                pendingTask = null;
                currentTask = next;
                // 提升时按 next 实际状态设全局 Status（next 可能已在交叉淡化期间静默起播）
                setStatus(next.isAudible() ? Status.PLAYING : Status.BUFFERING);
                next.openGate();
            }
        }
    }

    public enum Status {
        IDLE, BUFFERING, PLAYING, RETRYING, ERROR
    }
}
