package indi.etern.musichud.client.audio;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.FormatType;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.MusicResourceInfo;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.client.audio.decoder.*;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.hud.renderer.PlayingStatusRenderer;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.requestResponseCycle.GetMusicResourceRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.GetMusicResourceResponse;
import lombok.Getter;
import lombok.SneakyThrows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundSource;
import org.apache.logging.log4j.Logger;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.SOFTDirectChannels;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Playback task for a single song: owns the decoder, the OpenAL source/buffers,
 * the audio buffer queue, the download & play worker threads and all byte-level
 * wall-clock alignment state.
 * <p>
 * Lifecycle: {@code PENDING} (waiting for the start gate) → downloader fills the
 * buffer queue → gate opens → source initialized → {@code FADING_IN} (gain ramps
 * up, {@link #startFuture()} completes) → {@code PLAYING} → natural end or
 * {@link #beginFadeOut(long)} → {@code FADING_OUT} → {@code FINISHED}.
 * <p>
 * A task can be cancelled or fully restarted (reopen stream, rebuild source) at
 * any point; both paths release resources and complete {@link #finishFuture()}.
 */
public class PlaybackTask {
    private static final int BUFFER_COUNT = 8;
    private static final int BUFFER_SIZE = 65536;
    private static final int AUDIO_BUFFER_CAPACITY = 60;
    private static final long STALE_DROP_MARGIN_MS = 500;// 内容落后墙钟超过该值才丢弃
    private static final long PLAYBACK_STALL_LOG_MS = 500;// 播放线程迭代间隔超过该值记录停滞
    private static final long PLAY_LOOP_SLEEP_MS = 40;// 主循环轮询间隔
    private static final long INITIAL_BUFFER_WAIT_SLEEP_MS = 50;// 初始缓冲等待轮询间隔
    private static final long FULLY_RETRY_SLEEP_MS = 1000;// 全量重试前的等待
    private static final long DOWNLOAD_RETRY_DELAY_ADDITIONAL_MS = 1000;
    private static final long UNDERFLOW_BUFFERING_DELAY_MS = 300;// 预取队列连续欠载超过该值才上报 BUFFERING
    private static final Logger LOGGER = MusicHud.getLogger(PlaybackTask.class);
    private static final ClientConfig clientConfig = ClientConfig.getInstance();

    @Getter
    private final MusicDetail musicDetail;
    /**
     * 同步基准时刻：初始为服务器广播的开始时间（可为 null），首次起播时惰性重赋为
     * wallStart（服务器时间 ?? 本地起播时刻），此后保持不变（复刻旧版 this.serverStartTime = wallStart
     * 的重赋语义，保证重试/完全重试后仍以首次起播时刻为基准，从当前墙钟位置继续而不是从头）。
     */
    private volatile ZonedDateTime serverStartTime;
    /**
     * 调度器在交叉淡化时指定的淡入时长（transitionMs = max(outgoing.fadeOut, incoming.fadeIn)）。
     * -1 表示未指定，起播时使用任务自己的 fadeIn。
     */
    private volatile long transitionFadeInMs = -1;
    private final Fade fadeIn;
    private final Fade fadeOut;
    private final CompletableFuture<ZonedDateTime> startFuture = new CompletableFuture<>();
    private final CompletableFuture<Void> finishFuture = new CompletableFuture<>();
    private final CompletableFuture<Void> gate = new CompletableFuture<>();
    private final Set<Consumer<PlaybackState>> stateListeners = new HashSet<>();
    private final AtomicLong totalBufferedBytes = new AtomicLong(0);
    private final int[] buffers = new int[BUFFER_COUNT];
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private volatile BlockingQueue<byte[]> audioBuffer = new LinkedBlockingQueue<>(AUDIO_BUFFER_CAPACITY);
    private volatile PlaybackState state = PlaybackState.PENDING;
    private volatile boolean cancelled = false;
    private volatile boolean restartRequested = false;
    private volatile boolean downloadDone = false;
    private volatile int source = 0;
    private volatile long fadeStartNanos = -1;
    private volatile long fadeDurationMs = 0;
    private volatile float lastSetGain = -1;
    private float lastVolume = 1;
    private AudioDecoder currentDecoder;
    private long playedBytes = 0;
    private long fedBytes = 0;
    private int lastDecoderFormat = -1;
    private int lastDecoderSampleRate = -1;
    private long lastStaleDropLogTime = 0;
    private long lastUnderrunLogTime = 0;
    private long lastDownloadRestartTimestamp = 0;
    private long underrunSinceNanos = -1;
    private int roundRobinBufferIndex = 0;
    private volatile Future<?> downloadThreadFuture;
    private volatile Future<?> playThreadFuture;

    private PlaybackTask(MusicDetail musicDetail, ZonedDateTime serverStartTime, Fade fadeIn, Fade fadeOut) {
        this.musicDetail = musicDetail;
        this.serverStartTime = serverStartTime;
        this.fadeIn = fadeIn;
        this.fadeOut = fadeOut;
    }

    /**
     * Create a task using the default fade durations.
     */
    public static PlaybackTask of(MusicDetail musicDetail, ZonedDateTime serverStartTime) {
        return new PlaybackTask(musicDetail, serverStartTime,
                Fade.of(StreamAudioPlayer.DEFAULT_FADE_IN_MS), Fade.of(StreamAudioPlayer.DEFAULT_FADE_OUT_MS));
    }

    /**
     * Create a task with explicit fade in/out settings.
     */
    public static PlaybackTask of(MusicDetail musicDetail, ZonedDateTime serverStartTime, Fade fadeIn, Fade fadeOut) {
        return new PlaybackTask(musicDetail, serverStartTime, fadeIn, fadeOut);
    }

    public Fade fadeIn() {
        return fadeIn;
    }

    public Fade fadeOut() {
        return fadeOut;
    }

    /**
     * Completes with the effective wall-clock start time the moment this task
     * becomes audible (fade-in begins). Completes exceptionally if the task is
     * cancelled before it started playing.
     */
    public CompletableFuture<ZonedDateTime> startFuture() {
        return startFuture;
    }

    /**
     * Completes when playback fully ends (fade-out finished, resources released).
     */
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
        downloadThreadFuture = MusicHud.EXECUTOR.submit(this::downloadLoop);
        playThreadFuture = MusicHud.EXECUTOR.submit(this::playLoop);
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
        gate.complete(null);
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

    /**
     * Cancel the task immediately: interrupt workers, release resources,
     * complete start future exceptionally and finish future normally.
     */
    public void cancel() {
        if (cancelled) return;
        cancelled = true;
        restartRequested = true;
        if (downloadThreadFuture != null) downloadThreadFuture.cancel(true);
        if (playThreadFuture != null) playThreadFuture.cancel(true);
        gate.complete(null);
        startFuture.completeExceptionally(new CancellationException("Playback task cancelled"));
        cleanup();
        finishFuture.complete(null);
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
        boolean forceSync = serverStartTime != null;
        MusicResourceInfo musicResourceInfo = MusicResourceInfo.NONE;
        while (!cancelled && !restartRequested) {
            int trial = localRetryCount + 1;
            try {
                if (musicResourceInfo == null || musicResourceInfo.equals(MusicResourceInfo.NONE) || localRetryCount % 3 == 0) {
                    musicResourceInfo = getCurrentMusicResourceInfo(clientConfig.getPrimaryChosenQuality(), musicResourceInfo).get();
                    if (musicResourceInfo == null) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ie) {
                            return;
                        }
                        continue;
                    }
                }

                LOGGER.debug("Starting audio download (attempt {})", trial);

                AudioDecoder decoder = loadAudioDecoder(musicResourceInfo.getUrl(), musicResourceInfo.getType());
                currentDecoder = decoder;
                setState(PlaybackState.LOADING);

                if (forceSync) {
                    syncPlaying();
                }

                int initialBuffers = 0;
                while (!cancelled && !restartRequested && initialBuffers < BUFFER_COUNT * 2) {
                    byte[] audioData = decoder.readChunk(BUFFER_SIZE);
                    if (audioData == null) break;
                    if (cancelled || restartRequested) break;
                    audioBuffer.put(audioData);
                    totalBufferedBytes.addAndGet(audioData.length);
                    initialBuffers++;
                }

                while (!cancelled && !restartRequested) {
                    // 保持解码器头与墙钟对齐（无论下载缓冲是否充足）
                    syncPlaying();

                    byte[] audioData = decoder.readChunk(BUFFER_SIZE);
                    if (audioData == null) break;
                    if (cancelled || restartRequested) break;

                    audioBuffer.put(audioData);
                    playedBytes += audioData.length;
                    totalBufferedBytes.addAndGet(audioData.length);
                }

                if (cancelled || restartRequested) return;

                // 下载完成
                downloadDone = true;
                LOGGER.debug("Audio download completed");
                return;
            } catch (InterruptedException e) {
                LOGGER.debug("Download stopped by interruption");
                return;
            } catch (ArrayIndexOutOfBoundsException e) {
                LOGGER.debug("Download stopped by index out of bounds");
                return;
            } catch (Exception e) {
                if (restartRequested || cancelled) return;
                String message = e.getMessage();
                if (e instanceof SocketException e1 && "Closed by interrupt".equals(message)) return;
                LOGGER.error("Download error (attempt {})\n{} : {}", trial, e.getClass().getName(), message);
                if (e.getCause() instanceof TimeoutException || (message != null && (message.contains("Timeout") || message.contains("timeout")))) {
                    message = I18n.get(MusicHud.MOD_ID + ".error.cause.timeout");
                }
                ToastUtil.show(
                        I18n.get(MusicHud.MOD_ID + ".error.downloadingAudioStream")
                                .replace("{trial}", String.valueOf(trial))
                                .replace("{message}", message)
                );

                audioBuffer.clear();
                totalBufferedBytes.set(0);
                playedBytes = 0;
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

    private void syncPlaying() {
        if (serverStartTime == null || currentDecoder == null) return;
        int bytesPerSample = getBytesPerSample(currentDecoder.getFormat());
        long bytesPerSecond = (long) currentDecoder.getSampleRate() * bytesPerSample;

        // 解码器头直接对齐墙钟绝对位置（不再减半缓冲补偿）：
        // 起播时播放线程的 fedBytes 也锚定在同一墙钟位置，内容位置与墙钟完全对齐，
        // 出声即同步；旧版的固定半缓冲补偿与实际起播延迟不匹配，反而导致内容
        // 永久落后墙钟（stale drop 校准到 margin 边界后停止，无法自动恢复）。
        while (!cancelled && !restartRequested) {
            long millis = Duration.between(serverStartTime, ZonedDateTime.now()).toMillis();
            long skipBytes = millis * bytesPerSecond / 1000;
            if (playedBytes > skipBytes - bytesPerSample) {
                break;
            }
            byte[] chunk = currentDecoder.readChunk(Math.max(0, skipBytes - playedBytes));
            if (chunk == null) break;
            playedBytes += chunk.length;
        }
    }

    private void playLoop() {
        Thread.currentThread().setName("MH-MusicPlayer");
        waitForGate();
        if (cancelled) {
            finish();
            return;
        }
        while (!cancelled) {
            try {
                if (playOnce() == PlayResult.FINISHED) {
                    finish();
                    return;
                }
            } catch (Exception e) {
                if (cancelled) break;
                LOGGER.error("Playback error: {}", e.getMessage(), e);
                rebuildForRetry();
                if (cancelled) break;
            }
        }
        finish();
    }

    private enum PlayResult {
        /** 播放自然结束（淡出完成），可以完成整个任务。 */
        FINISHED,
        /** 需要重启（资源已重建、下载线程已重启），播放线程继续下一轮。 */
        RESTARTED
    }

    private void waitForGate() {
        while (!gate.isDone() && !cancelled && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /**
     * One playback session: initialize source, wait for initial buffer, start
     * playback with fade-in, run the main loop until natural end or restart.
     */
    private PlayResult playOnce() {
        initSource();
        waitInitialBuffer();
        if (cancelled) return PlayResult.RESTARTED;

        if (totalBufferedBytes.get() == 0) {
            LOGGER.error("No audio data available");
            setState(PlaybackState.ERROR);
            rebuildForRetry();
            return PlayResult.RESTARTED;
        }

        if (!initialized.get() || source == 0) {
            throw new IllegalStateException("Audio player not initialized");
        }

        boolean firstChunk = true;
        for (int i = 0; i < BUFFER_COUNT; i++) {
            byte[] audioData = audioBuffer.poll();
            if (audioData == null) break;

            if (firstChunk) {
                firstChunk = false;
                // 锚定内容绝对位置：服务器同步时下载线程已把解码器跳到
                // 墙钟位置，fedBytes 从这里开始累计，之后与 expectedBytes
                // （墙钟绝对位置）基准一致。不再减半缓冲补偿——内容与墙钟
                // 完全对齐，出声即同步（见 syncPlaying 注释）。
                if (serverStartTime != null && currentDecoder != null) {
                    long bytesPerSecond = (long) currentDecoder.getSampleRate()
                            * getBytesPerSample(currentDecoder.getFormat());
                    fedBytes = Duration.between(serverStartTime, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000;
                    fedBytes = Math.max(0, fedBytes);
                }
            }

            queueBuffer(buffers[i], audioData);
            fedBytes += audioData.length;
            totalBufferedBytes.addAndGet(-audioData.length);
        }

        if (clientConfig.getDisableVanillaMusic())
            Minecraft.getInstance().getSoundManager().stop(null, SoundSource.MUSIC);

        if (source != 0 && AL10.alIsSource(source)) {
            AL10.alSourcef(source, AL10.AL_GAIN, 0);
            lastSetGain = 0;
        }
        AL10.alSourcePlay(source);
        checkALError("alSourcePlay-Pre");

        // fade progress 与 startFuture 同刻起算：网络缓冲等待不会提前消耗淡入进度，
        // HUD 进度/歌词/淡入起点严格一致
        long fadeDuration = transitionFadeInMs >= 0 ? transitionFadeInMs : fadeIn.durationMs();
        if (state != PlaybackState.FADING_OUT) {
            fadeDurationMs = fadeDuration;
            fadeStartNanos = System.nanoTime();
            setState(PlaybackState.FADING_IN);
        } else {
            LOGGER.debug("Playback started while fade-out already requested, honoring fade-out");
        }
        ZonedDateTime wallStart = Objects.requireNonNullElseGet(serverStartTime, ZonedDateTime::now);
        serverStartTime = wallStart;
        startFuture.complete(wallStart);
        LOGGER.debug("Playback started, wallStart={}, fadeIn={} ms", wallStart, fadeDurationMs);

        mainLoop(wallStart);
        return PlayResult.FINISHED;
    }

    @SuppressWarnings("BusyWait")
    private void mainLoop(ZonedDateTime wallStart) {
        long lastIterationNanos = -1;
        while (!cancelled && !Thread.currentThread().isInterrupted()) {
            try {
                long iterationNow = System.nanoTime();
                if (lastIterationNanos > 0) {
                    long stallMs = (iterationNow - lastIterationNanos) / 1_000_000;
                    if (stallMs > PLAYBACK_STALL_LOG_MS) {
                        // GC STW / 调度暂停：本地线程被冻结，恢复后第一轮间隔 = 冻结时长
                        int bytesPerSample = currentDecoder != null ? getBytesPerSample(currentDecoder.getFormat()) : 4;
                        long bytesPerSecond = (long) (currentDecoder != null ? currentDecoder.getSampleRate() : 44100) * bytesPerSample;
                        long expected = Duration.between(wallStart, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000;
                        LOGGER.info("Playback thread stalled for {} ms (GC STW / scheduling pause?)" +
                                        " [content={} ms expected={} ms bufferedChunks={} processed={} state={}]",
                                stallMs,
                                fedBytes * 1000L / bytesPerSecond, expected * 1000L / bytesPerSecond,
                                audioBuffer.size(),
                                AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED),
                                AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE));
                    }
                }
                lastIterationNanos = iterationNow;

                updateGain();
                if (!initialized.get() || source == 0) break;

                if (state == PlaybackState.FADING_OUT && fadeProgress() >= 1.0) break;

                checkDecoderChangeAndFlush();

                int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
                //noinspection SpellCheckingInspection
                checkALError("alGetSourcei-Processed");

                int bytesPerSample = currentDecoder != null ? getBytesPerSample(currentDecoder.getFormat()) : 4;
                long bytesPerSecond = (long) (currentDecoder != null ? currentDecoder.getSampleRate() : 44100) * bytesPerSample;
                long expectedBytes = Duration.between(wallStart, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000;
                long staleDropMarginBytes = STALE_DROP_MARGIN_MS * bytesPerSecond / 1000;

                while (processed-- > 0) {
                    int[] buffer = new int[1];
                    AL10.alSourceUnqueueBuffers(source, buffer);
                    //noinspection SpellCheckingInspection
                    checkALError("alSourceUnqueueBuffers-Main");

                    byte[] audioData = audioBuffer.poll(0, TimeUnit.MILLISECONDS);
                    if (audioData != null && underrunSinceNanos >= 0) {
                        // 欠载恢复：数据位置已被下载线程 sync 对齐墙钟，重置基准并跳过陈旧判定，
                        // 否则落后的 fedBytes 会把墙钟位置的新鲜数据误判为陈旧连续丢弃（音频提前）
                        underrunSinceNanos = -1;
                        fedBytes = expectedBytes;
                        logUnderrunDiagnostics("underrun-recovered", expectedBytes, bytesPerSecond);
                    } else if (audioData == null) {
                        if (!downloadDone && underrunSinceNanos < 0) {
                            underrunSinceNanos = System.nanoTime();
                            logUnderrunDiagnostics("underrun-started", expectedBytes, bytesPerSecond);
                        }
                        continue;
                    } else {
                        while (audioData != null && fedBytes < expectedBytes - staleDropMarginBytes) {
                            totalBufferedBytes.addAndGet(-audioData.length);
                            fedBytes += audioData.length;
                            logStaleDrop(audioData.length, bytesPerSecond);
                            audioData = audioBuffer.poll(0, TimeUnit.MILLISECONDS);
                        }
                        if (audioData == null) {
                            if (!downloadDone && underrunSinceNanos < 0) {
                                underrunSinceNanos = System.nanoTime();
                                logUnderrunDiagnostics("underrun-started", expectedBytes, bytesPerSecond);
                            }
                            continue;
                        }
                    }
                    if (state == PlaybackState.BUFFERING || state == PlaybackState.LOADING) setState(PlaybackState.PLAYING);

                    queueBuffer(buffer[0], audioData);
                    fedBytes += audioData.length;
                    if (audioData.length == BUFFER_SIZE) {
                        totalBufferedBytes.addAndGet(-audioData.length);
                    }
                }

                if (underrunSinceNanos < 0 && audioBuffer.isEmpty() && !downloadDone) {
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

                if (downloadDone && audioBuffer.isEmpty() && state != PlaybackState.FADING_OUT
                        && state != PlaybackState.ERROR && state != PlaybackState.RETRYING) {
                    int queuedNow = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
                    if (queuedNow == 0) {
                        beginFadeOut(fadeOut.durationMs());
                    }
                }

                // 下载完成后内容耗尽应走自然结束淡出，不再上报 BUFFERING
                if (underrunSinceNanos >= 0 && !downloadDone && state != PlaybackState.ERROR && state != PlaybackState.RETRYING
                        && state != PlaybackState.FADING_IN && state != PlaybackState.FADING_OUT) {
                    long underrunMs = (System.nanoTime() - underrunSinceNanos) / 1_000_000;
                    if (underrunMs >= UNDERFLOW_BUFFERING_DELAY_MS) {
                        setState(PlaybackState.BUFFERING);
                    }
                }

                refillBuffersIfSourceEmpty(expectedBytes, bytesPerSecond);

                int sourceState = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
                //noinspection SpellCheckingInspection
                checkALError("alGetSourcei-SourceState");
                int queuedNow = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
                if (sourceState != AL10.AL_PLAYING && queuedNow > 0 && !cancelled) {
                    AL10.alSourcePlay(source);
                    checkALError("alSourcePlay-Main");
                }
                Thread.sleep(PLAY_LOOP_SLEEP_MS);
            } catch (InterruptedException e) {
                break;
            }
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
            int queued = source != 0 && AL10.alIsSource(source) ? AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED) : -1;
            int processed = source != 0 && AL10.alIsSource(source) ? AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED) : -1;
            LOGGER.debug("PlaybackDiagnostics {}: fedBytes={} ms, expectedBytes={} ms, deltaMs={}, "
                            + "bufferedChunks={}, sourceQueued={}, sourceProcessed={}, state={}",
                    event,
                    fedBytes * 1000L / bytesPerSecond, expectedBytes * 1000L / bytesPerSecond,
                    (fedBytes - expectedBytes) * 1000L / bytesPerSecond,
                    audioBuffer.size(), queued, processed, state);
        } catch (Exception e) {
            LOGGER.warn("PlaybackDiagnostics failed to log {}: {}", event, e.getMessage());
        }
    }

    private void refillBuffersIfSourceEmpty(long expectedBytes, long bytesPerSecond) {
        if (source == 0 || !AL10.alIsSource(source)) return;
        int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
        if (queued > 0) return;

        boolean wasUnderrun = underrunSinceNanos >= 0;
        if (wasUnderrun) {
            underrunSinceNanos = -1;
        }
        // source 已空即重新起播点：填充数据位置已被 sync 对齐墙钟，
        // 对齐落后基准，避免欠播期间累积的落后周期性触发陈旧丢弃（跳音/空帧）
        if (fedBytes < expectedBytes) {
            fedBytes = expectedBytes;
        }

        boolean filled = false;
        int fillCount = 0;
        while (!cancelled && fillCount < BUFFER_COUNT) {
            byte[] audioData = audioBuffer.poll();
            if (audioData == null) break;
            fillCount++;

            int bufferId = buffers[roundRobinBufferIndex];
            roundRobinBufferIndex = (roundRobinBufferIndex + 1) % BUFFER_COUNT;
            queueBuffer(bufferId, audioData);
            fedBytes += audioData.length;
            if (audioData.length == BUFFER_SIZE) {
                totalBufferedBytes.addAndGet(-audioData.length);
            }
            filled = true;
        }
        if (filled) {
            if (wasUnderrun) {
                logUnderrunDiagnostics("underrun-recovered", expectedBytes, bytesPerSecond);
            }
            if (state == PlaybackState.BUFFERING || state == PlaybackState.LOADING) setState(PlaybackState.PLAYING);
            AL10.alSourcePlay(source);
            checkALError("alSourcePlay-Fill");
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
                    fedBytes * 1000L / bytesPerSecond, expected * 1000L / bytesPerSecond);
        }
    }

    private void updateGain() {
        float targetGain = clientConfig.getMuted() ? 0 : (float) clientConfig.getSoundVolume() / 100 *
                (clientConfig.getMixWithVanillaSoundVolume() ? Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MUSIC) : 1);
        if (lastVolume != targetGain && source != 0 && AL10.alIsSource(source)) {
            PlayingStatusRenderer.getInstance().updateStatus(null);
            lastVolume = targetGain;
        }
        if (source == 0 || !AL10.alIsSource(source)) return;

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
            AL10.alSourcef(source, AL10.AL_GAIN, gain);
            int error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                LOGGER.warn("Failed to set source gain to {}: {} (source: {})", gain, getALErrorString(error), source);
                if (error == AL10.AL_INVALID_NAME) {
                    LOGGER.error("Source {} is invalid, will be reinitialized on next restart", source);
                    source = 0;
                    initialized.set(false);
                    throw new RuntimeException("AL source invalid");
                } else {
                    setState(PlaybackState.ERROR);
                }
            } else {
                lastSetGain = gain;
            }
        }
    }

    private void initSource() {
        source = AL10.alGenSources();
        checkALError("alGenSources");

        for (int i = 0; i < BUFFER_COUNT; i++) {
            buffers[i] = AL10.alGenBuffers();
            checkALError("alGenBuffers");
        }

        // 配置为非空间播放
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0);
        if (AL.getCapabilities().AL_SOFT_direct_channels) {
            AL10.alSourcei(source, SOFTDirectChannels.AL_DIRECT_CHANNELS_SOFT, AL10.AL_TRUE);
        }
        checkALError("source configuration");
        lastVolume = 1;
        lastSetGain = -1;

        initialized.set(true);
    }

    private void queueBuffer(int bufferId, byte[] audioData) {
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(audioData.length);
        directBuffer.put(audioData);
        directBuffer.flip();

        int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
        int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;

        AL10.alBufferData(bufferId, format, directBuffer, sampleRate);
        checkALError("alBufferData");
        AL10.alSourceQueueBuffers(source, bufferId);
        checkALError("alSourceQueueBuffers");
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
            if (source != 0) {
                AL10.alSourceStop(source);
                checkALError("alSourceStop-Flush");
                int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
                checkALError("alGetSourcei-Queued-Flush");
                for (int i = 0; i < queued; i++) {
                    int[] buffer = new int[1];
                    AL10.alSourceUnqueueBuffers(source, buffer);
                    checkALError("alSourceUnqueueBuffers-Flush");
                }
            }
            fedBytes = 0;
            audioBuffer.clear();
            totalBufferedBytes.set(0);
            roundRobinBufferIndex = 0;
        }
    }

    private int getBytesPerSample(int format) {
        return switch (format) {
            case AL10.AL_FORMAT_MONO8 -> 1;
            case AL10.AL_FORMAT_MONO16, AL10.AL_FORMAT_STEREO8 -> 2;
            case AL10.AL_FORMAT_STEREO16 -> 4;
            default -> 4;
        };
    }

    private void checkALError(String operation) {
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            String errorMsg = getALErrorString(error);
            LOGGER.warn("OpenAL Error during {}: {} ({})", operation, errorMsg, error);
            throw new RuntimeException("al error occurred while \"" + operation + "\": " + errorMsg);
        }
    }

    private String getALErrorString(int error) {
        return switch (error) {
            case AL10.AL_INVALID_NAME -> "AL_INVALID_NAME";
            case AL10.AL_INVALID_ENUM -> "AL_INVALID_ENUM";
            case AL10.AL_INVALID_VALUE -> "AL_INVALID_VALUE";
            case AL10.AL_INVALID_OPERATION -> "AL_INVALID_OPERATION";
            case AL10.AL_OUT_OF_MEMORY -> "AL_OUT_OF_MEMORY";
            default -> "UNKNOWN_ERROR";
        };
    }

    private void rebuildForRetry() {
        restartRequested = true;
        setState(PlaybackState.RETRYING);
        if (downloadThreadFuture != null) {
            downloadThreadFuture.cancel(true);
            downloadThreadFuture = null;
        }
        cleanup();
        try {
            Thread.sleep(FULLY_RETRY_SLEEP_MS);
        } catch (InterruptedException ignored) {
            if (cancelled) return;
        }
        if (cancelled) return;
        restartRequested = false;
        LOGGER.info("Fully retrying");
        downloadThreadFuture = MusicHud.EXECUTOR.submit(this::downloadLoop);
    }

    private void waitInitialBuffer() {
        long lastRestartTimestamp = 0;
        while (!cancelled && !downloadDone && totalBufferedBytes.get() < (long) BUFFER_SIZE * BUFFER_COUNT) {
            Future<?> currentDownloadFuture = downloadThreadFuture;
            if (currentDownloadFuture != null && currentDownloadFuture.isDone() && !downloadDone
                    && System.currentTimeMillis() - lastRestartTimestamp > 1000) {
                LOGGER.warn("Download thread died unexpectedly, restarting download");
                downloadThreadFuture = MusicHud.EXECUTOR.submit(this::downloadLoop);
                lastRestartTimestamp = System.currentTimeMillis();
            }
            try {
                //noinspection BusyWait
                Thread.sleep(INITIAL_BUFFER_WAIT_SLEEP_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void finish() {
        cleanup();
        setState(PlaybackState.FINISHED);
        finishFuture.complete(null);
    }

    private void cleanup() {
        try {
            if (source != 0 && AL10.alIsSource(source)) {
                AL10.alSourceStop(source);
                int error = AL10.alGetError();
                if (error != AL10.AL_NO_ERROR) {
                    LOGGER.warn("Error stopping source {}: {}", source, getALErrorString(error));
                }

                int processed;
                try {
                    processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
                    error = AL10.alGetError();
                    if (error != AL10.AL_NO_ERROR) {
                        LOGGER.warn("Error getting processed buffers: {}", getALErrorString(error));
                        processed = BUFFER_COUNT;
                    }
                } catch (Exception e) {
                    LOGGER.warn("Exception getting processed buffers", e);
                    processed = BUFFER_COUNT;
                }

                //noinspection SpellCheckingInspection
                int unqueueCount = 0;
                for (int i = 0; i < processed; i++) {
                    int[] buffer = new int[1];
                    AL10.alSourceUnqueueBuffers(source, buffer);
                    error = AL10.alGetError();
                    if (error != AL10.AL_NO_ERROR) {
                        LOGGER.warn("Failed to unqueue buffer (attempt {}): {}", i + 1, getALErrorString(error));
                    } else {
                        unqueueCount++;
                    }
                }
                LOGGER.debug("Unqueued {} buffers", unqueueCount);

                AL10.alDeleteSources(source);
                error = AL10.alGetError();
                if (error != AL10.AL_NO_ERROR) {
                    LOGGER.warn("Failed to delete source {}: {}", source, getALErrorString(error));
                }
                source = 0;
            }

            for (int i = 0; i < buffers.length; i++) {
                if (buffers[i] != 0) {
                    if (AL10.alIsBuffer(buffers[i])) {
                        AL10.alDeleteBuffers(buffers[i]);
                        int error = AL10.alGetError();
                        if (error != AL10.AL_NO_ERROR) {
                            LOGGER.warn("Failed to delete buffer {}: {}", buffers[i], getALErrorString(error));
                        }
                    } else {
                        LOGGER.warn("Buffer {} is not a valid OpenAL buffer", buffers[i]);
                    }
                    buffers[i] = 0;
                }
            }

            initialized.set(false);
            lastVolume = 1;
            lastSetGain = -1;
            downloadDone = false;
            underrunSinceNanos = -1;
            roundRobinBufferIndex = 0;
            audioBuffer = new LinkedBlockingQueue<>(AUDIO_BUFFER_CAPACITY);
            totalBufferedBytes.set(0);
            playedBytes = 0;
            fedBytes = 0;
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
        FormatType formatType1 = formatType;
        if (formatType1 == FormatType.AUTO) {
            formatType1 = AudioFormatDetector.detectFormat(inputStream);
        }
        return switch (formatType1) {
            case WAV -> new WavStreamDecoder(inputStream);
            case MP3 -> new MP3StreamDecoder(inputStream);
            case FLAC -> new FLACStreamDecoder(inputStream);
            case AUTO -> {
                throw new IllegalArgumentException();
            }
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
                .thenApply(GetMusicResourceResponse::getMusicResourceInfo)
                .thenCompose(value -> {
                    if (value == MusicResourceInfo.NONE) {
                        MusicService.getInstance().switchMusic(MusicDetail.NONE, MusicDetail.NONE, null, I18n.get(MusicHud.MOD_ID + ".text.failedToLoadMusicResource"));
                        setState(PlaybackState.ERROR);
                        return CompletableFuture.failedFuture(new RuntimeException("Failed to load music resource"));
                    }
                    return CompletableFuture.completedFuture(value);
                });
    }
}
