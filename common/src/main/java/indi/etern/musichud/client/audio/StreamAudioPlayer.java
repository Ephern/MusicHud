package indi.etern.musichud.client.audio;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Traceable;
import indi.etern.musichud.interfaces.ClientConfig;
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
    private final PlaybackSpectrum spectrum = new PlaybackSpectrum(MusicHud.EXECUTOR);
    private final PlaybackSpectrumMixer spectrumMixer = new PlaybackSpectrumMixer();
    private final java.util.IdentityHashMap<PlaybackTask, Long> spectrumSubmissions = new java.util.IdentityHashMap<>();
    private long nextSpectrumSubmission;
    private long spectrumToken;
    private boolean spectrumMuted;
    /**
     * Last spectrum snapshot, published by the audio/mixer thread.
     * <p>
     * The player monitor is held for the whole PCM decode/mix of a chunk, which blocked the render
     * thread (audio-reactive background) for several milliseconds. Readers now take the immutable
     * snapshot instead of the monitor; a frame of staleness is invisible for audio reactivity.
     */
    private volatile SpectrumSnapshot publishedSpectrum = SpectrumSnapshot.silence(0, 0);
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

    /** Read-only session-bound spectrum of the actual current/pending PCM mix. Never blocks. */
    public SpectrumSnapshot getSpectrum() {
        return publishedSpectrum;
    }

    /** Publishes the current snapshot for {@link #getSpectrum()}; callers must hold the monitor. */
    private void publishSpectrumLocked(boolean muted) {
        publishedSpectrum = spectrum.snapshot(muted);
    }

    private void updateSpectrumPause(boolean muted) {
        if (spectrumMuted != muted) {
            spectrumMuted = muted;
            // Discard old wallclock capture windows, while preserving the native FFT history/envelope.
            spectrumMixer.clear();
        }
        spectrum.setPaused(muted);
    }

    public record SpectrumMixDiagnostics(long blocks, long missingSamples,
                                         long contentStartNanos, long contentEndNanos, long assemblyNanos,
                                         long minimumEndLeadNanos, long maximumEndLeadNanos,
                                         SourcePcmClock.Reading currentClock, SourcePcmClock.Reading pendingClock) { }

    public synchronized SpectrumMixDiagnostics getSpectrumMixDiagnostics() {
        return new SpectrumMixDiagnostics(spectrumMixer.mixedBlocks(), spectrumMixer.missingSamples(),
                spectrumMixer.contentStartNanos(), spectrumMixer.contentEndNanos(), spectrumMixer.assemblyNanos(),
                spectrumMixer.minimumEndLeadNanos(), spectrumMixer.maximumEndLeadNanos(),
                currentTask == null ? null : currentTask.pcmClock(), pendingTask == null ? null : pendingTask.pcmClock());
    }

    private synchronized void onPcm(PlaybackTask task, long submission, PcmChunk chunk) {
        Long registered = spectrumSubmissions.get(task);
        if (registered == null || registered != submission || !task.isAudible()) return;
        boolean muted = ClientConfig.getInstance().getMuted();
        updateSpectrumPause(muted);
        publishSpectrumLocked(muted);
        if (muted) return;
        retainAudibleSpectrumSources();
        spectrum.setActive(spectrumToken, true);
        spectrumMixer.accept(submission, chunk, mixed -> spectrum.accept(spectrumToken, mixed));
        publishSpectrumLocked(false);
    }

    private void retainAudibleSpectrumSources() {
        java.util.Set<Long> active = new java.util.HashSet<>();
        for (PlaybackTask task : new PlaybackTask[] {currentTask, pendingTask}) {
            Long submission = spectrumSubmissions.get(task);
            if (task != null && task.isAudible() && submission != null) active.add(submission);
        }
        spectrumMixer.retain(active);
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
        if (spectrumSubmissions.isEmpty()) spectrumToken = spectrum.beginSession();
        long submission = ++nextSpectrumSubmission;
        spectrumSubmissions.put(task, submission);
        task.setPcmSink(chunk -> onPcm(task, submission, chunk));
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
                    if (pendingTask != task || currentTask != current) return;
                    if (current.isAudible()) {
                        current.beginFadeOut(transitionMs);
                    } else {
                        // Outgoing is not producing sound (loading/retrying after a
                        // network drop): it cannot be cross-faded, and leaving it
                        // current would let its retry resurrect and play alongside
                        // the incoming task. Adopt the incoming task and stop it.
                        pendingTask = null;
                        currentTask = task;
                        setStatus(switch (task.state()) {
                            case FADING_IN, PLAYING, FADING_OUT -> Status.PLAYING;
                            case RETRYING -> Status.RETRYING;
                            case ERROR -> Status.ERROR;
                            default -> Status.BUFFERING;
                        });
                        current.cancel();
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
        spectrum.stop();
        spectrumMixer.clear();
        spectrumSubmissions.clear();
        publishSpectrumLocked(ClientConfig.getInstance().getMuted());
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
        retainAudibleSpectrumSources();
        if ((currentTask == null || !currentTask.isAudible()) && (pendingTask == null || !pendingTask.isAudible())) {
            spectrum.setActive(spectrumToken, false);
            publishSpectrumLocked(ClientConfig.getInstance().getMuted());
        }
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
            spectrumSubmissions.remove(finished);
            retainAudibleSpectrumSources();
            if (spectrumSubmissions.isEmpty()) {
                spectrumMixer.clear();
                spectrum.stop();
                publishSpectrumLocked(ClientConfig.getInstance().getMuted());
            }
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
