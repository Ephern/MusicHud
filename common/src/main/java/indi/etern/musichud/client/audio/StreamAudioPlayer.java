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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.SOFTDirectChannels;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class StreamAudioPlayer {
    private static final int BUFFER_COUNT = 8;
    private static final int BUFFER_SIZE = 65536;
    private static final int AUDIO_BUFFER_CAPACITY = 60;
    private static final long STALE_DROP_MARGIN_MS = 500;// 内容落后墙钟超过该值才丢弃
    private static final long PLAYBACK_STALL_LOG_MS = 500;// 播放线程迭代间隔超过该值记录停滞
    private static final long PLAY_LOOP_SLEEP_MS = 40;// 主循环轮询间隔
    private static final long INITIAL_BUFFER_WAIT_SLEEP_MS = 50;// 初始缓冲等待轮询间隔
    private static final long FULLY_RETRY_SLEEP_MS = 1000;// 全量重试前的等待
    private static final long PLAY_INIT_RETRY_SLEEP_MS = 500;// 播放初始化失败后的重试等待
    private static final Logger LOGGER = MusicHud.getLogger(StreamAudioPlayer.class);
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static volatile StreamAudioPlayer instance = null;
    private final int[] buffers = new int[BUFFER_COUNT];
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicReference<Status> status = new AtomicReference<>(Status.IDLE);
    @Getter
    private final Set<Consumer<Status>> statusChangeListener = new HashSet<>();
    private final AtomicLong totalBufferedBytes = new AtomicLong(0);
    private volatile BlockingQueue<byte[]> audioBuffer = new LinkedBlockingQueue<>(AUDIO_BUFFER_CAPACITY);
    private int source = 0;
    private float lastVolume;
    private volatile CompletableFuture<?> playingFuture;
    private volatile CompletableFuture<?> downloadFuture;
    private MusicDetail currentMusicDetail;
    private AudioDecoder currentDecoder;
    private long playedBytes = 0;
    private long fedBytes = 0;
    private int lastDecoderFormat = -1;
    private int lastDecoderSampleRate = -1;
    private volatile ZonedDateTime serverStartTime;
    private Future<?> downloadThreadFuture;
    private Future<?> playThreadFuture;
    private long lastStaleDropLogTime = 0;

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

    public Status getStatus() {
        return status.get();
    }

    private void setStatus(Status status) {
        if (this.status.get() != status) {
            this.status.set(status);
            statusChangeListener.forEach(c -> c.accept(status));
        }
    }

    protected void fullyRetryCurrent(@Nullable CompletableFuture<ZonedDateTime> startPlayingFuture) {
        MusicDetail currentMusicDetail1 = currentMusicDetail;
        ZonedDateTime serverStartTime1 = serverStartTime;
        stopInternal();
        try {
            Thread.sleep(FULLY_RETRY_SLEEP_MS);
        } catch (InterruptedException ignored) {
        } finally {
            LOGGER.info("Fully retrying");
            CompletableFuture<ZonedDateTime> zonedDateTimeCompletableFuture = playAsyncInternal(currentMusicDetail1, serverStartTime1);
            if (startPlayingFuture != null) {
                zonedDateTimeCompletableFuture
                        .thenAccept(startPlayingFuture::complete)
                        .exceptionally(e -> {
                            startPlayingFuture.completeExceptionally(e);
                            return null;
                        });
            }
        }
    }

    public CompletableFuture<ZonedDateTime> playAsync(MusicDetail musicDetail, ZonedDateTime startTime) {
        stopInternal();
        return playAsyncInternal(musicDetail, startTime);
    }

    private @NotNull CompletableFuture<ZonedDateTime> playAsyncInternal(MusicDetail musicDetail, ZonedDateTime startTime) {
        try {
            currentMusicDetail = musicDetail;
            setStatus(Status.BUFFERING);

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

            initialized.set(true);
        } catch (Exception e) {
            stopInternal();
            try {
                Thread.sleep(PLAY_INIT_RETRY_SLEEP_MS);
            } catch (Exception ignored) {
            }
            return playAsyncInternal(musicDetail, startTime);
        }

        CompletableFuture<Void> downloadInitializedFuture = new CompletableFuture<>();
        CompletableFuture<ZonedDateTime> startPlayingFuture = new CompletableFuture<>();
        serverStartTime = startTime;
        downloadFuture = new CompletableFuture<>();
        playingFuture = new CompletableFuture<>();
        downloadThreadFuture = MusicHud.EXECUTOR.submit(() -> {
            Thread.currentThread().setName("MHWorker-Downloader");
            try {
                downloadAudioWithRetry(startTime != null, downloadInitializedFuture);
            } catch (Exception e) {
                LOGGER.error("Download thread error", e);
                setStatus(Status.ERROR);
                try {
                    fullyRetryCurrent(startPlayingFuture);
                } catch (RuntimeException e1) {
                    LOGGER.error("Retry failed: {}: {}", e1.getClass(), e1.getMessage());
                }
            } finally {
                downloadInitializedFuture.complete(null);
            }
        });
        downloadInitializedFuture.thenAccept(ignore -> {
            playThreadFuture = MusicHud.EXECUTOR.submit(() -> {
                Thread.currentThread().setName("MH-MusicPlayer");
                try {
                    playAudioWithRetry(startPlayingFuture, startTime);
                } catch (Exception e) {
                    LOGGER.error("Play thread error: {}", e.getMessage());
                    if (!startPlayingFuture.isDone()) {
                        startPlayingFuture.completeExceptionally(e);
                    }
                }
            });
        });

        return startPlayingFuture;
    }

    @SuppressWarnings("BusyWait")
    private void playAudioWithRetry(CompletableFuture<ZonedDateTime> startPlayingFuture, ZonedDateTime serverStartTime) {
        CompletableFuture<?> currentPlayingFuture = playingFuture;
        CompletableFuture<?> currentDownloadFuture = downloadFuture;
        BlockingQueue<byte[]> playBuffer = audioBuffer;
        boolean finished = false;
        try {
            // 等待一些数据缓冲
            while (currentPlayingFuture != null && !currentPlayingFuture.isDone() && currentPlayingFuture == playingFuture && totalBufferedBytes.get() < BUFFER_SIZE * BUFFER_COUNT) {
                Thread.sleep(INITIAL_BUFFER_WAIT_SLEEP_MS);
            }

            if (totalBufferedBytes.get() == 0) {
                LOGGER.error("No audio data available");
                if (currentPlayingFuture != null && playingFuture != null) {
                    setStatus(Status.ERROR);
                }
                fullyRetryCurrent(startPlayingFuture);
            } else {
                if (!initialized.get() || source == 0) {
                    startPlayingFuture.completeExceptionally(new IllegalStateException("Audio player not initialized"));
                    finished = true;
                } else {// 从缓冲区填充初始数据
                    boolean firstChunk = true;
                    for (int i = 0; i < BUFFER_COUNT; i++) {
                        byte[] audioData = playBuffer.poll(0, TimeUnit.SECONDS);
                        if (audioData == null) break;

                        if (firstChunk) {
                            firstChunk = false;
                            // 锚定内容绝对位置：服务器同步时下载线程已把解码器跳到
                            // 墙钟 - 初始缓冲补偿，fedBytes 从这里开始累计，之后
                            // 与 expectedBytes（墙钟绝对位置）基准一致
                            if (serverStartTime != null && currentDecoder != null) {
                                long bytesPerSecond = (long) currentDecoder.getSampleRate()
                                        * getBytesPerSample(currentDecoder.getFormat());
                                fedBytes = Duration.between(serverStartTime, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000
                                        - (long) BUFFER_SIZE * BUFFER_COUNT / 2;
                                fedBytes = Math.max(0, fedBytes);
                            }
                        }

                        ByteBuffer directBuffer = ByteBuffer.allocateDirect(audioData.length);
                        directBuffer.put(audioData);
                        directBuffer.flip();

                        int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
                        int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;

                        AL10.alBufferData(buffers[i], format, directBuffer, sampleRate);
                        checkALError("alBufferData-Pre");
                        AL10.alSourceQueueBuffers(source, buffers[i]);
                        checkALError("alSourceQueueBuffers-Pre");

                        fedBytes += audioData.length;
                        totalBufferedBytes.addAndGet(-audioData.length);
                    }
                    if (clientConfig.getDisableVanillaMusic())
                        Minecraft.getInstance().getSoundManager().stop(null, SoundSource.MUSIC);
                    setStatus(Status.PLAYING);
                    AL10.alSourcePlay(source);
                    checkALError("alSourcePlay-Pre");
                }

                if (!finished) {
                    ZonedDateTime wallStart = Objects.requireNonNullElseGet(serverStartTime, ZonedDateTime::now);
                    this.serverStartTime = wallStart;
                    long lastIterationNanos = -1;
                    while (currentPlayingFuture != null && !currentPlayingFuture.isDone() && currentPlayingFuture == playingFuture) {
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
                                    // 注意：这里不要清空 audioBuffer 或重置 fedBytes——那会破坏内容连续性
                                    // （下载线程的解码器头不会回退），导致后续排队的内容与墙钟脱节。
                                    // refill 的陈旧判定会自然丢弃 [内容位置, 墙钟-余量) 的过期内容，
                                    // 剩余内容按序播放，与墙钟的偏差保持在余量之内。
                                }
                            }
                            lastIterationNanos = iterationNow;

                            updateVolumeIfNecessary();
                            if (!initialized.get() || source == 0) break;

                            checkDecoderChangeAndFlush();

                            int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
                            //noinspection SpellCheckingInspection
                            checkALError("alGetSourcei-Processed");

                            startPlayingFuture.complete(wallStart);

                            int bytesPerSample = currentDecoder != null ? getBytesPerSample(currentDecoder.getFormat()) : 4;
                            long bytesPerSecond = (long) (currentDecoder != null ? currentDecoder.getSampleRate() : 44100) * bytesPerSample;
                            long expectedBytes = Duration.between(wallStart, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000;
                            long staleDropMarginBytes = STALE_DROP_MARGIN_MS * bytesPerSecond / 1000;

                            while (processed-- > 0) {
                                int[] buffer = new int[1];
                                AL10.alSourceUnqueueBuffers(source, buffer);
                                //noinspection SpellCheckingInspection
                                checkALError("alSourceUnqueueBuffers-Main");

                                byte[] audioData = playBuffer.poll(0, TimeUnit.MILLISECONDS);
                                while (audioData != null && fedBytes < expectedBytes - staleDropMarginBytes) {
                                    // 陈旧内容：落后墙钟超过余量，丢弃而不是排入 OpenAL 源。
                                    // 下载线程的 syncPlaying 保证解码器头 ≥ 墙钟，新鲜内容随后到达。
                                    totalBufferedBytes.addAndGet(-audioData.length);
                                    fedBytes += audioData.length;
                                    logStaleDrop(audioData.length, bytesPerSecond);
                                    audioData = playBuffer.poll(0, TimeUnit.MILLISECONDS);
                                }

                                if (audioData == null) {
                                    if (playBuffer.isEmpty() && NowPlayingInfo.getInstance().isCompleted()) {
                                        // 播放已完成且缓冲区为空，结束播放
                                        LOGGER.debug("No more audio data available");
                                        currentPlayingFuture.complete(null);
                                        setStatus(Status.PLAYING);
                                        break;
                                    } else if (!currentDownloadFuture.isDone()) {
                                        audioData = new byte[BUFFER_SIZE];
                                        if (status.get() != Status.ERROR && status.get() != Status.RETRYING) {
                                            setStatus(Status.BUFFERING);
                                        }
                                    } else {
                                        audioData = new byte[BUFFER_SIZE];
                                    }
                                } else {
                                    setStatus(Status.PLAYING);
                                }

                                ByteBuffer directBuffer = ByteBuffer.allocateDirect(audioData.length);
                                directBuffer.put(audioData);
                                directBuffer.flip();

                                int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
                                int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;

                                AL10.alBufferData(buffer[0], format, directBuffer, sampleRate);
                                checkALError("alBufferData-Main");
                                AL10.alSourceQueueBuffers(source, buffer[0]);
                                checkALError("alSourceQueueBuffers-Main");

                                // 静音垫占用的时间也计入位置，避免恢复后的新内容被误判为陈旧
                                fedBytes += audioData.length;
                                if (audioData.length == BUFFER_SIZE) { // 不是静音数据
                                    totalBufferedBytes.addAndGet(-audioData.length);
                                }
                            }

                            int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
                            //noinspection SpellCheckingInspection
                            checkALError("alGetSourcei-SourceState");
                            if (state != AL10.AL_PLAYING && !currentPlayingFuture.isDone() && currentPlayingFuture == playingFuture) {
                                AL10.alSourcePlay(source);
                                checkALError("alSourcePlay-Main");
                            }
                            Thread.sleep(PLAY_LOOP_SLEEP_MS);
                        } catch (InterruptedException e) {
                            break;
                        } catch (Exception e) {
                            LOGGER.error("Playback error: {}", e.getMessage(), e);
                            try {
                                fullyRetryCurrent(startPlayingFuture);
                            } catch (RuntimeException e1) {
                                break;
                            }
                            break;
                        }
                    }
                }
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            LOGGER.error("Playback error: {}", e.getMessage(), e);
            fullyRetryCurrent(startPlayingFuture);
        } finally {
            if (currentPlayingFuture != null) {
                currentPlayingFuture.complete(null);
            }
            LOGGER.debug("Play task finished");
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

    @SuppressWarnings("BusyWait")
    private void downloadAudioWithRetry(boolean forceSync, CompletableFuture<Void> downloadInitializedFuture) {
        CompletableFuture<?> currentPlayingFuture = playingFuture;
        CompletableFuture<?> currentDownloadFuture = downloadFuture;
        BlockingQueue<byte[]> localAudioBuffer = audioBuffer;

        int localRetryCount = 0;
        boolean forceSyncInternal = forceSync;

        MusicResourceInfo musicResourceInfo = MusicResourceInfo.NONE;
        while (!currentDownloadFuture.isDone() && currentDownloadFuture == downloadFuture) {
            int trial = localRetryCount + 1;
            try {
                if (musicResourceInfo == null || musicResourceInfo.equals(MusicResourceInfo.NONE) || localRetryCount % 3 == 0) {
                    musicResourceInfo = getCurrentMusicResourceInfo(clientConfig.getPrimaryChosenQuality(), musicResourceInfo).get();
                    if (musicResourceInfo == null) {
                        continue;
                    }
                }

                LOGGER.debug("Starting audio download (attempt {})", trial);

                AudioDecoder decoder = loadAudioDecoder(musicResourceInfo.getUrl(), musicResourceInfo.getType());
                currentDecoder = decoder;
                downloadInitializedFuture.complete(null);

                if (status.get() != Status.ERROR && status.get() != Status.RETRYING) {
                    setStatus(Status.BUFFERING);
                }

                if (forceSyncInternal) {
                    syncPlaying(currentDownloadFuture);
                }

                int initialBuffers = 0;
                while (!currentDownloadFuture.isDone() && currentDownloadFuture == downloadFuture && initialBuffers < BUFFER_COUNT * 2) {
                    byte[] audioData = decoder.readChunk(BUFFER_SIZE);
                    if (audioData == null) break;
                    if (currentDownloadFuture.isDone() || currentDownloadFuture != downloadFuture) break;

                    localAudioBuffer.put(audioData);
                    totalBufferedBytes.addAndGet(audioData.length);
                    initialBuffers++;
                }

                while (!currentDownloadFuture.isDone() && currentDownloadFuture == downloadFuture) {
                    // 保持解码器头与墙钟对齐（无论下载缓冲是否充足）
                    syncPlaying(currentDownloadFuture);

                    byte[] audioData = decoder.readChunk(BUFFER_SIZE);
                    if (audioData == null) break;

                    if (currentPlayingFuture.isDone() || currentDownloadFuture != downloadFuture) break;

                    localAudioBuffer.put(audioData);
                    playedBytes += audioData.length;
                    totalBufferedBytes.addAndGet(audioData.length);
                }

                // 下载完成
                LOGGER.debug("Audio download completed");
                currentDownloadFuture.complete(null);
                break;
            } catch (InterruptedException e) {
                LOGGER.debug("Download stopped by interruption");
                break;
            } catch (ArrayIndexOutOfBoundsException e) {
                LOGGER.debug("Download stopped by index out of bounds");
                break;
            } catch (Exception e) {
                String message = e.getMessage();
                if (e instanceof SocketException e1 && message.equals("Closed by interrupt")) break;
                LOGGER.error("Download error (attempt {})\n{} : {}", trial, e.getClass().getName(), message);
                if (e.getCause() instanceof TimeoutException || message.contains("Timeout") || message.contains("timeout")) {
                    message = I18n.get(MusicHud.MOD_ID + ".error.cause.timeout");
                }
                ToastUtil.show(
                        I18n.get(MusicHud.MOD_ID + ".error.downloadingAudioStream")
                                .replace("{trial}", String.valueOf(trial))
                                .replace("{message}", message)
                );

                localAudioBuffer.clear();
                totalBufferedBytes.set(0);
                playedBytes = 0;
                forceSyncInternal = true;
                localRetryCount++;
                setStatus(Status.RETRYING);

                try {
                    // 等待重试延迟
                    int retryDelayAdditionalMs = 1000;
                    int delay = localRetryCount * retryDelayAdditionalMs;
                    LOGGER.debug("Waiting {} ms before retry", delay);
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    LOGGER.debug("Download thread interrupted");
                    break;
                }
            }
        }

        LOGGER.debug("Download task finished");
    }

    private void syncPlaying(CompletableFuture<?> currentDownloadFuture) {
        if (serverStartTime == null || currentDecoder == null) return;
        int bytesPerSample = getBytesPerSample(currentDecoder.getFormat());
        int bytesPerSecond = currentDecoder.getSampleRate() * bytesPerSample;

        while (!currentDownloadFuture.isDone() && currentDownloadFuture == downloadFuture) {
            long millis = Duration.between(serverStartTime, ZonedDateTime.now()).toMillis();
            long skipBytes = millis * bytesPerSecond / 1000;
            // Compensate for initial buffer latency: when no audio has been
            // buffered for playback yet, the play thread still needs to fill
            // BUFFER_SIZE * BUFFER_COUNT bytes before alSourcePlay, causing a
            // delay between sync and actual audio output. Subtract half the
            // buffer to avoid audio lagging behind the wall clock.
            if (playedBytes == 0) {
                long bufferLatencyBytes = (long) BUFFER_SIZE * BUFFER_COUNT / 2;
                skipBytes = Math.max(0, skipBytes - bufferLatencyBytes);
            }
            if (playedBytes > skipBytes - bytesPerSample) {
                break;
            }
            byte[] chunk = currentDecoder.readChunk(Math.max(0, skipBytes - playedBytes));
            if (chunk == null) break;
            playedBytes += chunk.length;
        }
    }

    /**
     * The downloader may replace the decoder after a retry, and the new decoder
     * may produce a different format/sample rate. Queued buffers created by the
     * old decoder then sit in the source queue next to new-format data; the AL
     * mixer cannot continue past the format boundary, so the voice dies on it
     * and the refill deadlocks (processed stays 0). Detect the change and flush
     * the whole pipeline; the downloader's sync re-aligns the content.
     */
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
            // 用新格式的数据重新填充源队列（下载线程的 sync 会重新对齐内容位置）
            for (int i = 0; i < BUFFER_COUNT; i++) {
                byte[] audioData = audioBuffer.poll();
                if (audioData == null) break;

                ByteBuffer directBuffer = ByteBuffer.allocateDirect(audioData.length);
                directBuffer.put(audioData);
                directBuffer.flip();
                AL10.alBufferData(buffers[i], format, directBuffer, sampleRate);
                checkALError("alBufferData-Flush");
                AL10.alSourceQueueBuffers(source, buffers[i]);
                checkALError("alSourceQueueBuffers-Flush");
                fedBytes += audioData.length;
            }
            AL10.alSourcePlay(source);
            checkALError("alSourcePlay-Flush");
        }
    }

    private void updateVolumeIfNecessary() {
        float musicVolume = clientConfig.getMuted() ? 0 : (float) clientConfig.getSoundVolume() / 100 *
                (clientConfig.getMixWithVanillaSoundVolume() ? Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MUSIC) : 1);
        if (lastVolume != musicVolume && source != 0 && AL10.alIsSource(source)) {
            PlayingStatusRenderer.getInstance().updateStatus(null);
            AL10.alSourcef(source, AL10.AL_GAIN, musicVolume);
            int error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                LOGGER.warn("Failed to set source gain to {}: {} (source: {})", musicVolume, getALErrorString(error), source);
                if (error == AL10.AL_INVALID_NAME) {
                    LOGGER.error("Source {} is invalid, will be reinitialized on nextIdle play", source);
                    source = 0;
                    setStatus(Status.ERROR);
                } else {
                    setStatus(Status.ERROR);
                }
            } else {
                lastVolume = musicVolume;
            }
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

    @SneakyThrows
    public void stop() {
        stopInternal();
        setStatus(Status.IDLE);
    }

    private void stopInternal() {
        if (downloadThreadFuture != null) {
            downloadThreadFuture.cancel(true);
            downloadThreadFuture = null;
        }

        if (playThreadFuture != null) {
            playThreadFuture.cancel(true);
            playThreadFuture = null;
        }

        if (playingFuture != null) {
            playingFuture.cancel(true);
            playingFuture = null;
        }

        if (downloadFuture != null) {
            downloadFuture.cancel(true);
            downloadFuture = null;
        }

        lastVolume = 1;
        serverStartTime = null;
        cleanup();
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
            audioBuffer = new LinkedBlockingQueue<>(AUDIO_BUFFER_CAPACITY);
            totalBufferedBytes.set(0);
            playedBytes = 0;
            fedBytes = 0;
            lastDecoderFormat = -1;
            lastDecoderSampleRate = -1;
            serverStartTime = null;
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

    public CompletableFuture<MusicResourceInfo> getCurrentMusicResourceInfo(Quality quality, MusicResourceInfo previous) {
        String url = previous == null || previous.getUrl() == null ? "" : previous.getUrl();
        return RequestResponseManager.send(
                        new GetMusicResourceRequest(currentMusicDetail.getId(), quality, url),
                        GetMusicResourceResponse.class,
                        Duration.ofSeconds(10))
                .thenApply(GetMusicResourceResponse::getMusicResourceInfo)
                .thenCompose(value -> {
                    if (value == MusicResourceInfo.NONE) {
                        MusicService.getInstance().switchMusic(MusicDetail.NONE, MusicDetail.NONE, null, I18n.get(MusicHud.MOD_ID + ".text.failedToLoadMusicResource"));
                        setStatus(Status.ERROR);
                        return CompletableFuture.failedFuture(new RuntimeException("Failed to load music resource"));
                    }
                    return CompletableFuture.completedFuture(value);
                });
    }

    public enum Status {
        IDLE, BUFFERING, PLAYING, RETRYING, ERROR
    }
}
