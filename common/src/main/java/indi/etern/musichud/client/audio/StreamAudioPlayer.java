package indi.etern.musichud.client.audio;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Traceable;
import lombok.Getter;
import org.apache.logging.log4j.Logger;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
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
    private final Set<Consumer<Status>> statusChangeListener = new CopyOnWriteArraySet<>();
    private PlaybackTask currentTask;
    private PlaybackTask pendingTask;
    /** Next track buffered ahead of time; not audible until it becomes current/pending. */
    private PlaybackTask preloadedTask;


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

    // audible 的 pending 先淡出自然结束，避免硬取消爆音
    private static void retirePending(PlaybackTask pending) {
        if (pending.isAudible()) {
            pending.beginFadeOut(pending.fadeOut().durationMs());
        } else {
            pending.cancel();
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
        PlaybackTask stale = preloadedTask;
        if (stale != null && stale != task) {
            preloadedTask = null;
            stale.cancel();
        }
        task.addStateListener(state -> onTaskStateChanged(task, state));
        task.finishFuture().whenComplete((unused, throwable) -> onTaskFinished(task));
        task.submitThreads();

        PlaybackTask current = currentTask;
        if (current == null || !current.isAudible()) {
            if (pendingTask != null && pendingTask != task) {
                retirePending(pendingTask);
            }
            pendingTask = null;
            // Adopt the new task before cancelling the old one: {@code cancel()}
            // completes the old task's finish future synchronously, and its
            // onTaskFinished must not see itself as currentTask (which would clear
            // currentTask and reset the global status to IDLE after we switched).
            currentTask = task;
            if (current != null) {
                current.cancel();
            }
            task.openGate();
        } else {
            if (pendingTask != null && pendingTask != task) {
                retirePending(pendingTask);
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

    /**
     * Buffer the given track without making it audible: its workers are started
     * but the start gate stays closed, so only the download side runs. The task is
     * handed to the next {@link #obtainTaskFor} call for the same music, removing
     * the initial buffering cost from the actual switch. Requests for a track that
     * is already preloaded are ignored; a changed next track replaces the old one.
     */
    public synchronized void preload(Traceable<MusicDetail> trace) {
        if (trace == null || trace.value() == null || trace.value().equals(MusicDetail.NONE)) {
            return;
        }
        long musicId = trace.value().getId();
        PlaybackTask existing = preloadedTask;
        if (existing != null && existing.getMusicDetail().getId() == musicId) {
            return;
        }
        if (existing != null) {
            existing.cancel();
        }
        PlaybackTask task = PlaybackTask.of(trace, null);
        preloadedTask = task;
        task.submitThreads();
        LOGGER.debug("Preloading next track: {} (ID: {})", trace.value().getName(), musicId);
    }

    /**
     * Whether {@code task} is allowed to preload its successor right now: only the
     * audible current task may do so, and only while no transition is pending.
     * <p>
     * Without this an outgoing task (still {@code currentTask} until its successor
     * becomes audible) could preload the track <em>after</em> the pending one, i.e.
     * open an audio connection a full track too early, which then risks a dropped
     * or expired stream when that track finally starts.
     */
    synchronized boolean canPreloadFrom(PlaybackTask task) {
        return currentTask == task && pendingTask == null;
    }

    /**
     * Returns the preloaded task when it matches the track and no server start time
     * must be honoured, otherwise builds a fresh task. A non-matching preload is
     * left for {@link #play(PlaybackTask)} to discard.
     */
    public synchronized PlaybackTask obtainTaskFor(Traceable<MusicDetail> trace, ZonedDateTime serverStartTime) {
        PlaybackTask preloaded = preloadedTask;
        if (preloaded != null && serverStartTime == null
                && preloaded.getMusicDetail().getId() == trace.value().getId()) {
            preloadedTask = null;
            return preloaded;
        }
        return PlaybackTask.of(trace, serverStartTime);
    }

    public synchronized CompletableFuture<Void> stop() {
        if (preloadedTask != null) {
            preloadedTask.cancel();
            preloadedTask = null;
        }
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
        // A pending successor will take over in onTaskFinished; never flash IDLE
        // in between (the old task's FINISHED must not mask the already-known next).
        if (state == PlaybackState.FINISHED && pendingTask != null) return;
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
                setStatus(switch (next.state()) {
                    case FADING_IN, PLAYING, FADING_OUT -> Status.PLAYING;
                    case RETRYING -> Status.RETRYING;
                    case ERROR -> Status.ERROR;
                    default -> Status.BUFFERING;
                });
                next.openGate();
            }
        }
    }

    public enum Status {
        IDLE, BUFFERING, PLAYING, RETRYING, ERROR
    }
}
