package indi.etern.musichud.client.audio;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.option.MultichannelMode;
import indi.etern.musichud.client.audio.decoder.*;
import indi.etern.musichud.client.interfaces.IClientEventService;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.hud.renderer.PlayingStatusRenderer;
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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
    private static final int AUDIO_BUFFER_CAPACITY = 60;
    /**
     * Consecutive {@code alBufferData} failures required before permanently
     * switching a discrete stream to stereo downmix (one failure can be transient).
     */
    private static final int DISCRETE_REJECTION_CONFIRMATIONS = 2;
    private static final long STALE_DROP_MARGIN_MS = 500;
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
    private final AtomicBoolean cleanupGuard = new AtomicBoolean();
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
    private volatile boolean restartRequested = false;
    private volatile boolean downloadDone = false;
    private volatile OpenAlSource source;
    private volatile long fadeStartNanos = -1;
    private volatile long fadeDurationMs = 0;
    private volatile float lastSetGain = -1;
    private float lastVolume = 1;
    private volatile AudioDecoder currentDecoder;
    private int lastDecoderFormat = -1;
    private int lastDecoderSampleRate = -1;
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
    private MusicResourceInfo musicResourceInfo;
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

    public boolean isAudible() {
        PlaybackState s = state;
        return s == PlaybackState.FADING_IN || s == PlaybackState.PLAYING || s == PlaybackState.FADING_OUT;
    }

    public void addStateListener(Consumer<PlaybackState> listener) {
        stateListeners.add(listener);
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
        musicResourceInfo = MusicResourceInfo.NONE;
        while (!cancelled && !restartRequested) {
            int trial = localRetryCount + 1;
            try {
                if (forceResourceRefresh || musicResourceInfo == null || musicResourceInfo.equals(MusicResourceInfo.NONE) || localRetryCount % 3 == 0) {
                    forceResourceRefresh = false;
                    musicResourceInfo = getCurrentMusicResourceInfo(clientConfig.getPrimaryChosenQuality(), musicResourceInfo).get();
                    if (musicResourceInfo == null) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ie) {
                            return;
                        }
                        continue;
                    }
                    if (cancelled || restartRequested) return;
                }

                LOGGER.debug("Starting audio download (attempt {})", trial);

                AudioDecoder decoder = loadAudioDecoder(musicResourceInfo.getUrl(), musicResourceInfo.getType());
                if (cancelled || restartRequested) {
                    try {
                        decoder.close();
                    } catch (Exception ignored) {
                    }
                    return;
                }
                currentDecoder = decoder;
                // A freshly opened decoder starts at byte 0; make sure a stale
                // count from a previous decoder (e.g. this loop restarted after
                // the download thread died) can't make syncPlaying() skip the
                // wall-clock re-anchor.
                resetSyncAccounting();
                ledger.decodedBytes.set(0);
                setState(PlaybackState.LOADING);

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

                while (!cancelled && !restartRequested) {
                    // 保持解码器头与墙钟对齐（无论下载缓冲是否充足）
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
                    if (truncationRetryCount < MAX_TRUNCATION_RETRIES) {
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
                    }
                    LOGGER.warn("Audio stream still short after {} truncation retries, accepting decoded content (~{} ms of {} ms)",
                            truncationRetryCount, decodedMs, musicDetail.getDurationMillis());
                }

                // 下载完成
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
                LOGGER.error("Download error (attempt {})\n{} : {}", trial, e.getClass().getName(), message);
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
        while (!cancelled && !restartRequested) {
            long millis = Duration.between(serverStartTime, ZonedDateTime.now()).toMillis();
            long skipBytes = millis * bytesPerSecond / 1000;
            if (ledger.decodedBytes.get() > skipBytes - bytesPerSample) {
                break;
            }
            byte[] chunk = currentDecoder.readChunk(Math.max(0, skipBytes - ledger.decodedBytes.get()));
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
                if (downloadStalled) {
                    // Watchdog: rebuild the whole pipeline (decoder + source) instead
                    // of staying silent on a dead download worker.
                    downloadStalled = false;
                    rebuildForRetry();
                    if (cancelled) break;
                }
            } catch (Exception e) {
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
        OpenAlSource.DirectChannels directChannels = OpenAlSource.directChannelsFor(format);
        source = OpenAlSource.create(new OpenAlSource.Config(BUFFER_COUNT, directChannels), ledger);

        boolean firstChunk = true;
        for (int i = 0; i < BUFFER_COUNT; i++) {
            byte[] audioData = pollAudio();
            if (audioData == null) break;

            if (firstChunk) {
                firstChunk = false;
                // Anchor the absolute content position to the wall clock; the
                // downloader already skipped the decoder to it via syncPlaying().
                if (serverStartTime != null && currentDecoder != null) {
                    long bytesPerSecond = (long) currentDecoder.getSampleRate()
                            * OpenAlSource.bytesPerSample(currentDecoder.getFormat());
                    long anchoredBytes = Math.max(0, Duration.between(serverStartTime, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000);
                    ledger.anchor(anchoredBytes);
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

        source.setGain(0);
        lastSetGain = 0;
        source.play();

        // Fade progress and startFuture start together, so buffer waits don't consume it.
        long fadeDuration = transitionFadeInMs >= 0 ? transitionFadeInMs : fadeIn.durationMs();
        if (skipFadeInOnce && state != PlaybackState.FADING_OUT) {
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
        // Only a completed fade-out (real end of stream or an explicit fade-out) may
        // finish the task. A dead source or an interrupted loop must be recovered by
        // playLoop instead of being reported as a natural end.
        return exitReason == LoopExitReason.ENDED ? PlayResult.FINISHED : PlayResult.RESTARTED;
    }

    /**
     * Starts buffering the next queued/idle track once the current one is close
     * enough to its end. The preloaded task is reused on the next switch, so the
     * transition no longer pays the initial buffering cost.
     *
     * <p>Only the teardown state is skipped: retrying or buffering current tracks
     * still preload their successor. The plain duration-based switch remains the
     * fallback when no preload was requested.</p>
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
                long iterationNow = System.nanoTime();
                if (lastIterationNanos > 0) {
                    long stallMs = (iterationNow - lastIterationNanos) / 1_000_000;
                    if (stallMs > PLAYBACK_STALL_LOG_MS) {
                        int bytesPerSample = currentDecoder != null ? OpenAlSource.bytesPerSample(currentDecoder.getFormat()) : 4;
                        long bytesPerSecond = (long) (currentDecoder != null ? currentDecoder.getSampleRate() : 44100) * bytesPerSample;
                        long expected = Duration.between(wallStart, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000;
                        LOGGER.info("Playback thread stalled for {} ms (GC STW / scheduling pause?)" +
                                        " [content={} ms expected={} ms bufferedChunks={} sourceQueued={} playing={} state={}]",
                                stallMs,
                                ledger.fedBytes.get() * 1000L / bytesPerSecond, expected * 1000L / bytesPerSecond,
                                bufferedChunks(),
                                source != null ? source.queuedCount() : -1,
                                source != null && source.isPlaying(),
                                state);
                    }
                }
                lastIterationNanos = iterationNow;

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
                Supplier<Long> expectedBytes = () -> Duration.between(wallStart, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000;
                long staleDropMarginBytes = STALE_DROP_MARGIN_MS * bytesPerSecond / 1000;
                int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
                int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;

                boolean sourceEmpty = source.isEmpty();
                boolean starving = sourceEmpty && !downloadDone
                        && ledger.prefetchBytes.get() < rebufferTargetBytes(bytesPerSecond);
                if (starving && !rebuffering) {
                    rebuffering = true;
                    LOGGER.info("Playback rebuffering: {} ms buffered, waiting for {} ms",
                            ledger.prefetchBytes.get() * 1000L / bytesPerSecond, REBUFFER_TARGET_MS);
                } else if (!starving && rebuffering) {
                    rebuffering = false;
                    Long bytes = expectedBytes.get();
                    if (underrunSinceNanos >= 0) {
                        underrunSinceNanos = -1;
                        logUnderrunDiagnostics("underrun-recovered", bytes, bytesPerSecond);
                    }
                    if (ledger.fedBytes.get() < bytes) {
                        ledger.fedBytes.set(bytes);
                    }
                    LOGGER.info("Playback rebuffering complete: {} ms buffered",
                            ledger.prefetchBytes.get() * 1000L / bytesPerSecond);
                }
                if (!rebuffering) {
                    if (sourceEmpty) {
                        Long bytes = expectedBytes.get();
                        if (underrunSinceNanos >= 0) {
                            underrunSinceNanos = -1;
                            logUnderrunDiagnostics("underrun-recovered", bytes, bytesPerSecond);
                        }
                        if (ledger.fedBytes.get() < bytes) {
                            ledger.fedBytes.set(bytes);
                        }
                    }
                    int filled = source.fill(() -> takeChunk(expectedBytes, staleDropMarginBytes, bytesPerSecond),
                            BUFFER_COUNT, format, sampleRate);
                    if (filled > 0) {
                        resumeIfWaiting();
                    }
                }

                updateUnderrunState(expectedBytes.get(), bytesPerSecond);
                source.updatePlaybackPosition();

                if (!source.isPlaying() && source.queuedCount() > 0 && !cancelled) {
                    source.play();
                }
                Thread.sleep(PLAY_LOOP_SLEEP_MS);
            } catch (InterruptedException e) {
                return LoopExitReason.INTERRUPTED;
            }
        }
        return LoopExitReason.INTERRUPTED;
    }

    /** Take a chunk for OpenAL, applying stale-drop and underrun policy. */
    private byte @Nullable [] takeChunk(Supplier<Long> expectedBytes, long staleDropMarginBytes, long bytesPerSecond) {
        // Account for any decoder fast-forward first: it is an exact jump, not a
        // lag to be approximated by the margin heuristic below.
        dropToSyncTarget();
        if (underrunSinceNanos >= 0) {
            underrunSinceNanos = -1;
            Long bytes = expectedBytes.get();
            if (ledger.fedBytes.get() < bytes) {
                ledger.fedBytes.set(bytes);
            }
            logUnderrunDiagnostics("underrun-recovered", bytes, bytesPerSecond);
            byte[] data = pollAudio();
            if (data == null) {
                if (!downloadDone) {
                    underrunSinceNanos = System.nanoTime();
                    logUnderrunDiagnostics("underrun-started", bytes, bytesPerSecond);
                }
                return null;
            }
            return data;
        }
        byte[] data = pollAudio();
        // Below the recovery target we are (or are about to be) starving: dropping
        // here would skip live audio and accelerate the underrun. Play what we have
        // instead; the pre-buffer handles the rest.
        boolean healthy = ledger.prefetchBytes.get() >= rebufferTargetBytes(bytesPerSecond);
        if (healthy) {
            while (data != null && ledger.fedBytes.get() < expectedBytes.get() - staleDropMarginBytes) {
                // Drop stale chunks and advance fedBytes (overwriting would collapse the
                // position and mark the whole queue stale).
                ledger.fedBytes.addAndGet(data.length);
                logStaleDrop(data.length, bytesPerSecond);
                data = pollAudio();
            }
        }
        if (data == null && !downloadDone && underrunSinceNanos < 0) {
            underrunSinceNanos = System.nanoTime();
            logUnderrunDiagnostics("underrun-started", expectedBytes.get(), bytesPerSecond);
        }
        return data;
    }

    private void resumeIfWaiting() {
        if (state == PlaybackState.LOADING || state == PlaybackState.BUFFERING) {
            setState(PlaybackState.PLAYING);
        }
    }

    private void updateUnderrunState(long expectedBytes, long bytesPerSecond) {
        boolean sourceDry = source != null && source.queuedCount() == 0;
        // A dry source is starving whenever the prefetch cannot yet feed it, even if
        // a few chunks are still buffered. Requiring an empty prefetch here (the old
        // !hasAudio() test) let a 1..N chunk buffer wedge playback silently in
        // PLAYING with no BUFFERING state and no download restart.
        if (underrunSinceNanos < 0 && sourceDry && !downloadDone
                && ledger.prefetchBytes.get() < rebufferTargetBytes(bytesPerSecond)) {
            underrunSinceNanos = System.nanoTime();
            logUnderrunDiagnostics("underrun-started", expectedBytes, bytesPerSecond);
            // 下载线程异常退出（非 EOF）时温和重启下载，避免永久卡 BUFFERING
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
            if (source != null && source.queuedCount() == 0) {
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
        float targetGain = clientConfig.getMuted() ? 0 : (float) clientConfig.getSoundVolume() / 100 *
                (clientConfig.getMixWithVanillaSoundVolume() ? Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MUSIC) : 1);
        if (lastVolume != targetGain && source != null) {
            PlayingStatusRenderer.getInstance().updateStatus(null);
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
            ledger.prefetchBytes.set(0);
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
        synchronized (decoder) {
            decoder.fallbackToDownmix();
            clearAudio();
            resetSyncAccounting();
            lastDecoderFormat = -1;
            lastDecoderSampleRate = -1;
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
     * Fast recovery when the OpenAL source name went invalid mid-playback
     * (context reload / device loss): drop the dead source and let the next
     * {@code playOnce()} round rebuild it. Unlike the full retry this keeps the
     * decoder, the buffered chunks and the ledger position, so playback resumes
     * almost seamlessly instead of restarting after a 1s sleep.
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
        resetSyncAccounting();
        restoreUnplayed(unplayed);
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
        for (PlaybackLedger.LedgerEntry entry : unplayed) {
            if (entry.format() != format || entry.sampleRate() != sampleRate) {
                continue;
            }
            ByteBuffer pcm = entry.pcm();
            byte[] data = new byte[pcm.remaining()];
            pcm.duplicate().get(data);
            recoveredHead.addLast(data);
            ledger.prefetchBytes.addAndGet(data.length);
            enqueuedBytes.addAndGet(data.length);
        }
        signalBufferReady();
    }

    /**
     * Reconcile the play-side content position after the downloader skipped the
     * decoder forward in {@link #syncPlaying()}. Buffered chunks enqueued before
     * the skip are stale and get dropped; then {@code fedBytes} is advanced to
     * the absolute skip target, so the skipped span is accounted exactly once.
     * Without this the skipped bytes are invisible to {@code fedBytes}, which
     * then looks permanently behind the wall clock and makes {@link #takeChunk}
     * drop live audio every second.
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
     * Drop whole chunks from the head of the prefetch buffer until the content
     * position reaches the wall clock. Used after a context reload: the
     * recovered queue lags the wall clock by the reload duration, and dropping
     * it makes playback resume at the synchronized position. Chunks are never
     * dropped past the wall clock, so the resume point is at most one chunk
     * behind (never ahead).
     */
    private void dropStaleToWallClock() {
        if (serverStartTime == null || currentDecoder == null) return;
        long bytesPerSample = OpenAlSource.bytesPerSample(currentDecoder.getFormat());
        long bytesPerSecond = (long) currentDecoder.getSampleRate() * bytesPerSample;
        // Frame-align the target so the resumed chunk is never channel-scrambled.
        Supplier<Long> expectedBytes = () -> Math.max(0,
                Duration.between(serverStartTime, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000)
                / bytesPerSample * bytesPerSample;
//        long startBytes = ledger.fedBytes.get();
//        long droppedBytes = 0;
//        int droppedChunks = 0;
        while (true) {
            long remaining = expectedBytes.get() - ledger.fedBytes.get();
            if (remaining <= 0) break;
            byte[] head = peekAudio();
            if (head == null) break;
            if (head.length <= remaining) {
                pollAudio();
                ledger.fedBytes.addAndGet(head.length);
//                droppedBytes += head.length;
//                droppedChunks++;
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
                ledger.fedBytes.addAndGet(drop);
//                droppedBytes += drop;
//                droppedChunks++;
                logStaleDrop(drop, bytesPerSecond);
                break;
            }
        }
    }

    private void rebuildForRetry() {
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
            while (!cancelled && !downloadDone && ledger.prefetchBytes.get() < (long) BUFFER_SIZE * BUFFER_COUNT) {
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
        scrobbleOnQuitUnregister.unregister();
        scrobble();
        cleanup();
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
                    if (musicDetail.getPusherInfo().getPlayerUUID().equals(PlayerInfoUtil.getSelfUUID())) {
                        sendScrobbleOptionally();
                    }
                }
            }
        }
    }

    private void sendScrobbleOptionally() {
        if (musicDetail != null && !MusicDetail.NONE.equals(musicDetail) && musicResourceInfo != null && !musicResourceInfo.equals(MusicResourceInfo.NONE)) {
            Quality quality = musicResourceInfo.getQuality();
            if (quality != Quality.NONE) {
                int durationSec = musicDetail.getDurationMillis() / 1000;
                int playedSec = serverStartTime == null ? durationSec : Math.toIntExact(Duration.between(serverStartTime, ZonedDateTime.now()).toSeconds());
                if (playedSec >= SCROBBLE_MIN_PLAY_DURATION_SEC) {
                    clientNetworkService.sendToServer(new ScrobbleMessage(musicDetail.getId(), playedSec, durationSec, musicResourceInfo.getBitrate(), quality, musicTrace.source()));
                }
            }
        }
    }

    private void cleanup() {
        if (!cleanupGuard.compareAndSet(false, true)) {
            return;
        }
        try {
            cleanupInternal();
        } finally {
            cleanupGuard.set(false);
        }
    }

    private void cleanupInternal() {
        try {
            if (source != null) {
                source.close();
                source = null;
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
        URL url = new URI(urlString).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        InputStream inputStream = connection.getInputStream();
        BufferedInputStream bufferedStream = new BufferedInputStream(inputStream, 8192);

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
    }

    private CompletableFuture<MusicResourceInfo> getCurrentMusicResourceInfo(Quality quality, MusicResourceInfo previous) {
        String url = previous == null || previous.getUrl() == null ? "" : previous.getUrl();
        return RequestResponseManager.send(
                        new GetMusicResourceRequest(musicDetail.getId(), quality, url),
                        GetMusicResourceResponse.class,
                        Duration.ofSeconds(10))
                .thenCompose(response -> {
                    MusicResourceInfo resourceInfo = response.getMusicResourceInfo();
                    if (resourceInfo == MusicResourceInfo.NONE) {
                        ToastUtil.show(I18n.get(MusicHud.MOD_ID + ".text.failedToLoadMusicResource"));
                        setState(PlaybackState.ERROR);
                        return CompletableFuture.failedFuture(new RuntimeException("Failed to load music resource"));
                    }
                    return CompletableFuture.completedFuture(resourceInfo);
                }).exceptionallyCompose(e -> {
                    ToastUtil.show(I18n.get(MusicHud.MOD_ID + ".text.failedToLoadMusicResource"));
                    setState(PlaybackState.ERROR);
                    return CompletableFuture.failedFuture(new RuntimeException("Failed to load music resource"));
                });
    }

    private enum PlayResult {
        FINISHED,
        RESTARTED
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
