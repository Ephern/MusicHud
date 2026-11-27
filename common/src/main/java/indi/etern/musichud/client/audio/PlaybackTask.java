package indi.etern.musichud.client.audio;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.option.MultichannelMode;
import indi.etern.musichud.client.audio.decoder.*;
import indi.etern.musichud.client.audio.info.*;
import indi.etern.musichud.client.audio.source.SpoolBuffer;
import indi.etern.musichud.client.interfaces.IClientEventService;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.hud.presentation.HudPresenter;
import indi.etern.musichud.client.utils.PlayerInfoUtil;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.network.IClientNetworkService;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.pushMessages.c2s.ScrobbleMessage;
import indi.etern.musichud.network.payloads.requestResponseCycle.GetMusicResourceRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.GetMusicResourceResponse;
import lombok.Getter;
import lombok.SneakyThrows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundSource;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL10;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Playback task for a single song: owns the decoder, OpenAL source, prefetch
 * queue, worker threads and the wall-clock alignment state.
 */
public class PlaybackTask {
    private static final int BUFFER_COUNT = 8;
    private static final int BUFFER_SIZE = 65536;
    /** Device-side queue target; absorbs client hitches without moving the content clock. */
    private static final long DEVICE_QUEUE_TARGET_MS = 3000;
    private static final int MIN_DEVICE_BUFFERS = BUFFER_COUNT;
    private static final int MAX_DEVICE_BUFFERS = 48;
    /** Deficit beyond which playback is realigned in one step instead of dropped chunk by chunk. */
    private static final long RESYNC_THRESHOLD_MS = 300;
    private static final long RESYNC_FADE_IN_MS = 150;
    /** One-shot play-worker stall for verifying queue headroom: {@code -Dmusic_hud.debug.stallMs}. */
    private static final long DEBUG_STALL_MS = Long.getLong("music_hud.debug.stallMs", 0);
    /** In-memory PCM look-ahead; 16 MiB is ~22 s @ 24-bit 96 kHz stereo, ~95 s @ 16-bit 44.1 kHz. */
    private static final int AUDIO_BUFFER_BUDGET_BYTES = 16 * 1024 * 1024;
    private static final int AUDIO_BUFFER_CAPACITY = Math.max(64, AUDIO_BUFFER_BUDGET_BYTES / BUFFER_SIZE);
    /**
     * Consecutive {@code alBufferData} failures required before permanently
     * switching a discrete stream to stereo downmix (one failure can be transient).
     */
    private static final int DISCRETE_REJECTION_CONFIRMATIONS = 2;
    private static final long PLAYBACK_STALL_LOG_MS = 500;
    private static final long PLAY_LOOP_SLEEP_MS = 40;
    private static final long INITIAL_BUFFER_WAIT_SLEEP_MS = 50;
    private static final long FULLY_RETRY_SLEEP_MS = 1000;
    private static final long DOWNLOAD_RETRY_DELAY_ADDITIONAL_MS = 1000;
    private static final long UNDERFLOW_BUFFERING_DELAY_MS = 300;
    /**
     * Recovery pre-buffer: once the source runs dry the play worker holds
     * playback until this much audio is queued, and stale-drop / catch-up skip
     * are disabled while below it. Prevents a download dip from turning into a
     * stream of small skips. Capped to a reachable fraction of the prefetch
     * capacity by {@link #rebufferTargetBytes(long)}.
     */
    private static final long REBUFFER_TARGET_MS = 3000;
    /**
     * If the download worker enqueues nothing for this long while the source is
     * dry, treat it as unresponsive (blocked socket, or stuck in the catch-up
     * loop) and rebuild the pipeline instead of hanging silently in PLAYING.
     */
    private static final long DOWNLOAD_STALL_RESTART_MS = 30000;
    private static final int SCROBBLE_MIN_PLAY_DURATION_SEC = 30;
    /** Start buffering the next track once this many ms remain in the current one. */
    private static final long NEXT_TRACK_PRELOAD_REMAINING_MS = 5000;
    /**
     * How many times a stream that ends well before the declared duration (a
     * truncated/expired download rather than a real end) is retried with a fresh
     * resource URL before its decoded content is accepted as-is.
     */
    private static final int MAX_TRUNCATION_RETRIES = 3;
    /** Serve audio from a disk spool; {@code -Dmusic_hud.debug.audioSpool=false} streams one connection instead. */
    private static final boolean AUDIO_SPOOL_ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("music_hud.debug.audioSpool"));
    private static final int SPOOL_INPUT_BUFFER_SIZE = 64 * 1024;
    private static final Logger LOGGER = MusicHud.getLogger(PlaybackTask.class);
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private static final IClientNetworkService clientNetworkService = IClientNetworkService.getInstance();

    @Getter
    private final Traceable<MusicDetail> musicTrace;
    @Getter
    private final MusicDetail musicDetail;
    private final Fade fadeIn;
    private final Fade fadeOut;
    private final CompletableFuture<ZonedDateTime> startFuture = new CompletableFuture<>();
    private final CompletableFuture<Void> finishFuture = new CompletableFuture<>();
    private final CountDownLatch gate = new CountDownLatch(1);
    private final Set<Consumer<PlaybackState>> stateListeners = new CopyOnWriteArraySet<>();
    private final PlaybackLedger ledger = new PlaybackLedger();
    /**
     * Serializes OpenAL source creation (play worker) against teardown
     * ({@link #cancel()}/{@link #finish()} via {@link #cleanupInternal()}), so a
     * source can never be created after cancellation has already closed the
     * field. Without this, {@code cancel()} could observe {@code source == null}
     * on a still-loading task and then the play worker's late source would play
     * alongside the successor.
     */
    private final ReentrantLock sourceLock = new ReentrantLock();
    /**
     * Serializes {@link #cleanup()} invocations. A concurrent cancel()/finish()
     * teardown waits for the running one and then runs as well, instead of the
     * former compare-and-set design that silently dropped it and could leave a
     * just-created resource open.
     */
    private final ReentrantLock cleanupLock = new ReentrantLock();
    /** Sync reference: server broadcast start time, lazily set to the local wall start. */
    private volatile ZonedDateTime serverStartTime;
    /** Cross-fade duration set by the scheduler; -1 uses the task's own fade-in. */
    private volatile long transitionFadeInMs = -1;
    private volatile BlockingDeque<byte[]> audioBuffer = new LinkedBlockingDeque<>(AUDIO_BUFFER_CAPACITY);
    /** Chunks recovered from a dead source; drained before {@link #audioBuffer}. */
    private final ConcurrentLinkedDeque<byte[]> recoveredHead = new ConcurrentLinkedDeque<>();
    /**
     * "Enough audio buffered" signal for {@link #waitInitialBuffer()}; a Condition
     * (not a monitor) so waiting doesn't pin virtual-thread carriers.
     */
    private final ReentrantLock bufferLock = new ReentrantLock();
    private final Condition bufferReady = bufferLock.newCondition();
    private volatile PlaybackState state = PlaybackState.PENDING;
    private volatile boolean cancelled = false;
    /**
     * Monotonic "this task must end" flag, set by {@link #beginFadeOut(long)} and
     * never cleared. Unlike {@link #state} a retry cannot overwrite it, so a
     * superseded task can never fade back in and play alongside its successor.
     */
    private volatile boolean ending = false;
    private volatile boolean restartRequested = false;
    private volatile boolean downloadDone = false;
    private volatile OpenAlSource source;
    private volatile PcmSink pcmSink;
    private volatile long fadeStartNanos = -1;
    private volatile long fadeDurationMs = 0;
    private volatile float lastSetGain = -1;
    private float lastVolume = 1;
    private volatile AudioDecoder currentDecoder;
    private int lastDecoderFormat = -1;
    private int lastDecoderSampleRate = -1;
    /** Buffer slots of the current source, sized so it holds about {@link #DEVICE_QUEUE_TARGET_MS}. */
    private volatile int deviceBufferCount = BUFFER_COUNT;
    private boolean debugStallInjected = false;
    /** Consecutive upload failures; reset on a successful prefill. */
    private int discreteRejectionCount = 0;
    /** Rebuilds a dead source at full gain instead of restarting the fade-in. */
    private volatile boolean skipFadeInOnce = false;
    /** Next round drops content that would have played during a reload gap. */
    private volatile boolean resyncOnResume = false;
    /** Set while the play worker holds playback to rebuild its buffer. */
    private volatile boolean rebuffering = false;
    /** Set when the watchdog decides the download worker is unresponsive. */
    private volatile boolean downloadStalled = false;
    /** Wall-clock time of the last successful prefetch enqueue (liveness). */
    private volatile long lastEnqueueMs = System.currentTimeMillis();
    /** Cumulative bytes enqueued into the prefetch buffer (download worker). */
    private final AtomicLong enqueuedBytes = new AtomicLong(0);
    /** Cumulative bytes polled out of the prefetch buffer (play worker). */
    private final AtomicLong polledBytes = new AtomicLong(0);
    /**
     * Enqueue offset at which the downloader last fast-forwarded the decoder in
     * {@link #syncPlaying()}: every buffered byte enqueued before it predates the
     * skip and is stale. Set only when a skip actually happened.
     */
    private final AtomicLong syncSkipBoundary = new AtomicLong(0);
    /**
     * Absolute content position (wall-clock byte space) the decoder was last
     * fast-forwarded to. The play worker advances {@code fedBytes} to it once the
     * stale buffered content is gone, so skipped bytes are accounted exactly once
     * instead of leaving {@code fedBytes} permanently behind the wall clock.
     */
    private final AtomicLong syncSkipTarget = new AtomicLong(0);
    private long lastStaleDropLogTime = 0;
    private long lastUnderrunLogTime = 0;
    private long lastDownloadRestartTimestamp = 0;
    private long underrunSinceNanos = -1;
    private volatile Future<?> downloadThreadFuture;
    private volatile Future<?> playThreadFuture;
    private long lastJmtcTickMs = 0;
    /** Resolved resource (url/quality/size); replaced wholesale, never mutated in place. */
    private final AtomicReference<MusicResourceInfo> resourceInfo =
            new AtomicReference<>(MusicResourceInfo.NONE);
    /** Quality of the resource request that won, used for the client-side source guess. */
    private volatile Quality requestedQuality = Quality.NONE;
    private volatile AudioSource audioSource = AudioSource.UNKNOWN;
    /** Source/output metadata of the open decoder, cleared per download attempt. */
    private volatile DecodedStreamInfo decodedStream;
    private volatile AudioOutputInfo decodedOutput;
    private final AtomicReference<AudioPlaybackInfo> audioInfo = new AtomicReference<>();
    private final Set<Consumer<AudioPlaybackInfo>> audioInfoListeners = new CopyOnWriteArraySet<>();
    private final AtomicLong audioInfoRevision = new AtomicLong();
    private final AtomicBoolean audioInfoLogged = new AtomicBoolean();
    /** Keeps snapshot publication ordered when download/play/spool threads publish concurrently. */
    private final Object audioInfoLock = new Object();
    /** Prevents a failing priority plan from showing the same toast on every retry. */
    private volatile boolean resourceResolveErrorNotified;
    /** Disk-backed network buffer. */
    private volatile SpoolBuffer audioSpool;
    private Unregister scrobbleOnQuitUnregister;
    private volatile boolean scrobbled = false;
    /** Guards {@link #submitThreads()}: a preloaded task must not start its workers twice. */
    private final AtomicBoolean submitted = new AtomicBoolean();
    /** Music id last requested for next-track preload; -1 when none was requested yet. */
    private long preloadTriggeredMusicId = -1;

    private PlaybackTask(Traceable<MusicDetail> musicTrace, ZonedDateTime serverStartTime, Fade fadeIn, Fade fadeOut) {
        this.musicTrace = musicTrace;
        this.musicDetail = musicTrace.value();
        this.serverStartTime = serverStartTime;
        this.fadeIn = fadeIn;
        this.fadeOut = fadeOut;
    }

    public static PlaybackTask of(Traceable<MusicDetail> musicTrace, ZonedDateTime serverStartTime) {
        return new PlaybackTask(musicTrace, serverStartTime,
                Fade.of(StreamAudioPlayer.DEFAULT_FADE_IN_MS), Fade.of(StreamAudioPlayer.DEFAULT_FADE_OUT_MS));
    }

    /** Attach a non-blocking observer of the PCM window currently playing, including rebuilt sources. */
    public void setPcmSink(PcmSink sink) {
        sourceLock.lock();
        try {
            pcmSink = sink;
            if (source != null) source.setPcmSink(sink);
        } finally {
            sourceLock.unlock();
        }
    }

    public Fade fadeIn() {
        return fadeIn;
    }

    public Fade fadeOut() {
        return fadeOut;
    }

    /** Completes with the wall-clock start time when the task becomes audible. */
    public CompletableFuture<ZonedDateTime> startFuture() {
        return startFuture;
    }

    public CompletableFuture<Void> finishFuture() {
        return finishFuture;
    }

    public PlaybackState state() {
        return state;
    }

    public SourcePcmClock.Reading pcmClock() {
        OpenAlSource current = source;
        return current == null ? null : current.getPcmClock();
    }

    /** Last successfully applied OpenAL gain, including volume and fades. */
    public float appliedGain() {
        OpenAlSource current = source;
        return current == null ? 0 : current.getAppliedGain();
    }

    public boolean isAudible() {
        PlaybackState s = state;
        return s == PlaybackState.FADING_IN || s == PlaybackState.PLAYING || s == PlaybackState.FADING_OUT;
    }

    public void addStateListener(Consumer<PlaybackState> listener) {
        stateListeners.add(listener);
    }

    /** Last published metadata snapshot, or {@code null} before a resource is resolved. */
    @Nullable
    public AudioPlaybackInfo audioInfo() {
        return audioInfo.get();
    }

    /**
     * Observe metadata snapshots. If a snapshot already exists it is delivered once on the
     * caller's thread; later snapshots arrive from the download/play/spool worker threads, so
     * consumers must dispatch UI work themselves and may drop callbacks by {@code revision}.
     */
    public Unregister addAudioInfoListener(Consumer<AudioPlaybackInfo> listener) {
        audioInfoListeners.add(listener);
        AudioPlaybackInfo current = audioInfo.get();
        if (current != null) {
            deliverAudioInfo(listener, current);
        }
        return () -> audioInfoListeners.remove(listener);
    }

    private void deliverAudioInfo(Consumer<AudioPlaybackInfo> listener, AudioPlaybackInfo snapshot) {
        try {
            listener.accept(snapshot);
        } catch (Throwable t) {
            LOGGER.warn("Audio info listener failed", t);
        }
    }

    private void publishAudioInfo() {
        AudioPlaybackInfo snapshot;
        synchronized (audioInfoLock) {
            snapshot = buildAudioInfo();
            audioInfo.set(snapshot);
        }
        logAudioInfoOnce(snapshot);
        for (Consumer<AudioPlaybackInfo> listener : audioInfoListeners) {
            deliverAudioInfo(listener, snapshot);
        }
    }

    private AudioPlaybackInfo buildAudioInfo() {
        MusicResourceInfo resource = resourceInfo.get();
        AudioStreamInfo stream = buildStreamInfo(decodedStream);
        Quality reported = resource.getQuality().canonical();
        Quality estimated = QualityEstimator.estimate(stream).canonical();
        Quality effective = QualityEstimator.effective(reported, estimated, stream);
        return new AudioPlaybackInfo(
                audioInfoRevision.incrementAndGet(),
                musicDetail == null ? -1 : musicDetail.getId(),
                requestedQuality,
                reported,
                estimated,
                effective,
                QualityEstimator.isMismatch(reported, estimated, effective),
                audioSource,
                resource.getType(),
                resource.getBitrate(),
                resource.getSize(),
                stream,
                decodedOutput
        );
    }

    private @Nullable AudioStreamInfo buildStreamInfo(@Nullable DecodedStreamInfo decoded) {
        if (decoded == null) return null;
        int declaredMs = musicDetail == null ? 0 : Math.max(0, musicDetail.getDurationMillis());
        long durationMs = decoded.totalSamples() > 0 && decoded.sampleRate() > 0
                ? decoded.totalSamples() * 1000L / decoded.sampleRate()
                : declaredMs;
        int bitrateBps = decoded.bitrateBps();
        long sizeBytes = resourceInfo.get().getSize();
        // Lossless containers carry no bitrate field; derive it from the object size.
        if (bitrateBps <= 0 && durationMs > 0 && sizeBytes > 0) {
            bitrateBps = (int) Math.min(Integer.MAX_VALUE, sizeBytes * 8000.0 / durationMs);
        }
        return new AudioStreamInfo(decoded.container(), decoded.channelLayout(), decoded.sampleRate(),
                decoded.channels(), decoded.bitsPerSample(), bitrateBps, decoded.totalSamples(),
                durationMs, decoded.variableBitrate());
    }

    private boolean updateDecoderInfo(AudioDecoder decoder) {
        DecodedStreamInfo stream = decoder.getStreamInfo();
        AudioOutputInfo output = decoder.getOutputInfo();
        boolean changed = !Objects.equals(decodedStream, stream) || !Objects.equals(decodedOutput, output);
        decodedStream = stream;
        decodedOutput = output;
        return changed;
    }

    private void publishResourceInfo(Quality requested, AudioSource source) {
        requestedQuality = requested == null ? Quality.NONE : requested.canonical();
        audioSource = source;
        publishAudioInfo();
    }

    private void logAudioInfoOnce(AudioPlaybackInfo info) {
        if (info.stream() == null || info.output() == null || !audioInfoLogged.compareAndSet(false, true)) {
            return;
        }
        LOGGER.debug("Audio metadata resolved: name='{}', id={}, requested={}, reported={}, estimated={}, "
                        + "effective={}, mismatch={}, source={}, reportedType={}, reportedBitrate={} bps, "
                        + "size={} B, stream={}, output={}",
                musicDetail == null ? "?" : musicDetail.getName(), info.musicId(),
                info.requestedQuality().name(), info.reportedQuality().name(),
                info.estimatedQuality().name(), info.effectiveQuality().name(),
                info.qualityMismatch(), info.source(),
                info.reportedType(), info.reportedBitrateBps(), info.sizeBytes(),
                info.stream(), info.output());
    }

    /**
     * Start the download & play workers once at submission time. Retries only
     * restart the download thread; the play worker loops within itself.
     */
    void submitThreads() {
        if (!submitted.compareAndSet(false, true)) {
            return;
        }
        downloadThreadFuture = MusicHud.EXECUTOR.submit(this::downloadLoop);
        playThreadFuture = MusicHud.EXECUTOR.submit(this::playLoop);
        scrobbleOnQuitUnregister = IClientEventService.getInstance().registerClientPlayerQuit((player) -> scrobble());
    }

    /**
     * Apply the transition fade-in duration set by the orchestrator for a
     * cross-fade (must be called before the play worker starts its fade-in).
     */
    void applyTransition(long transitionMs) {
        this.transitionFadeInMs = transitionMs;
    }

    /**
     * Release the start gate; the play worker proceeds once audio is buffered.
     */
    void openGate() {
        gate.countDown();
    }

    /**
     * Request a fade-out with the given duration (switch transitions use the
     * longer of the outgoing fade-out and incoming fade-in). Idempotent.
     */
    public void beginFadeOut(long durationMs) {
        ending = true;
        fadeDurationMs = Math.max(0, durationMs);
        fadeStartNanos = System.nanoTime();
        setState(PlaybackState.FADING_OUT);
    }

    /** Cancel immediately: interrupt workers, release resources, complete both futures. */
    public void cancel() {
        if (cancelled) return;
        cancelled = true;
        LOGGER.debug("Playback task cancelled: {} (ID: {}, state: {}, audible: {})",
                musicDetail == null ? "?" : musicDetail.getName(),
                musicDetail == null ? -1 : musicDetail.getId(), state, isAudible());
        // A cancelled task never played through (preloads, replaced pending tasks), so it must
        // not emit a scrobble when the play worker unwinds through finish().
        scrobbled = true;
        stopDownloadWorker();
        if (playThreadFuture != null) playThreadFuture.cancel(true);
        gate.countDown();
        startFuture.completeExceptionally(new CancellationException("Playback task cancelled"));
        cleanup();
        releaseSpool();
        finishFuture.complete(null);
    }

    /** Stop the download worker and interrupt it before cleanup closes its decoder. */
    private void stopDownloadWorker() {
        restartRequested = true;
        Future<?> future = downloadThreadFuture;
        if (future != null) {
            future.cancel(true);
            downloadThreadFuture = null;
        }
    }

    private void setState(PlaybackState state) {
        if (this.state != state) {
            this.state = state;
            stateListeners.forEach(listener -> listener.accept(state));
        }
    }

    private double fadeProgress() {
        if (fadeStartNanos < 0 || fadeDurationMs <= 0) return 1.0;
        return (double) (System.nanoTime() - fadeStartNanos) / 1_000_000 / fadeDurationMs;
    }

    @SuppressWarnings("BusyWait")
    private void downloadLoop() {
        Thread.currentThread().setName("MHWorker-Downloader");
        int localRetryCount = 0;
        int truncationRetryCount = 0;
        boolean forceSync = serverStartTime != null;
        boolean forceResourceRefresh = false;
        resourceInfo.set(MusicResourceInfo.NONE);
        while (!cancelled && !restartRequested) {
            int trial = localRetryCount + 1;
            try {
                decodedStream = null;
                decodedOutput = null;
                MusicResourceInfo resource = resourceInfo.get();
                if (forceResourceRefresh || !resource.isUsable() || localRetryCount % 3 == 0) {
                    forceResourceRefresh = false;
                    ResolvedResource resolved = resolveResource(clientConfig.getQualityPriority(), resource);
                    resource = resolved.info();
                    if (!resource.isUsable()) {
                        if (!resourceResolveErrorNotified) {
                            resourceResolveErrorNotified = true;
                            ToastUtil.show(I18n.get(MusicHud.MOD_ID + ".text.failedToLoadMusicResource"));
                            setState(PlaybackState.ERROR);
                        }
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ie) {
                            return;
                        }
                        continue;
                    }
                    resourceResolveErrorNotified = false;
                    resourceInfo.set(resource);
                    publishResourceInfo(resolved.requested(), resolved.source());
                    if (cancelled || restartRequested) return;
                }

                LOGGER.debug("Starting audio download (attempt {})", trial);

                AudioDecoder decoder = loadAudioDecoder(resource.getUrl(), resource.getType());
                if (cancelled || restartRequested) {
                    try {
                        decoder.close();
                    } catch (Exception ignored) {
                    }
                    return;
                }
                currentDecoder = decoder;
                // A cancel()/restart may have run its cleanup between the check
                // above and this assignment, closing the (then-null) decoder
                // field. Drop the orphan so it does not stay open.
                if (cancelled || restartRequested) {
                    if (currentDecoder == decoder) {
                        currentDecoder = null;
                    }
                    try {
                        decoder.close();
                    } catch (Exception ignored) {
                    }
                    return;
                }
                // A freshly opened decoder starts at byte 0; make sure a stale
                // count from a previous decoder (e.g. this loop restarted after
                // the download thread died) can't make syncPlaying() skip the
                // wall-clock re-anchor.
                resetSyncAccounting();
                ledger.decodedBytes.set(0);
                setState(PlaybackState.LOADING);
                if (updateDecoderInfo(decoder)) {
                    publishAudioInfo();
                }

                if (forceSync) {
                    syncPlaying();
                }

                int initialBuffers = 0;
                while (!cancelled && !restartRequested && initialBuffers < BUFFER_COUNT * 2) {
                    byte[] audioData = decoder.readChunk(BUFFER_SIZE);
                    if (audioData == null) break;
                    if (cancelled || restartRequested) break;
                    enqueueAudio(audioData);
                    ledger.decodedBytes.addAndGet(audioData.length);
                    initialBuffers++;
                }
                // MP3 needs the initial frames before stream info exists.
                if (updateDecoderInfo(decoder)) {
                    publishAudioInfo();
                }

                while (!cancelled && !restartRequested) {
                    // Keep the decoder head aligned with the wall clock regardless of download backlog.
                    syncPlaying();

                    byte[] audioData = decoder.readChunk(BUFFER_SIZE);
                    if (audioData == null) break;
                    if (cancelled || restartRequested) break;

                    enqueueAudio(audioData);
                    ledger.decodedBytes.addAndGet(audioData.length);
                }

                if (cancelled || restartRequested) return;

                // A stream that ran out well before its declared duration is a
                // truncated/expired download (e.g. a signed URL that died while a
                // too-early preload held the connection), not a real end. Retry with
                // a freshly requested URL instead of silently fading out early.
                // Bounded, so tracks whose metadata is simply longer than the audio
                // still play what exists after a few attempts.
                long decodedMs = decodedMillis(decoder);
                if (streamLooksTruncated(decoder)) {
                    SpoolBuffer spool = audioSpool;
                    if (spool != null && spool.isComplete()) {
                        LOGGER.warn("Audio object is shorter than its metadata (decoded ~{} ms of {} ms), accepting it",
                                decodedMs, musicDetail.getDurationMillis());
                    } else if (truncationRetryCount < MAX_TRUNCATION_RETRIES) {
                        truncationRetryCount++;
                        localRetryCount++;
                        LOGGER.warn("Audio stream ended early: decoded ~{} ms of {} ms, retrying with a fresh resource (truncation retry {}/{})",
                                decodedMs, musicDetail.getDurationMillis(), truncationRetryCount, MAX_TRUNCATION_RETRIES);
                        try {
                            decoder.close();
                        } catch (Exception ignored) {
                        }
                        clearAudio();
                        ledger.prefetchBytes.set(0);
                        resetSyncAccounting();
                        ledger.decodedBytes.set(0);
                        forceSync = true;
                        forceResourceRefresh = true;
                        setState(PlaybackState.RETRYING);
                        try {
                            Thread.sleep((long) truncationRetryCount * DOWNLOAD_RETRY_DELAY_ADDITIONAL_MS);
                        } catch (InterruptedException ie) {
                            LOGGER.debug("Download thread interrupted");
                            return;
                        }
                        continue;
                    } else {
                        LOGGER.warn("Audio stream still short after {} truncation retries, accepting decoded content (~{} ms of {} ms)",
                                truncationRetryCount, decodedMs, musicDetail.getDurationMillis());
                    }
                }

                downloadDone = true;
                signalBufferReady();
                LOGGER.debug("Audio download completed");
                return;
            } catch (InterruptedException e) {
                LOGGER.debug("Download stopped by interruption");
                return;
            } catch (Exception e) {
                if (restartRequested || cancelled) return;
                String message = e.getMessage();
                if (e instanceof SocketException && "Closed by interrupt".equals(message)) return;
                LOGGER.error("Download error (attempt {})", trial, e);
                if (e.getCause() instanceof TimeoutException || (message != null && (message.contains("Timeout") || message.contains("timeout")))) {
                    message = I18n.get(MusicHud.MOD_ID + ".error.cause.timeout");
                }
                if (message != null) {
                    ToastUtil.show(
                            I18n.get(MusicHud.MOD_ID + ".error.downloadingAudioStream")
                                    .replace("{trial}", String.valueOf(trial))
                                    .replace("{message}", message)
                    );
                }

                clearAudio();
                ledger.prefetchBytes.set(0);
                resetSyncAccounting();
                ledger.decodedBytes.set(0);
                forceSync = true;
                localRetryCount++;
                setState(PlaybackState.RETRYING);

                try {
                    long delay = (long) localRetryCount * DOWNLOAD_RETRY_DELAY_ADDITIONAL_MS;
                    LOGGER.debug("Waiting {} ms before retry", delay);
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    LOGGER.debug("Download thread interrupted");
                    return;
                }
            }
        }
    }

    /**
     * Enqueue a chunk, polling while the prefetch buffer is full. Does not signal
     * {@link #bufferReady}: the byte counters are updated by the caller after this
     * returns, so signalling here would wake {@link #waitInitialBuffer()} on a
     * stale {@code prefetchBytes} value.
     */
    private void enqueueAudio(byte[] data) throws InterruptedException {
        while (audioBuffer.size() >= AUDIO_BUFFER_CAPACITY) {
            if (cancelled || restartRequested) {
                throw new InterruptedException("download stopped");
            }
            //noinspection BusyWait
            Thread.sleep(PLAY_LOOP_SLEEP_MS);
        }
        if (cancelled || restartRequested) {
            throw new InterruptedException("download stopped");
        }
        // Count before publishing: a concurrent poll must not be able to decrement
        // below the real occupancy, which used to let prefetchBytes drift low and
        // wedge waitForBuffer/underrun detection.
        ledger.prefetchBytes.addAndGet(data.length);
        enqueuedBytes.addAndGet(data.length);
        audioBuffer.put(data);
        lastEnqueueMs = System.currentTimeMillis();
    }

    private void signalBufferReady() {
        bufferLock.lock();
        try {
            bufferReady.signalAll();
        } finally {
            bufferLock.unlock();
        }
    }

    /** Poll the next chunk (recovered head first) and decrement the prefetch count. */
    private byte @Nullable [] pollAudio() {
        byte[] data = recoveredHead.pollFirst();
        if (data == null) {
            data = audioBuffer.poll();
        }
        if (data != null) {
            ledger.prefetchBytes.addAndGet(-data.length);
            polledBytes.addAndGet(data.length);
        }
        return data;
    }

    private byte @Nullable [] peekAudio() {
        byte[] data = recoveredHead.peekFirst();
        return data != null ? data : audioBuffer.peekFirst();
    }

    private boolean hasAudio() {
        return !recoveredHead.isEmpty() || !audioBuffer.isEmpty();
    }

    private int bufferedChunks() {
        return recoveredHead.size() + audioBuffer.size();
    }

    private void clearAudio() {
        recoveredHead.clear();
        audioBuffer.clear();
        // Reset the byte count before signalling so waiters don't see a stale value.
        ledger.prefetchBytes.set(0);
        signalBufferReady();
    }

    /**
     * Reset the enqueue/poll and skip-tracking counters. Must be called whenever
     * the prefetch buffer or the decoder is discarded, so the counters stay in the
     * same byte space as a freshly opened stream.
     */
    private void resetSyncAccounting() {
        enqueuedBytes.set(0);
        polledBytes.set(0);
        syncSkipBoundary.set(0);
        syncSkipTarget.set(0);
    }

    /** Decoded stream position in ms for the given decoder, or -1 when unknown. */
    private long decodedMillis(AudioDecoder decoder) {
        if (decoder == null) return -1;
        int sampleRate = decoder.getSampleRate();
        int bytesPerSample = OpenAlSource.bytesPerSample(decoder.getFormat());
        if (sampleRate <= 0 || bytesPerSample <= 0) return -1;
        long bytesPerSecond = (long) sampleRate * bytesPerSample;
        return ledger.decodedBytes.get() * 1000L / bytesPerSecond;
    }

    /**
     * Whether the decoder hit EOF well before the track's declared duration, i.e.
     * the download was cut short (dropped/expired connection) instead of ending
     * at the real end of the audio. A tolerance absorbs metadata rounding and
     * codec padding so they are not mistaken for truncation.
     */
    private boolean streamLooksTruncated(AudioDecoder decoder) {
        if (musicDetail == null || musicDetail.equals(MusicDetail.NONE)) return false;
        long durationMs = musicDetail.getDurationMillis();
        if (durationMs <= 0) return false;
        long decodedMs = decodedMillis(decoder);
        if (decodedMs < 0) return false;
        long tolerance = Math.max(2000L, durationMs / 20);
        return decodedMs < durationMs - tolerance;
    }

    /**
     * Bytes buffered that amount to {@link #REBUFFER_TARGET_MS}, capped at half
     * the prefetch capacity so the target is always reachable even for very high
     * bitrates with large raw chunks.
     */
    private static long rebufferTargetBytes(long bytesPerSecond) {
        long byDuration = bytesPerSecond * REBUFFER_TARGET_MS / 1000;
        long byCapacity = (long) BUFFER_SIZE * (AUDIO_BUFFER_CAPACITY / 2);
        return Math.min(byDuration, byCapacity);
    }

    /** Buffer slots that hold about {@link #DEVICE_QUEUE_TARGET_MS} of the decoder's output. */
    private static int deviceBufferCountFor(@Nullable AudioDecoder decoder) {
        int frameBytes = decoder != null ? OpenAlSource.bytesPerSample(decoder.getFormat()) : 4;
        int sampleRate = decoder != null ? decoder.getSampleRate() : 44100;
        long bytesPerTarget = (long) sampleRate * frameBytes * DEVICE_QUEUE_TARGET_MS / 1000;
        long slots = (bytesPerTarget + BUFFER_SIZE - 1) / BUFFER_SIZE;
        return Math.clamp(slots, MIN_DEVICE_BUFFERS, MAX_DEVICE_BUFFERS);
    }

    /**
     * Prefetch needed before playback starts: the recovery target plus a full device queue,
     * so filling the source does not consume the whole safety margin.
     */
    private long initialPrefetchBytes() {
        AudioDecoder decoder = currentDecoder;
        int frameBytes = decoder != null ? OpenAlSource.bytesPerSample(decoder.getFormat()) : 4;
        int sampleRate = decoder != null ? decoder.getSampleRate() : 44100;
        long deviceQueueBytes = (long) deviceBufferCountFor(decoder) * BUFFER_SIZE;
        return Math.max((long) BUFFER_SIZE * BUFFER_COUNT,
                rebufferTargetBytes((long) sampleRate * frameBytes) + deviceQueueBytes);
    }

    private void syncPlaying() {
        if (serverStartTime == null || currentDecoder == null) return;
        int bytesPerSample = OpenAlSource.bytesPerSample(currentDecoder.getFormat());
        long bytesPerSecond = (long) currentDecoder.getSampleRate() * bytesPerSample;
        if (enqueuedBytes.get() > 0
                && ledger.prefetchBytes.get() < rebufferTargetBytes(bytesPerSecond)) {
            // Starving: fast-forwarding would throw away the only data available and
            // can pin the download worker in this loop, leaving the source dry. Keep
            // what we have and let it buffer instead.
            return;
        }

        boolean skipped = false;
        long maxRequest = (long) BUFFER_SIZE / bytesPerSample * bytesPerSample;
        while (!cancelled && !restartRequested) {
            long millis = Duration.between(serverStartTime, ZonedDateTime.now()).toMillis();
            long skipBytes = millis * bytesPerSecond / 1000;
            long remaining = skipBytes - ledger.decodedBytes.get();
            // Never ask for a partial frame: a byte-slicing decoder would leave
            // its stream mid-frame and corrupt everything decoded afterwards.
            long request = remaining / bytesPerSample * bytesPerSample;
            if (request <= 0) break;
            byte[] chunk = currentDecoder.readChunk(Math.min(request, maxRequest));
            if (chunk == null) break;
            ledger.decodedBytes.addAndGet(chunk.length);
            skipped = true;
        }
        if (skipped) {
            // The decoder head jumped forward. Everything enqueued so far predates
            // the jump and is stale; remember both the enqueue boundary and the
            // exact position the decoder now sits at, so the play worker can flush
            // the stale content and then align fedBytes to the live position
            // instead of leaving an unaccounted gap that the margin-based drop
            // would "correct" by skipping live audio every second.
            syncSkipBoundary.accumulateAndGet(enqueuedBytes.get(), Math::max);
            syncSkipTarget.accumulateAndGet(ledger.decodedBytes.get(), Math::max);
        }
    }

    private void playLoop() {
        Thread.currentThread().setName("MH-MusicPlayer");
        waitForGate();
        if (cancelled) {
            finish();
            return;
        }
        while (!cancelled && !Thread.currentThread().isInterrupted()) {
            try {
                if (playOnce() == PlayResult.FINISHED) {
                    finish();
                    return;
                }
                if (ending) {
                    // A retiring task must not rebuild/resurrect.
                    finish();
                    return;
                }
                if (downloadStalled) {
                    // Watchdog: rebuild the whole pipeline (decoder + source) instead
                    // of staying silent on a dead download worker.
                    downloadStalled = false;
                    rebuildForRetry();
                    if (cancelled) break;
                }
            } catch (Exception e) {
                if (ending) break;
                if (tryDiscreteFallback(e)) {
                    continue;
                }
                if (tryRecoverInvalidSource(e)) {
                    continue;
                }
                SoundEngineState current = SoundEngineState.getCurrent();
                if (current == SoundEngineState.SHUTDOWN) {
                    break;
                }
                if (cancelled) break;
                if (current == SoundEngineState.RUNNING) {//only logs when running normally
                    LOGGER.error("Playback error: {}", e.getMessage(), e);
                }
                rebuildForRetry();
                if (cancelled) break;
            }
        }
        finish();
    }

    private void waitForGate() {
        try {
            gate.await();
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Closes a source that was created after {@link #cancel()} already ran its teardown (the source
     * was still null then), so cancellation cannot leak it.
     *
     * @return true when the caller must abort the current playback round
     */
    private boolean abortSourceIfAborted() {
        if (!cancelled && !Thread.currentThread().isInterrupted()) {
            return false;
        }
        OpenAlSource aborted;
        sourceLock.lock();
        try {
            aborted = source;
            source = null;
        } finally {
            sourceLock.unlock();
        }
        if (aborted != null) {
            try {
                aborted.close();
            } catch (Exception e) {
                LOGGER.warn("Failed to close source of aborted task", e);
            }
        }
        return true;
    }

    /** One playback session from source creation to the main loop. */
    private PlayResult playOnce() {
        // Block until any in-progress SoundEngine reload has rebuilt the context.
        try {
            SoundEngineState.awaitNotLoading();
        } catch (InterruptedException e) {
            return PlayResult.RESTARTED;
        }
        if (cancelled) return PlayResult.RESTARTED;

        boolean recovered = false;
        if (resyncOnResume) {
            // Drop the content that would have played during the reload gap.
            resyncOnResume = false;
            recovered = true;
            dropStaleToWallClock();
        }
        waitInitialBuffer();
        if (cancelled) return PlayResult.RESTARTED;
        if (downloadStalled) return PlayResult.RESTARTED;

        if (ledger.prefetchBytes.get() == 0 && !downloadDone) {
            LOGGER.error("No audio data available");
            setState(PlaybackState.ERROR);
            rebuildForRetry();
            return PlayResult.RESTARTED;
        }

        int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
        int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;

        if (recovered) {
            // waitInitialBuffer() may block; re-trim so the anchor below matches the
            // content actually at the head when playback starts.
            dropStaleToWallClock();
        }

        // The decoder may not exist yet at the top of this method (the downloader
        // does a network round-trip first), so configure the source only now that
        // the real stream format is known.
        int bufferSlots = deviceBufferCountFor(currentDecoder);
        deviceBufferCount = bufferSlots;
        OpenAlSource.DirectChannels directChannels = OpenAlSource.directChannelsFor(format);
        sourceLock.lock();
        try {
            // cancel() sets cancelled before tearing the source down under this
            // same lock, so re-checking here makes "created after cancellation"
            // impossible: either we see cancelled and bail, or our source is
            // visible to cancel()'s teardown.
            if (cancelled || Thread.currentThread().isInterrupted()) {
                return PlayResult.RESTARTED;
            }
            source = OpenAlSource.create(new OpenAlSource.Config(bufferSlots, directChannels), ledger);
            source.setPcmSink(pcmSink);
        } finally {
            sourceLock.unlock();
        }

        boolean firstChunk = true;
        for (int i = 0; i < bufferSlots; i++) {
            byte[] audioData = pollAudio();
            if (audioData == null) break;

            if (firstChunk) {
                firstChunk = false;
                // Anchor the absolute content position to the wall clock; the
                // downloader already skipped the decoder to it via syncPlaying().
                if (serverStartTime != null && currentDecoder != null) {
                    int frameBytes = OpenAlSource.bytesPerSample(currentDecoder.getFormat());
                    long bytesPerSecond = (long) currentDecoder.getSampleRate() * frameBytes;
                    long anchoredBytes = Math.max(0, Duration.between(serverStartTime, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000);
                    ledger.anchor(anchoredBytes - anchoredBytes % frameBytes);
                }
            }

            source.queueChunk(audioData, format, sampleRate);
        }
        if (!firstChunk) {
            // A clean upload resets the rejection streak.
            discreteRejectionCount = 0;
        }

        if (clientConfig.getDisableVanillaMusic()) {
            // SoundManager must be accessed on the client thread.
            Minecraft.getInstance().execute(() ->
                    Minecraft.getInstance().getSoundManager().stop(null, SoundSource.MUSIC));
        }

        // A cancel may have raced the create/queue above; never start a source
        // belonging to an aborted task.
        if (abortSourceIfAborted()) {
            return PlayResult.RESTARTED;
        }
        source.setGain(0);
        lastSetGain = 0;
        source.play();
        if (abortSourceIfAborted()) {
            return PlayResult.RESTARTED;
        }

        // Fade progress and startFuture start together, so buffer waits don't consume it.
        long fadeDuration = transitionFadeInMs >= 0 ? transitionFadeInMs : fadeIn.durationMs();
        if (ending) {
            // A retry may have reset the state to RETRYING/LOADING: restore FADING_OUT
            // and keep beginFadeOut()'s timer, so we end as soon as it elapses instead
            // of fading back in alongside the successor task.
            skipFadeInOnce = false;
            if (state != PlaybackState.FADING_OUT) {
                setState(PlaybackState.FADING_OUT);
            }
        } else if (skipFadeInOnce && state != PlaybackState.FADING_OUT) {
            // Source rebuild: resume at target gain to avoid an audible dip.
            skipFadeInOnce = false;
            fadeStartNanos = -1;
            fadeDurationMs = 0;
            if (state != PlaybackState.PLAYING) {
                setState(PlaybackState.PLAYING);
            }
        } else if (state != PlaybackState.FADING_OUT) {
            fadeDurationMs = fadeDuration;
            fadeStartNanos = System.nanoTime();
            setState(PlaybackState.FADING_IN);
        } else {
            skipFadeInOnce = false;
            LOGGER.debug("Playback started while fade-out already requested, honoring fade-out");
        }
        ZonedDateTime wallStart = Objects.requireNonNullElseGet(serverStartTime, ZonedDateTime::now);
        serverStartTime = wallStart;
        startFuture.complete(wallStart);
        LOGGER.debug("Playback started, wallStart={}, fadeIn={} ms", wallStart, fadeDurationMs);

        LoopExitReason exitReason = mainLoop(wallStart);
        // Only a real end of stream or an explicit fade-out finishes the task; a dead source or an
        // interrupted loop is restarted by playLoop.
        return exitReason == LoopExitReason.ENDED ? PlayResult.FINISHED : PlayResult.RESTARTED;
    }

    /**
     * Starts buffering the next track once the current one nears its end, so the next switch does not
     * pay the initial buffering. Only the teardown state is skipped.
     */
    private void maybePreloadNextTrack(ZonedDateTime wallStart) {
        if (cancelled || state == PlaybackState.FADING_OUT
                || musicDetail == null || musicDetail.equals(MusicDetail.NONE)
                || musicDetail.getDurationMillis() <= 0) {
            return;
        }
        // Only the task that is both the audible current task and the one the app
        // considers currently playing may preload. During a cross-fade the outgoing
        // task is still currentTask (and still nearing its own end) while the
        // successor is already pending; without those guards it would preload the
        // track AFTER the pending one, opening an audio connection a whole track
        // early so it can expire/get dropped before playback reaches it.
        if (!StreamAudioPlayer.getInstance().canPreloadFrom(this)
                || !Objects.equals(musicDetail, nowPlayingInfo.getCurrentlyPlayingMusicDetail())) {
            return;
        }
        long remainingMs = musicDetail.getDurationMillis()
                - Duration.between(wallStart, ZonedDateTime.now()).toMillis();
        if (remainingMs > NEXT_TRACK_PRELOAD_REMAINING_MS) {
            return;
        }
        Traceable<MusicDetail> nextTrace = nowPlayingInfo.getNextToPlayMusic();
        MusicDetail next = nextTrace.value();
        if (next == null || next.equals(MusicDetail.NONE) || next.getId() == preloadTriggeredMusicId) {
            return;
        }
        preloadTriggeredMusicId = next.getId();
        StreamAudioPlayer.getInstance().preload(nextTrace);
    }

    @SuppressWarnings("BusyWait")
    private LoopExitReason mainLoop(ZonedDateTime wallStart) {
        long lastIterationNanos = -1;
        while (!cancelled && !Thread.currentThread().isInterrupted()) {
            try {
                // Block until a reload has rebuilt the context; stale names would
                // error and could alias other mods' reused ids.
                if (SoundEngineState.getCurrent() == SoundEngineState.LOADING) {
                    SoundEngineState.awaitNotLoading();
                    continue;
                }
                if (downloadStalled) {
                    // Watchdog flagged an unresponsive download worker: leave the loop
                    // so playLoop rebuilds the pipeline instead of hanging silently.
                    return LoopExitReason.STALLED;
                }
                if (ending && state != PlaybackState.FADING_OUT) {
                    // beginFadeOut() raced our own FADING_IN->PLAYING transition
                    // (updateGain may have overwritten FADING_OUT): re-assert it so
                    // we ramp down and end instead of continuing to play.
                    fadeStartNanos = System.nanoTime();
                    fadeDurationMs = fadeOut.durationMs();
                    setState(PlaybackState.FADING_OUT);
                }
                long iterationNow = System.nanoTime();
                if (lastIterationNanos > 0) {
                    long stallMs = (iterationNow - lastIterationNanos) / 1_000_000;
                    if (stallMs > PLAYBACK_STALL_LOG_MS) {
                        int bytesPerSample = currentDecoder != null ? OpenAlSource.bytesPerSample(currentDecoder.getFormat()) : 4;
                        long bytesPerSecond = (long) (currentDecoder != null ? currentDecoder.getSampleRate() : 44100) * bytesPerSample;
                        long expected = Duration.between(wallStart, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000;
                        LOGGER.info("Playback thread stalled for {} ms (GC STW / scheduling pause?)" +
                                        " [content={} ms expected={} ms bufferedChunks={} sourceQueued={}/{} playing={} state={}]",
                                stallMs,
                                ledger.fedBytes.get() * 1000L / bytesPerSecond, expected * 1000L / bytesPerSecond,
                                bufferedChunks(),
                                source != null ? source.queuedCount() : -1, deviceBufferCount,
                                source != null && source.isPlaying(),
                                state);
                    }
                }
                lastIterationNanos = iterationNow;

                if (DEBUG_STALL_MS > 0 && !debugStallInjected && state == PlaybackState.PLAYING) {
                    debugStallInjected = true;
                    LOGGER.info("Debug: stalling the play worker for {} ms", DEBUG_STALL_MS);
                    Thread.sleep(DEBUG_STALL_MS);
                }

                long jmtcNow = System.currentTimeMillis();
                if (jmtcNow - lastJmtcTickMs >= 1000) {
                    lastJmtcTickMs = jmtcNow;
                    nowPlayingInfo.onPlaybackTick();
                }

                maybePreloadNextTrack(wallStart);

                updateGain();
                if (source == null) return LoopExitReason.SOURCE_INVALID;

                if (state == PlaybackState.FADING_OUT && fadeProgress() >= 1.0) return LoopExitReason.ENDED;

                checkDecoderChangeAndFlush();

                int bytesPerSample = currentDecoder != null ? OpenAlSource.bytesPerSample(currentDecoder.getFormat()) : 4;
                long bytesPerSecond = (long) (currentDecoder != null ? currentDecoder.getSampleRate() : 44100) * bytesPerSample;
                // Keep the wall-clock position frame-aligned so it stays
                // comparable with the frame-aligned fed position.
                Supplier<Long> expectedBytes = () -> {
                    long bytes = Duration.between(wallStart, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000;
                    return bytes - bytes % bytesPerSample;
                };
                long resyncThresholdBytes = RESYNC_THRESHOLD_MS * bytesPerSecond / 1000;
                int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
                int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;

                boolean sourceEmpty = !source.hasUnplayedBuffers();
                boolean starving = sourceEmpty && !downloadDone
                        && ledger.prefetchBytes.get() < rebufferTargetBytes(bytesPerSecond);
                if (starving && !rebuffering) {
                    rebuffering = true;
                    LOGGER.info("Playback rebuffering: {} ms buffered, waiting for {} ms",
                            ledger.prefetchBytes.get() * 1000L / bytesPerSecond, REBUFFER_TARGET_MS);
                } else if (!starving && rebuffering) {
                    rebuffering = false;
                    if (underrunSinceNanos >= 0) {
                        underrunSinceNanos = -1;
                        logUnderrunDiagnostics("underrun-recovered", expectedBytes.get(), bytesPerSecond);
                    }
                    LOGGER.info("Playback rebuffering complete: {} ms buffered",
                            ledger.prefetchBytes.get() * 1000L / bytesPerSecond);
                }

                long deficitBytes = expectedBytes.get() - playedContentBytes();
                boolean hasContent = ledger.queuedBytes.get() > 0 || ledger.prefetchBytes.get() > 0;
                if (!rebuffering && !ending && hasContent && deficitBytes > resyncThresholdBytes
                        && ledger.decodedBytes.get() > expectedBytes.get()) {
                    resyncToWallClock(deficitBytes, bytesPerSecond);
                    continue;
                }

                if (!rebuffering) {
                    if (sourceEmpty && underrunSinceNanos >= 0) {
                        underrunSinceNanos = -1;
                        logUnderrunDiagnostics("underrun-recovered", expectedBytes.get(), bytesPerSecond);
                    }
                    int filled = source.fill(() -> takeChunk(expectedBytes, bytesPerSecond),
                            deviceBufferCount, format, sampleRate);
                    if (filled > 0) {
                        resumeIfWaiting();
                    }
                }

                updateUnderrunState(expectedBytes.get(), bytesPerSecond);
                source.updatePlaybackPosition();

                if (!source.isPlaying() && source.hasUnplayedBuffers() && !cancelled) {
                    source.play();
                }
                Thread.sleep(PLAY_LOOP_SLEEP_MS);
            } catch (InterruptedException e) {
                return LoopExitReason.INTERRUPTED;
            }
        }
        return LoopExitReason.INTERRUPTED;
    }

    /** Take the next chunk for OpenAL; wall-clock realignment happens in {@link #mainLoop}. */
    private byte @Nullable [] takeChunk(Supplier<Long> expectedBytes, long bytesPerSecond) {
        // Apply a decoder fast-forward first: it is an exact content jump.
        dropToSyncTarget();
        byte[] data = pollAudio();
        if (data != null) {
            if (underrunSinceNanos >= 0) {
                underrunSinceNanos = -1;
                logUnderrunDiagnostics("underrun-recovered", expectedBytes.get(), bytesPerSecond);
            }
            return data;
        }
        if (!downloadDone && underrunSinceNanos < 0) {
            underrunSinceNanos = System.nanoTime();
            logUnderrunDiagnostics("underrun-started", expectedBytes.get(), bytesPerSecond);
        }
        return null;
    }

    /** Audible content position: already played bytes plus what is still buffered in the source. */
    private long playedContentBytes() {
        long queuedStart = Math.max(0, ledger.fedBytes.get() - ledger.queuedBytes.get());
        OpenAlSource current = source;
        if (current == null) return queuedStart;
        long playedInQueue = current.playedQueueBytes();
        return playedInQueue < 0 ? queuedStart : queuedStart + playedInQueue;
    }

    /**
     * Re-aligns playback with the wall clock in one step: the audio that should already have
     * played is dropped (including the unplayed tail of the device queue), then the source is
     * restarted with a short fade-in. Avoids bleeding audible skips for seconds after a hitch.
     */
    private void resyncToWallClock(long deficitBytes, long bytesPerSecond) {
        OpenAlSource current = source;
        if (current == null) return;
        long contentStart = Math.max(0, ledger.fedBytes.get() - ledger.queuedBytes.get());
        LOGGER.info("Playback is {} ms behind the wall clock, resyncing the source",
                deficitBytes * 1000L / bytesPerSecond);
        current.unqueueAll();
        List<PlaybackLedger.LedgerEntry> unplayed = ledger.drainQueue();
        ledger.anchor(contentStart);
        restoreUnplayed(unplayed);
        syncSkipBoundary.set(0);
        syncSkipTarget.set(0);
        dropStaleToWallClock();
        current.setGain(0);
        lastSetGain = 0;
        underrunSinceNanos = -1;
        rebuffering = false;
        if (!ending) {
            fadeStartNanos = System.nanoTime();
            fadeDurationMs = RESYNC_FADE_IN_MS;
            setState(PlaybackState.FADING_IN);
        }
    }

    private void resumeIfWaiting() {
        if (state == PlaybackState.LOADING || state == PlaybackState.BUFFERING) {
            setState(PlaybackState.PLAYING);
        }
    }

    private void updateUnderrunState(long expectedBytes, long bytesPerSecond) {
        boolean sourceDry = source != null && !source.hasUnplayedBuffers();
        // A dry source is starving whenever the prefetch cannot yet feed it, even if
        // a few chunks are still buffered. Requiring an empty prefetch here (the old
        // !hasAudio() test) let a 1..N chunk buffer wedge playback silently in
        // PLAYING with no BUFFERING state and no download restart.
        if (underrunSinceNanos < 0 && sourceDry && !downloadDone
                && ledger.prefetchBytes.get() < rebufferTargetBytes(bytesPerSecond)) {
            underrunSinceNanos = System.nanoTime();
            logUnderrunDiagnostics("underrun-started", expectedBytes, bytesPerSecond);
            // Restart a download thread that exited without EOF; otherwise playback stays BUFFERING forever.
            Future<?> currentDownloadFuture = downloadThreadFuture;
            if (currentDownloadFuture != null && currentDownloadFuture.isDone() && !cancelled
                    && System.currentTimeMillis() - lastDownloadRestartTimestamp > 1000) {
                LOGGER.warn("Download thread died during playback, restarting download");
                downloadThreadFuture = MusicHud.EXECUTOR.submit(this::downloadLoop);
                lastDownloadRestartTimestamp = System.currentTimeMillis();
            }
        }

        if (downloadDone && !hasAudio() && state != PlaybackState.FADING_OUT
                && state != PlaybackState.ERROR && state != PlaybackState.RETRYING) {
            if (source != null && !source.hasUnplayedBuffers()) {
                LOGGER.debug("Audio stream drained, fading out (queued={}, processed={})",
                        source.queuedCount(), source.processedCount());
                beginFadeOut(fadeOut.durationMs());
            }
        }

        if (underrunSinceNanos >= 0 && !downloadDone && state != PlaybackState.ERROR && state != PlaybackState.RETRYING
                && state != PlaybackState.FADING_IN && state != PlaybackState.FADING_OUT) {
            long underrunMs = (System.nanoTime() - underrunSinceNanos) / 1_000_000;
            if (underrunMs >= UNDERFLOW_BUFFERING_DELAY_MS) {
                setState(PlaybackState.BUFFERING);
            }
        }

        // Liveness watchdog: a download worker blocked on a half-open socket, or
        // stuck in the catch-up loop, enqueues nothing and raises no error. Rebuild
        // the pipeline instead of hanging silently in PLAYING. Skip while the worker
        // is already visibly retrying so its backoff is not reset every cycle.
        if (!downloadStalled && !downloadDone && !cancelled && sourceDry
                && state != PlaybackState.RETRYING && state != PlaybackState.ERROR
                && System.currentTimeMillis() - lastEnqueueMs > DOWNLOAD_STALL_RESTART_MS) {
            LOGGER.warn("Download worker unresponsive for {} ms while source is dry, rebuilding pipeline",
                    DOWNLOAD_STALL_RESTART_MS);
            downloadStalled = true;
        }
    }

    private void logUnderrunDiagnostics(String event, long expectedBytes, long bytesPerSecond) {
        if (!LOGGER.isDebugEnabled()) return;
        if ("underrun-started".equals(event)) {
            long now = System.currentTimeMillis();
            if (now - lastUnderrunLogTime < 1000) return;
            lastUnderrunLogTime = now;
        }
        try {
            int queued = source != null ? source.queuedCount() : -1;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("PlaybackDiagnostics {}: fedBytes={} ms, expectedBytes={} ms, deltaMs={}, "
                                + "bufferedChunks={}, sourceQueued={}, state={}",
                        event,
                        ledger.fedBytes.get() * 1000L / bytesPerSecond, expectedBytes * 1000L / bytesPerSecond,
                        (ledger.fedBytes.get() - expectedBytes) * 1000L / bytesPerSecond,
                        bufferedChunks(), queued, state);
            }
        } catch (Exception e) {
            LOGGER.warn("PlaybackDiagnostics failed to log {}: {}", event, e.getMessage());
        }
    }

    private void logStaleDrop(long droppedBytes, long bytesPerSecond) {
        long now = System.currentTimeMillis();
        if (now - lastStaleDropLogTime > 1000) {
            lastStaleDropLogTime = now;
            long expected = serverStartTime == null ? 0
                    : Duration.between(serverStartTime, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000;
            LOGGER.info("Dropped {} ms of stale audio [content now at {} ms, expected {} ms]",
                    droppedBytes * 1000L / bytesPerSecond,
                    ledger.fedBytes.get() * 1000L / bytesPerSecond, expected * 1000L / bytesPerSecond);
        }
    }

    private void updateGain() {
        float targetGain = getTargetGain();
        if (lastVolume != targetGain && source != null) {
            HudPresenter presentationService = HudPresenter.getActiveInstance();
            if (presentationService != null) {
                presentationService.updateStatus(null);
            }
            lastVolume = targetGain;
        }
        if (source == null) return;

        float gain = targetGain;
        PlaybackState s = state;
        if (fadeStartNanos >= 0 && fadeDurationMs > 0) {
            double progress = fadeProgress();
            if (s == PlaybackState.FADING_IN) {
                if (progress >= 1) {
                    setState(PlaybackState.PLAYING);
                } else {
                    gain = (float) (targetGain * fadeIn.easing().apply(progress));
                }
            } else if (s == PlaybackState.FADING_OUT) {
                float fadeOutStartGain = lastSetGain >= 0 ? lastSetGain : targetGain;
                gain = (float) (fadeOutStartGain * (1 - fadeOut.easing().apply(progress)));
            }
        }

        if (gain != lastSetGain) {
            source.setGain(gain);
            lastSetGain = gain;
        }
    }

    public static float getTargetGain() {
        return clientConfig.getMuted() ? 0 : (float) clientConfig.getSoundVolume() / 100 *
                (clientConfig.getMixWithVanillaSoundVolume() ?
                        Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER) * Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MUSIC)
                        : 1
                );
    }

    private void checkDecoderChangeAndFlush() {
        if (currentDecoder == null) return;
        int format = currentDecoder.getFormat();
        int sampleRate = currentDecoder.getSampleRate();
        if (lastDecoderSampleRate == -1) {
            lastDecoderFormat = format;
            lastDecoderSampleRate = sampleRate;
            return;
        }
        if (format != lastDecoderFormat || sampleRate != lastDecoderSampleRate) {
            lastDecoderFormat = format;
            lastDecoderSampleRate = sampleRate;
            LOGGER.info("Decoder format changed to {}/{}Hz, flushing pipeline", format, sampleRate);
            if (source != null) {
                source.flush();
            }
            clearAudio();
            resetSyncAccounting();
            // The flush zeroed the fed position; the downloader fast-forwards the new
            // stream to the wall clock, so re-anchor there instead of implying 0 ms.
            if (serverStartTime != null) {
                int frameBytes = OpenAlSource.bytesPerSample(format);
                long bytesPerSecond = (long) sampleRate * frameBytes;
                long bytes = Math.max(0, Duration.between(serverStartTime, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000);
                ledger.anchor(bytes - bytes % frameBytes);
            }
            if (updateDecoderInfo(currentDecoder)) {
                publishAudioInfo();
            }
        }
    }

    /**
     * Retries a rejected discrete multichannel upload once, then permanently
     * switches the live decoder to the stereo downmix. Returns true when the
     * caller should rebuild the source.
     */
    private boolean tryDiscreteFallback(Exception e) {
        AudioDecoder decoder = currentDecoder;
        if (decoder == null || !decoder.isDiscreteAttempt()) return false;
        if (clientConfig.getMultichannelMode() != MultichannelMode.PREFER_DISCRETE) return false;
        // A reload/device loss is not a format rejection; the fast recovery handles it.
        if (OpenAlSource.isInvalidSourceError(e)) return false;
        // Only a healthy, unchanged context can report a real format rejection.
        if (source == null || !source.isContextCurrent()
                || SoundEngineState.getCurrent() != SoundEngineState.RUNNING) return false;
        if (!OpenAlSource.isBufferDataError(e)) return false;

        if (++discreteRejectionCount < DISCRETE_REJECTION_CONFIRMATIONS) {
            // Transient failure: rebuild with the same format before giving up.
            LOGGER.warn("Discrete multichannel buffer upload failed (attempt {}/{}), retrying with the same format",
                    discreteRejectionCount, DISCRETE_REJECTION_CONFIRMATIONS, e);
            releaseSourceForRebuild();
            return true;
        }

        LOGGER.warn("Discrete multichannel buffer rejected by OpenAL device, falling back to stereo downmix", e);
        boolean infoChanged;
        synchronized (decoder) {
            int previousFrameBytes = OpenAlSource.bytesPerSample(decoder.getFormat());
            decoder.fallbackToDownmix();
            int currentFrameBytes = OpenAlSource.bytesPerSample(decoder.getFormat());
            if (previousFrameBytes > 0 && previousFrameBytes != currentFrameBytes) {
                // decodedBytes measures an output-byte position; convert it so it
                // keeps denoting the same duration in the downmixed format.
                ledger.decodedBytes.updateAndGet(bytes -> bytes / previousFrameBytes * currentFrameBytes);
            }
            clearAudio();
            resetSyncAccounting();
            lastDecoderFormat = -1;
            lastDecoderSampleRate = -1;
            infoChanged = updateDecoderInfo(decoder);
        }
        if (infoChanged) {
            publishAudioInfo();
        }
        releaseSourceForRebuild();
        return true;
    }

    /**
     * Drop the current source so the next {@code playOnce()} round creates a
     * fresh one. Used by the discrete-format fallback/retry paths.
     */
    private void releaseSourceForRebuild() {
        if (source != null) {
            try {
                source.flush();
            } catch (Exception ignored) {
            }
            source.release();
            source = null;
        }
    }

    /**
     * Fast recovery when the OpenAL source name went invalid mid-playback (context reload / device
     * loss): drop the dead source so the next round rebuilds it. Keeps the decoder, buffered chunks
     * and ledger position.
     *
     * @return true if the recovery was applied (caller should retry playback)
     */
    private boolean tryRecoverInvalidSource(Exception e) {
        boolean contextLost = source != null && !source.isContextCurrent();
        if (!OpenAlSource.isInvalidSourceError(e) && !contextLost) return false;
        if (source == null) return false;
        if (SoundEngineState.getCurrent() != SoundEngineState.SHUTDOWN) {
            LOGGER.warn("OpenAL source went invalid mid-playback, rebuilding source immediately", e);
        }
        // The old OpenAL queue died with the context. The ledger queue mirrors
        // it, so recover its PCM and push it back to the head of the prefetch
        // buffer instead of silently skipping it (which would jump the audio
        // forward by the queue depth).
        long contentStart = Math.max(0, ledger.fedBytes.get() - ledger.queuedBytes.get());
        List<PlaybackLedger.LedgerEntry> unplayed = ledger.drainQueue();
        try {
            source.release();
        } catch (Exception ignored) {
        }
        source = null;
        ledger.anchor(contentStart);
        long bufferedBytes = ledger.prefetchBytes.get();
        resetSyncAccounting();
        restoreUnplayed(unplayed);
        // resetSyncAccounting() zeroed the enqueue counter while the prefetch
        // buffer still holds pre-recovery audio; restore it so a later skip
        // boundary stays in the same byte space as polledBytes.
        enqueuedBytes.addAndGet(bufferedBytes);
        underrunSinceNanos = -1;
        lastDecoderFormat = -1;
        lastDecoderSampleRate = -1;
        skipFadeInOnce = true;
        resyncOnResume = true;
        return true;
    }

    /**
     * Re-insert the chunks recovered from a dead source at the head of the
     * prefetch buffer, preserving their playback order and the prefetch byte
     * accounting. Entries whose format no longer matches the live decoder are
     * dropped (a format change implies a flush anyway).
     */
    private void restoreUnplayed(List<PlaybackLedger.LedgerEntry> unplayed) {
        if (unplayed.isEmpty()) return;
        int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
        int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;
        List<byte[]> recovered = new ArrayList<>(unplayed.size());
        for (PlaybackLedger.LedgerEntry entry : unplayed) {
            if (entry.format() != format || entry.sampleRate() != sampleRate) {
                continue;
            }
            ByteBuffer pcm = entry.pcm();
            byte[] data = new byte[pcm.remaining()];
            pcm.duplicate().get(data);
            recovered.add(data);
            ledger.prefetchBytes.addAndGet(data.length);
            enqueuedBytes.addAndGet(data.length);
        }
        // Recovered PCM is older than everything still buffered, so it must lead the head.
        for (int i = recovered.size() - 1; i >= 0; i--) {
            recoveredHead.addFirst(recovered.get(i));
        }
        signalBufferReady();
    }

    /**
     * Reconciles the play-side position after the downloader skipped the decoder forward: drops the
     * now stale buffered chunks and advances {@code fedBytes} to the absolute skip target, or the
     * skipped span would look permanently behind the wall clock.
     */
    private void dropToSyncTarget() {
        long boundary = syncSkipBoundary.get();
        if (boundary <= 0) return;
        int bytesPerSample = currentDecoder != null ? OpenAlSource.bytesPerSample(currentDecoder.getFormat()) : 4;
        long bytesPerSecond = (long) (currentDecoder != null ? currentDecoder.getSampleRate() : 44100) * bytesPerSample;
        // Drop only content enqueued before the skip; chunks enqueued after it sit
        // behind those in the FIFO and are reached only once the boundary is
        // crossed, so they are never touched here.
        while (polledBytes.get() < boundary) {
            byte[] head = peekAudio();
            if (head == null) break;
            pollAudio();
            logStaleDrop(head.length, bytesPerSecond);
        }
        long target = syncSkipTarget.get();
        if (ledger.fedBytes.get() < target) {
            long advanced = target - ledger.fedBytes.get();
            ledger.fedBytes.set(target);
            LOGGER.debug("Content position re-synced to decoder fast-forward: +{} ms (now {} ms)",
                    advanced * 1000L / bytesPerSecond, target * 1000L / bytesPerSecond);
        }
    }

    /**
     * Drops whole chunks from the head of the prefetch buffer until the content position reaches the
     * wall clock, which is what a context reload needs: the recovered queue lags by the reload
     * duration. Never drops past the wall clock.
     */
    private void dropStaleToWallClock() {
        if (serverStartTime == null || currentDecoder == null) return;
        long bytesPerSample = OpenAlSource.bytesPerSample(currentDecoder.getFormat());
        long bytesPerSecond = (long) currentDecoder.getSampleRate() * bytesPerSample;
        // Frame-align the target so the resumed chunk is never channel-scrambled.
        Supplier<Long> expectedBytes = () -> Math.max(0,
                Duration.between(serverStartTime, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000)
                / bytesPerSample * bytesPerSample;
        while (true) {
            long remaining = expectedBytes.get() - ledger.fedBytes.get();
            if (remaining <= 0) break;
            byte[] head = peekAudio();
            if (head == null) break;
            if (head.length <= remaining) {
                pollAudio();
                ledger.fedBytes.addAndGet(head.length);
                logStaleDrop(head.length, bytesPerSecond);
            } else {
                // The next chunk crosses the wall clock: trim it so the resume
                // lands exactly on the target instead of stopping a whole chunk
                // short (which left the audio behind the lyrics/ledger).
                int drop = (int) (remaining / bytesPerSample * bytesPerSample);
                if (drop <= 0) break;
                byte[] trimmed = new byte[head.length - drop];
                System.arraycopy(head, drop, trimmed, 0, trimmed.length);
                pollAudio();
                recoveredHead.addFirst(trimmed);
                ledger.prefetchBytes.addAndGet(trimmed.length);
                // pollAudio() counted the whole chunk, but the trimmed tail stays
                // buffered and will be polled again.
                polledBytes.addAndGet(-trimmed.length);
                ledger.fedBytes.addAndGet(drop);
                logStaleDrop(drop, bytesPerSecond);
                break;
            }
        }
    }

    private void rebuildForRetry() {
        if (ending) return;
        setState(PlaybackState.RETRYING);
        stopDownloadWorker();
        cleanup();
        // cleanup() only resets the ledger through source.close() when a source
        // exists; a retry triggered while source == null (e.g. after a context
        // reload) must still zero decodedBytes so syncPlaying() re-anchors the
        // reopened decoder to the wall clock instead of playing from the start.
        ledger.resetAll();
        resetSyncAccounting();
        downloadStalled = false;
        rebuffering = false;
        try {
            Thread.sleep(FULLY_RETRY_SLEEP_MS);
        } catch (InterruptedException ignored) {
            if (cancelled) return;
        }
        if (cancelled) return;
        restartRequested = false;
        lastEnqueueMs = System.currentTimeMillis();
        LOGGER.info("Fully retrying");
        downloadThreadFuture = MusicHud.EXECUTOR.submit(this::downloadLoop);
    }

    private void waitInitialBuffer() {
        long lastRestartTimestamp = 0;
        long lastProgressMs = System.currentTimeMillis();
        long lastBytes = ledger.prefetchBytes.get();
        // Surface buffering to the UI even when this is a re-entry (e.g. after a
        // source rebuild), so a stall here is never silently shown as PLAYING.
        setState(PlaybackState.BUFFERING);
        bufferLock.lock();
        try {
            while (!cancelled && !downloadDone && ledger.prefetchBytes.get() < initialPrefetchBytes()) {
                long now = System.currentTimeMillis();
                long bytes = ledger.prefetchBytes.get();
                if (bytes != lastBytes) {
                    lastBytes = bytes;
                    lastProgressMs = now;
                }
                Future<?> currentDownloadFuture = downloadThreadFuture;
                if (currentDownloadFuture != null && currentDownloadFuture.isDone() && !downloadDone
                        && now - lastRestartTimestamp > 1000) {
                    LOGGER.warn("Download thread died unexpectedly, restarting download");
                    downloadThreadFuture = MusicHud.EXECUTOR.submit(this::downloadLoop);
                    lastRestartTimestamp = now;
                } else if (now - lastProgressMs > DOWNLOAD_STALL_RESTART_MS) {
                    // The worker is alive but not delivering (blocked socket / stuck
                    // decoder): bail out so playLoop rebuilds instead of waiting forever.
                    LOGGER.warn("Download made no progress for {} ms while buffering, rebuilding pipeline",
                            DOWNLOAD_STALL_RESTART_MS);
                    downloadStalled = true;
                    return;
                }
                try {
                    //noinspection ResultOfMethodCallIgnored
                    bufferReady.await(INITIAL_BUFFER_WAIT_SLEEP_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    return;
                }
            }
        } finally {
            bufferLock.unlock();
        }
    }

    private void finish() {
        stopDownloadWorker();
        // These steps must never prevent cleanup()/finishFuture from running: a
        // throw here used to skip both, leaking a live OpenAL source and wedging
        // the orchestrator's current/pending promotion.
        try {
            if (scrobbleOnQuitUnregister != null) {
                scrobbleOnQuitUnregister.unregister();
            }
            scrobble();
        } catch (Exception e) {
            LOGGER.warn("Pre-cleanup step failed, finishing task anyway", e);
        }
        cleanup();
        releaseSpool();
        // Complete the finish future FIRST: its listener promotes any pending task
        // and updates the global status. Broadcasting FINISHED before that would
        // briefly set the global status to IDLE even though a successor is waiting.
        finishFuture.complete(null);
        setState(PlaybackState.FINISHED);
    }

    private void scrobble() {
        if (!scrobbled) {
            scrobbled = true;
            switch (clientConfig.getScrobbleOption()) {
                case ALL -> sendScrobbleOptionally();
                case ONLY_SELF -> {
                    if (musicDetail.getPusherInfo().playerUUID().equals(PlayerInfoUtil.getSelfUUID())) {
                        sendScrobbleOptionally();
                    }
                }
            }
        }
    }

    private void sendScrobbleOptionally() {
        MusicResourceInfo resource = resourceInfo.get();
        if (musicDetail != null && !MusicDetail.NONE.equals(musicDetail) && resource.isUsable()) {
            Quality quality = resource.getQuality().canonical();
            if (quality != Quality.NONE) {
                int durationSec = musicDetail.getDurationMillis() / 1000;
                int playedSec = serverStartTime == null ? durationSec : Math.toIntExact(Duration.between(serverStartTime, ZonedDateTime.now()).toSeconds());
                if (playedSec >= SCROBBLE_MIN_PLAY_DURATION_SEC) {
                    clientNetworkService.sendToServer(new ScrobbleMessage(musicDetail.getId(), playedSec, durationSec, resource.getBitrate(), quality, musicTrace.source()));
                }
            }
        }
    }

    private void cleanup() {
        // Serialize concurrent teardowns (cancel() vs the play worker's finish())
        // instead of dropping one: the former compare-and-set silently skipped the
        // second caller, which could leave a source created just before the call
        // open and audible next to the successor task.
        cleanupLock.lock();
        try {
            cleanupInternal();
        } finally {
            cleanupLock.unlock();
        }
    }

    private void cleanupInternal() {
        try {
            sourceLock.lock();
            try {
                if (source != null) {
                    source.close();
                    source = null;
                }
            } finally {
                sourceLock.unlock();
            }

            lastVolume = 1;
            lastSetGain = -1;
            downloadDone = false;
            underrunSinceNanos = -1;
            rebuffering = false;
            downloadStalled = false;
            lastEnqueueMs = System.currentTimeMillis();
            resetSyncAccounting();
            audioBuffer = new LinkedBlockingDeque<>(AUDIO_BUFFER_CAPACITY);
            recoveredHead.clear();
            signalBufferReady();
            lastDecoderFormat = -1;
            lastDecoderSampleRate = -1;
            if (currentDecoder != null) {
                try {
                    currentDecoder.close();
                } catch (Exception ignored) {
                }
                currentDecoder = null;
            }
            LOGGER.debug("Cleanup completed");
        } catch (Exception e) {
            LOGGER.error("Unexpected error during cleanup", e);
            AL10.alGetError();
        }
    }

    /** The task's audio spool. */
    private SpoolBuffer audioSpool() {
        SpoolBuffer current = audioSpool;
        if (current != null && current.isUsable()) return current;
        if (current != null) {
            current.close();
            audioSpool = null;
        }
        MusicResourceInfo info = resourceInfo.get();
        if (!info.isUsable()) return null;
        SpoolBuffer opened = SpoolBuffer.open(info.getUrl(), info.getSize(), this::refreshSpoolUrl,
                "track-" + musicDetail.getId() + "-" + info.getQuality().canonical().name());
        audioSpool = opened;
        return opened;
    }

    private String refreshSpoolUrl(String staleUrl) {
        MusicResourceInfo previous = resourceInfo.get();
        Quality quality = previous.isUsable()
                ? previous.getQuality().canonical()
                : clientConfig.getQualityPriority().first();
        String retryFor = previous.getUrl().isEmpty() ? (staleUrl == null ? "" : staleUrl) : previous.getUrl();
        try {
            MusicResourceInfo refreshed = RequestResponseManager.send(
                            new GetMusicResourceRequest(musicDetail.getId(), quality, retryFor),
                            GetMusicResourceResponse.class,
                            Duration.ofSeconds(10))
                    .thenApply(GetMusicResourceResponse::getMusicResourceInfo)
                    .get(15, TimeUnit.SECONDS);
            if (refreshed == null || refreshed.equals(MusicResourceInfo.NONE) || refreshed.getUrl().isEmpty()) {
                return null;
            }
            resourceInfo.set(refreshed);
            publishResourceInfo(quality, ResolvedResource.sourceOf(quality));
            LOGGER.info("Refreshed the audio resource url for the spool");
            return refreshed.getUrl();
        } catch (Exception e) {
            LOGGER.warn("Failed to refresh the audio resource url: {}", e.toString());
            return null;
        }
    }

    private void releaseSpool() {
        SpoolBuffer spool = audioSpool;
        audioSpool = null;
        if (spool != null) {
            spool.close();
        }
    }

    @SneakyThrows
    private AudioDecoder getAudioDecoder(FormatType formatType, BufferedInputStream inputStream) {
        boolean useFloat32 = OpenAlSource.isFloat32Supported();
        FormatType formatType1 = formatType;
        if (formatType1 == FormatType.AUTO) {
            formatType1 = AudioFormatDetector.detectFormat(inputStream);
        }
        return switch (formatType1) {
            case WAV -> new WavStreamDecoder(inputStream, useFloat32, clientConfig.getMultichannelMode());
            case MP3 -> new MP3StreamDecoder(inputStream);
            case FLAC -> new FLACStreamDecoder(inputStream, useFloat32, clientConfig.getMultichannelMode());
            case AUTO -> throw new IllegalArgumentException();
        };
    }

    private AudioDecoder loadAudioDecoder(String urlString, FormatType formatType) throws URISyntaxException, IOException {
        InputStream inputStream;
        BufferedInputStream bufferedStream;
        SpoolBuffer spool = AUDIO_SPOOL_ENABLED ? audioSpool() : null;
        if (spool != null) {
            inputStream = spool.openStream();
            bufferedStream = new BufferedInputStream(inputStream, SPOOL_INPUT_BUFFER_SIZE);
        } else {
            URL url = new URI(urlString).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            inputStream = connection.getInputStream();
            bufferedStream = new BufferedInputStream(inputStream, 8192);
        }

        try {
            if (formatType != FormatType.AUTO) {
                FormatType detectedFormatType = null;
                try {
                    detectedFormatType = AudioFormatDetector.detectFormat(bufferedStream);
                } catch (IOException e) {
                    LOGGER.warn("Error while trying to detect format", e);
                }
                if (detectedFormatType != null && detectedFormatType != formatType) {
                    LOGGER.warn("Detected format type is not equals to resource format type, using detected");
                    return getAudioDecoder(detectedFormatType, bufferedStream);
                } else {
                    return getAudioDecoder(formatType, bufferedStream);
                }
            } else {
                return getAudioDecoder(formatType, bufferedStream);
            }
        } catch (Throwable e) {
            bufferedStream.close();
            inputStream.close();
            throw e;
        }
    }

    private ResolvedResource resolveResource(QualityPriority priority, MusicResourceInfo previous) {
        // URL expiry / CDN retry: keep the actual quality instead of restarting the whole plan.
        if (previous != null && previous.isUsable()) {
            MusicResourceInfo refreshed = requestResource(previous.getQuality().canonical(), previous.getUrl());
            if (refreshed != null && refreshed.isUsable()) {
                return ResolvedResource.of(refreshed, previous.getQuality());
            }
        }

        List<Quality> order = priority.enabledOrder();
        List<Quality> remaining = new ArrayList<>(order);
        int maxRequestedRank = -1;
        long deadline = System.currentTimeMillis() + 30_000L;
        while (!remaining.isEmpty()) {
            if (cancelled || restartRequested || System.currentTimeMillis() > deadline) {
                break;
            }
            Quality requested = remaining.removeFirst().canonical();
            maxRequestedRank = Math.max(maxRequestedRank, requested.rank());
            MusicResourceInfo info = requestResource(requested, "");
            if (info == null || !info.isUsable()) {
                continue;
            }
            Quality actual = info.getQuality().canonical();
            if (actual == Quality.NONE) {
                continue;
            }
            int actualRank = actual.rank();
            int actualIndex = order.indexOf(actual);
            int requestedRank = maxRequestedRank;
            remaining.removeIf(candidate -> {
                Quality canonical = candidate.canonical();
                // The API returned `actual` for a request capped at `requestedRank`;
                // everything between them is unavailable on the API ladder.
                if (canonical.rank() > actualRank && canonical.rank() <= requestedRank) {
                    return true;
                }
                // Once an acceptable actual is known, less preferred entries cannot improve it.
                return actualIndex >= 0 && order.indexOf(canonical) > actualIndex;
            });
            if (actualIndex >= 0) {
                // The result is in hand; remaining now only lists candidates that might beat it.
                remaining.remove(actual);
                if (remaining.isEmpty()) {
                    return ResolvedResource.of(info, requested);
                }
            }
        }

        if (priority.allowUnblocked() && !cancelled && !restartRequested) {
            MusicResourceInfo info = requestResource(Quality.NONE, "");
            if (info != null && info.isUsable()) {
                return ResolvedResource.of(info, Quality.NONE);
            }
        }
        return ResolvedResource.none();
    }

    @Nullable
    private MusicResourceInfo requestResource(Quality quality, String retryFor) {
        try {
            return RequestResponseManager.send(
                            new GetMusicResourceRequest(musicDetail.getId(), quality, retryFor),
                            GetMusicResourceResponse.class,
                            Duration.ofSeconds(10))
                    .thenApply(GetMusicResourceResponse::getMusicResourceInfo)
                    .get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.warn("Failed to request music resource (quality={}, retryFor={}): {}",
                    quality, retryFor, e.toString());
            return null;
        }
    }

    private enum PlayResult {
        FINISHED,
        RESTARTED
    }

    private record ResolvedResource(MusicResourceInfo info, Quality requested, AudioSource source) {
        static ResolvedResource none() {
            return new ResolvedResource(MusicResourceInfo.NONE, Quality.NONE, AudioSource.UNKNOWN);
        }

        static ResolvedResource of(MusicResourceInfo info, Quality requested) {
            Quality canonical = requested == null ? Quality.NONE : requested.canonical();
            return new ResolvedResource(info, canonical, sourceOf(canonical));
        }

        static AudioSource sourceOf(Quality requested) {
            return requested == Quality.NONE ? AudioSource.UNBLOCKED : AudioSource.OFFICIAL;
        }
    }

    /** Why {@link #mainLoop} returned; only {@link #ENDED} is a natural finish. */
    private enum LoopExitReason {
        /** Fade-out ran to completion (end of stream, cross-fade or explicit stop). */
        ENDED,
        /** The OpenAL source vanished mid-loop; the pipeline must be rebuilt. */
        SOURCE_INVALID,
        /** The download watchdog flagged the worker as unresponsive. */
        STALLED,
        /** The play thread was interrupted (cancellation / shutdown). */
        INTERRUPTED
    }
}
