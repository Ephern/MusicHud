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
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
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
     * wallStart（服务器时间 ?? 本地起播时刻），此后保持不变
     */
    private volatile ZonedDateTime serverStartTime;
    /**
     * 调度器在交叉淡化时指定的淡入时长（transitionMs = max(outgoing.fadeOut, incoming.fadeIn)）。
     * -1 表示未指定，起播时使用任务自己的 fadeIn
     */
    private volatile long transitionFadeInMs = -1;
    private final Fade fadeIn;
    private final Fade fadeOut;
    private final CompletableFuture<ZonedDateTime> startFuture = new CompletableFuture<>();
    private final CompletableFuture<Void> finishFuture = new CompletableFuture<>();
    private final CountDownLatch gate = new CountDownLatch(1);
    private final Set<Consumer<PlaybackState>> stateListeners = new CopyOnWriteArraySet<>();
    private final PlaybackLedger ledger = new PlaybackLedger();

    private volatile BlockingQueue<byte[]> audioBuffer = new LinkedBlockingQueue<>(AUDIO_BUFFER_CAPACITY);
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
    private long lastStaleDropLogTime = 0;
    private long lastUnderrunLogTime = 0;
    private long lastDownloadRestartTimestamp = 0;
    private long underrunSinceNanos = -1;
    private volatile Future<?> downloadThreadFuture;
    private volatile Future<?> playThreadFuture;
    private final AtomicBoolean cleanupGuard = new AtomicBoolean();

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
        gate.countDown();
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
                    ledger.prefetchBytes.addAndGet(audioData.length);
                    initialBuffers++;
                }

                while (!cancelled && !restartRequested) {
                    // 保持解码器头与墙钟对齐（无论下载缓冲是否充足）
                    syncPlaying();

                    byte[] audioData = decoder.readChunk(BUFFER_SIZE);
                    if (audioData == null) break;
                    if (cancelled || restartRequested) break;

                    audioBuffer.put(audioData);
                    ledger.decodedBytes.addAndGet(audioData.length);
                    ledger.prefetchBytes.addAndGet(audioData.length);
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

                audioBuffer.clear();
                ledger.prefetchBytes.set(0);
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

    private void syncPlaying() {
        if (serverStartTime == null || currentDecoder == null) return;
        int bytesPerSample = OpenAlSource.bytesPerSample(currentDecoder.getFormat());
        long bytesPerSecond = (long) currentDecoder.getSampleRate() * bytesPerSample;

        while (!cancelled && !restartRequested) {
            long millis = Duration.between(serverStartTime, ZonedDateTime.now()).toMillis();
            long skipBytes = millis * bytesPerSecond / 1000;
            if (ledger.decodedBytes.get() > skipBytes - bytesPerSample) {
                break;
            }
            byte[] chunk = currentDecoder.readChunk(Math.max(0, skipBytes - ledger.decodedBytes.get()));
            if (chunk == null) break;
            ledger.decodedBytes.addAndGet(chunk.length);
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
        try {
            gate.await();
        } catch (InterruptedException ignored) {}
    }

    /**
     * One playback session: initialize source, wait for initial buffer, start
     * playback with fade-in, run the main loop until natural end or restart.
     */
    private PlayResult playOnce() {
        source = OpenAlSource.create(new OpenAlSource.Config(BUFFER_COUNT, AL.getCapabilities().AL_SOFT_direct_channels), ledger);
        waitInitialBuffer();
        if (cancelled) return PlayResult.RESTARTED;

        if (ledger.prefetchBytes.get() == 0) {
            LOGGER.error("No audio data available");
            setState(PlaybackState.ERROR);
            rebuildForRetry();
            return PlayResult.RESTARTED;
        }

        if (source == null) {
            throw new IllegalStateException("Audio player not initialized");
        }

        int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
        int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;

        boolean firstChunk = true;
        for (int i = 0; i < BUFFER_COUNT; i++) {
            byte[] audioData = audioBuffer.poll();
            if (audioData == null) break;

            if (firstChunk) {
                firstChunk = false;
                // 锚定内容绝对位置：服务器同步时下载线程已把解码器跳到
                // 墙钟位置，ledger.fedBytes 从这里开始累计，之后与 expectedBytes
                // （墙钟绝对位置）基准一致。不再减半缓冲补偿——内容与墙钟
                // 完全对齐，出声即同步（见 syncPlaying 注释）。
                if (serverStartTime != null && currentDecoder != null) {
                    long bytesPerSecond = (long) currentDecoder.getSampleRate()
                            * OpenAlSource.bytesPerSample(currentDecoder.getFormat());
                    ledger.anchor(Math.max(0, Duration.between(serverStartTime, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000));
                }
            }

            source.queueChunk(audioData, format, sampleRate);
            ledger.prefetchBytes.addAndGet(-audioData.length);
        }

        if (clientConfig.getDisableVanillaMusic()) {
            // SoundManager 仅允许在主线程访问，投递到客户端线程
            Minecraft.getInstance().execute(() ->
                    Minecraft.getInstance().getSoundManager().stop(null, SoundSource.MUSIC));
        }

        source.setGain(0);
        lastSetGain = 0;
        source.play();

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
                        int bytesPerSample = currentDecoder != null ? OpenAlSource.bytesPerSample(currentDecoder.getFormat()) : 4;
                        long bytesPerSecond = (long) (currentDecoder != null ? currentDecoder.getSampleRate() : 44100) * bytesPerSample;
                        long expected = Duration.between(wallStart, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000;
                        LOGGER.info("Playback thread stalled for {} ms (GC STW / scheduling pause?)" +
                                        " [content={} ms expected={} ms bufferedChunks={} sourceQueued={} playing={} state={}]",
                                stallMs,
                                ledger.fedBytes.get() * 1000L / bytesPerSecond, expected * 1000L / bytesPerSecond,
                                audioBuffer.size(),
                                source != null ? source.queuedCount() : -1,
                                source != null && source.isPlaying(),
                                state);
                    }
                }
                lastIterationNanos = iterationNow;

                updateGain();
                if (source == null) break;

                if (state == PlaybackState.FADING_OUT && fadeProgress() >= 1.0) break;

                checkDecoderChangeAndFlush();

                int bytesPerSample = currentDecoder != null ? OpenAlSource.bytesPerSample(currentDecoder.getFormat()) : 4;
                long bytesPerSecond = (long) (currentDecoder != null ? currentDecoder.getSampleRate() : 44100) * bytesPerSample;
                long expectedBytes = Duration.between(wallStart, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000;
                long staleDropMarginBytes = STALE_DROP_MARGIN_MS * bytesPerSecond / 1000;
                int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
                int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;

                // source 已空即重新起播点：填充数据位置已被 sync 对齐墙钟，
                // 对齐落后基准，避免欠播累积的落后触发陈旧丢弃（跳音/空帧）。
                // 欠载恢复需等待缓冲积累到阈值再填充（与 waitInitialBuffer 一致），
                // 否则边产边播使队列无法建立超前缓冲，下载产出≈播放时周期性空帧。
                boolean sourceEmpty = source.isEmpty();
                boolean waitForBuffer = sourceEmpty && !downloadDone
                        && ledger.prefetchBytes.get() < (long) BUFFER_SIZE * BUFFER_COUNT;
                if (!waitForBuffer) {
                    if (sourceEmpty) {
                        if (underrunSinceNanos >= 0) {
                            underrunSinceNanos = -1;
                            logUnderrunDiagnostics("underrun-recovered", expectedBytes, bytesPerSecond);
                        }
                        if (ledger.fedBytes.get() < expectedBytes) {
                            ledger.fedBytes.set(expectedBytes);
                        }
                    }
                    int filled = source.fill(() -> takeChunk(expectedBytes, staleDropMarginBytes, bytesPerSecond),
                            BUFFER_COUNT, format, sampleRate);
                    if (filled > 0) {
                        resumeIfWaiting();
                    }
                }

                updateUnderrunState(expectedBytes, bytesPerSecond);
                source.updatePlaybackPosition();

                if (!source.isPlaying() && source.queuedCount() > 0 && !cancelled) {
                    source.play();
                }
                Thread.sleep(PLAY_LOOP_SLEEP_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * 取一块数据供 OpenAL 填充：poll 预取队列，应用陈旧丢弃与欠载策略。
     * 欠载恢复时重置 ledger 基准到墙钟并跳过陈旧判定（数据已被 sync 对齐）。
     */
    private byte @Nullable [] takeChunk(long expectedBytes, long staleDropMarginBytes, long bytesPerSecond) {
        if (underrunSinceNanos >= 0) {
            underrunSinceNanos = -1;
            ledger.fedBytes.set(expectedBytes);
            logUnderrunDiagnostics("underrun-recovered", expectedBytes, bytesPerSecond);
            byte[] data = audioBuffer.poll();
            if (data == null) {
                if (!downloadDone) {
                    underrunSinceNanos = System.nanoTime();
                    logUnderrunDiagnostics("underrun-started", expectedBytes, bytesPerSecond);
                }
                return null;
            }
            ledger.prefetchBytes.addAndGet(-data.length);
            return data;
        }
        byte[] data = audioBuffer.poll();
        if (data != null) {
            ledger.prefetchBytes.addAndGet(-data.length);
        }
        while (data != null && ledger.fedBytes.get() < expectedBytes - staleDropMarginBytes) {
            // 陈旧内容：丢弃并累加 fedBytes 推进绝对位置，追上墙钟后接住新鲜内容；
            // 若覆盖赋值会把位置压塌导致整个队列被误判陈旧。
            ledger.fedBytes.addAndGet(data.length);
            logStaleDrop(data.length, bytesPerSecond);
            data = audioBuffer.poll();
            if (data != null) {
                ledger.prefetchBytes.addAndGet(-data.length);
            }
        }
        if (data == null && !downloadDone && underrunSinceNanos < 0) {
            underrunSinceNanos = System.nanoTime();
            logUnderrunDiagnostics("underrun-started", expectedBytes, bytesPerSecond);
        }
        return data;
    }

    private void resumeIfWaiting() {
        if (state == PlaybackState.LOADING || state == PlaybackState.BUFFERING) {
            setState(PlaybackState.PLAYING);
        }
    }

    private void updateUnderrunState(long expectedBytes, long bytesPerSecond) {
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
            if (source != null && source.queuedCount() == 0) {
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
                        audioBuffer.size(), queued, state);
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
            // INVALID_NAME 时门面抛 SourceInvalidException，由 playLoop 统一走 rebuild
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
            audioBuffer.clear();
            ledger.prefetchBytes.set(0);
        }
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
        while (!cancelled && !downloadDone && ledger.prefetchBytes.get() < (long) BUFFER_SIZE * BUFFER_COUNT) {
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
            audioBuffer = new LinkedBlockingQueue<>(AUDIO_BUFFER_CAPACITY);
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
