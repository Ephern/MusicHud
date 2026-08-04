package indi.etern.musichud.client.audio;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.FormatType;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.MusicResourceInfo;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.client.audio.decoder.*;
import indi.etern.musichud.client.services.music.MusicService;
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
import org.lwjgl.openal.EXTOffset;
import org.lwjgl.openal.SOFTBufferSamples;
import org.lwjgl.openal.SOFTDirectChannels;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class StreamAudioPlayer {
    private static final int BUFFER_COUNT = 4;
    private static final int BUFFER_SIZE = 65536;
    private static final long RESYNC_THRESHOLD_MS = 150;
    private static final long RESYNC_COOLDOWN_MS = 1500;
    private static final long STALL_DETECT_MS = 300;
    private static final boolean DEBUG_VERIFY_POSITION = true;
    private int debugPositionCounter = 0;
    private long debugLastNanos = 0;
    private long debugLastOffsetSamples = -1;
    private static final Logger LOGGER = MusicHud.getLogger(StreamAudioPlayer.class);
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static volatile StreamAudioPlayer instance = null;
    private final int[] buffers = new int[BUFFER_COUNT];
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicReference<Status> status = new AtomicReference<>(Status.IDLE);
    @Getter
    private final Set<Consumer<Status>> statusChangeListener = new HashSet<>();
    private final AtomicLong totalBufferedBytes = new AtomicLong(0);
    private final ArrayDeque<Integer> queuedSizes = new ArrayDeque<>();
    private final ArrayDeque<byte[]> queuedData = new ArrayDeque<>();
    private final ArrayDeque<Integer> queuedBufferIds = new ArrayDeque<>();
    private volatile BlockingQueue<byte[]> audioBuffer = new LinkedBlockingQueue<>(30); // 最大30个数据块的缓冲区
    private int source = 0;
    private float lastVolume;
    private volatile CompletableFuture<?> playingFuture;
    private volatile CompletableFuture<?> downloadFuture;
    private MusicDetail currentMusicDetail;
    private AudioDecoder currentDecoder;
    private long playedBytes = 0;
    private long fedBytes = 0;
    private long queuedBytes = 0;
    private long lastRepairAt = 0;
    private boolean pendingRestart = false;
    private int lastDecoderFormat = -1;
    private int lastDecoderSampleRate = -1;
    private long watchdogLastPosition = -1;
    private long watchdogLastTime = 0;
    private volatile ZonedDateTime serverStartTime;
    private Future<?> downloadThreadFuture;
    private Future<?> playThreadFuture;

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
            Thread.sleep(1000);
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
                Thread.sleep(500);
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
                Thread.sleep(50);
            }

            if (totalBufferedBytes.get() == 0) {
                LOGGER.error("No audio data available");
                if (currentPlayingFuture != null && playingFuture != null) {
                    setStatus(Status.ERROR);
                }
                fullyRetryCurrent(startPlayingFuture);
            } else {
//                synchronized (StreamAudioPlayer.class) {
                if (!initialized.get() || source == 0) {
                    startPlayingFuture.completeExceptionally(new IllegalStateException("Audio player not initialized"));
                    finished = true;
                } else {// 从缓冲区填充初始数据
                    for (int i = 0; i < BUFFER_COUNT; i++) {
                        byte[] audioData = playBuffer.poll(0, TimeUnit.SECONDS);
                        if (audioData == null) break;

                        ByteBuffer directBuffer = ByteBuffer.allocateDirect(audioData.length);
                        directBuffer.put(audioData);
                        directBuffer.flip();

                        int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
                        int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;

                        AL10.alBufferData(buffers[i], format, directBuffer, sampleRate);
                        checkALError("alBufferData-Pre");
                        AL10.alSourceQueueBuffers(source, buffers[i]);
                        checkALError("alSourceQueueBuffers-Pre");

                        queuedSizes.add(audioData.length);
                        queuedData.add(audioData);
                        queuedBufferIds.add(buffers[i]);
                        fedBytes += audioData.length;
                        queuedBytes += audioData.length;

                        totalBufferedBytes.addAndGet(-audioData.length);
                    }
                    if (clientConfig.getDisableVanillaMusic())
                        Minecraft.getInstance().getSoundManager().stop(null, SoundSource.MUSIC);
                    setStatus(Status.PLAYING);
                    AL10.alSourcePlay(source);
                    checkALError("alSourcePlay-Pre");
                }

                if (!finished) {
                    this.serverStartTime = Objects.requireNonNullElseGet(serverStartTime, ZonedDateTime::now);
                    while (currentPlayingFuture != null && !currentPlayingFuture.isDone() && currentPlayingFuture == playingFuture) {
                        try {
                            updateVolumeIfNecessary();
                            if (!initialized.get() || source == 0) break;

                            checkDecoderChangeAndFlush();

//                            debugVerifyPosition();

                            boolean repaired = checkAndRepairLag(playBuffer);

                                int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
                                //noinspection SpellCheckingInspection
                                checkALError("alGetSourcei-Processed");

                                startPlayingFuture.complete(serverStartTime == null ? ZonedDateTime.now() : serverStartTime);

                                boolean refilled = false;
                                if (!repaired) {
                                    while (processed-- > 0) {
                                        int[] buffer = new int[1];
                                        AL10.alSourceUnqueueBuffers(source, buffer);
                                        //noinspection SpellCheckingInspection
                                        checkALError("alSourceUnqueueBuffers-Main");

                                        queuedBytes -= queuedSizes.poll();
                                        queuedData.poll();
                                        queuedBufferIds.poll();

                                        byte[] audioData = playBuffer.poll(0, TimeUnit.MILLISECONDS);

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

                                        queuedSizes.add(audioData.length);
                                        queuedData.add(audioData);
                                        queuedBufferIds.add(buffer[0]);
                                        fedBytes += audioData.length;
                                        queuedBytes += audioData.length;
                                        refilled = true;

                                        if (audioData.length == BUFFER_SIZE) { // 不是静音数据
                                            totalBufferedBytes.addAndGet(-audioData.length);
                                        }
                                    }
                                }

                                // Top up the queue to BUFFER_COUNT when data is
                                // available, so the source does not underrun and
                                // restart at every buffer boundary (after a
                                // repair/recycle the queue depth may be low).
                                int queuedNow = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
                                while (queuedNow < BUFFER_COUNT) {
                                    byte[] audioData = playBuffer.poll(0, TimeUnit.MILLISECONDS);
                                    if (audioData == null) break;
                                    int freeId = 0;
                                    for (int id : buffers) {
                                        if (!queuedBufferIds.contains(id)) {
                                            freeId = id;
                                            break;
                                        }
                                    }
                                    if (freeId == 0) break;

                                    ByteBuffer directBuffer = ByteBuffer.allocateDirect(audioData.length);
                                    directBuffer.put(audioData);
                                    directBuffer.flip();

                                    int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
                                    int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;

                                    AL10.alBufferData(freeId, format, directBuffer, sampleRate);
                                    checkALError("alBufferData-TopUp");
                                    AL10.alSourceQueueBuffers(source, freeId);
                                    checkALError("alSourceQueueBuffers-TopUp");

                                    queuedSizes.add(audioData.length);
                                    queuedData.add(audioData);
                                    queuedBufferIds.add(freeId);
                                    fedBytes += audioData.length;
                                    queuedBytes += audioData.length;
                                    refilled = true;
                                    queuedNow++;
                                }

                                if (repaired) {
                                    // Defer the restart after a repair: alSourceStop is processed
                                    // asynchronously by the mixer, and an immediate alSourcePlay
                                    // could get its voice killed by the pending stop, leaving the
                                    // source stuck on an unfinished buffer.
                                    pendingRestart = true;
                                } else if (pendingRestart) {
                                    // The async alSourceStop from a previous repair/recycle has
                                    // settled by now; the freshly queued buffers can be played.
                                    pendingRestart = false;
                                    AL10.alSourcePlay(source);
                                    checkALError("alSourcePlay-Main");
                                    watchdogLastPosition = -1;
                                } else if (refilled) {
                                    // The regular refill just queued data and no stop is pending;
                                    // restart right away.
                                    int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
                                    if (state != AL10.AL_PLAYING) {
                                        AL10.alSourcePlay(source);
                                        checkALError("alSourcePlay-Main");
                                    }
                                    watchdogLastPosition = -1;
                                } else if (!currentPlayingFuture.isDone() && currentPlayingFuture == playingFuture) {
                                    // Position watchdog: AL_SOURCE_STATE can be stale (a voice may
                                    // keep playing while the state reads STOPPED, left over from an
                                    // earlier alSourceStop), so the source must NOT be stopped or
                                    // recycled while the playback position is advancing. Only when
                                    // the position is genuinely frozen do we recycle the source.
                                    int bytesPerSample = getBytesPerSample(currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16);
                                    int sampleOffset = AL10.alGetSourcei(source, EXTOffset.AL_SAMPLE_OFFSET);
                                    long positionNow = fedBytes - queuedBytes + (long) sampleOffset * bytesPerSample;
                                    long now = System.currentTimeMillis();
                                    if (positionNow != watchdogLastPosition) {
                                        watchdogLastPosition = positionNow;
                                        watchdogLastTime = now;
                                    } else if (now - watchdogLastTime >= STALL_DETECT_MS) {
                                        recycleSource(playBuffer);
                                        watchdogLastPosition = positionNow;
                                        watchdogLastTime = now;
                                    }
                                }
                            Thread.sleep(40);
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

    @SuppressWarnings("BusyWait")
    private void downloadAudioWithRetry(boolean forceSync, CompletableFuture<Void> downloadInitializedFuture) {
        CompletableFuture<?> currentPlayingFuture = playingFuture;
        CompletableFuture<?> currentDownloadFuture = downloadFuture;
        BlockingQueue<byte[]> localAudioBuffer = audioBuffer;

        int localRetryCount = 0;
        boolean forceSyncInternal = forceSync;

        MusicResourceInfo musicResourceInfo = MusicResourceInfo.NONE;
        while (!currentDownloadFuture.isDone() && currentDownloadFuture == downloadFuture) {
            try {
                if (musicResourceInfo == null || musicResourceInfo.equals(MusicResourceInfo.NONE) || localRetryCount % 3 == 0) {
                    musicResourceInfo = getCurrentMusicResourceInfo(clientConfig.getPrimaryChosenQuality(), musicResourceInfo).get();
                    if (musicResourceInfo == null) {
                        continue;
                    }
                }

                LOGGER.debug("Starting audio download (attempt {})", localRetryCount + 1);

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
                    // Keep the decoder head aligned with the wall clock at all
                    // times, not only when the download buffer runs low (which
                    // may never happen while the player keeps up).
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
                if (e instanceof SocketException e1 && e1.getMessage().equals("Closed by interrupt")) break;
                LOGGER.error("Download error (attempt {})\n{} : {}", localRetryCount + 1, e.getClass().getSimpleName(), e.getMessage());

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
            queuedSizes.clear();
            queuedData.clear();
            queuedBufferIds.clear();
            fedBytes = 0;
            queuedBytes = 0;
            audioBuffer.clear();
            totalBufferedBytes.set(0);
        }
    }

    /**
     * The playback position has been frozen for a while, so the voice is
     * genuinely dead. Unqueue everything (after a stop so even unprocessed
     * buffers can be reclaimed), refill from the download buffer and defer the
     * restart until the async stop has settled.
     */
    private void recycleSource(BlockingQueue<byte[]> playBuffer) throws InterruptedException {
        LOGGER.warn("Playback position frozen, recycling source");
        AL10.alSourceStop(source);
        checkALError("alSourceStop-Recycle");
        int queuedCount = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
        checkALError("alGetSourcei-Queued-Recycle");
        for (int i = 0; i < queuedCount; i++) {
            int[] buffer = new int[1];
            AL10.alSourceUnqueueBuffers(source, buffer);
            checkALError("alSourceUnqueueBuffers-Recycle");
            queuedBytes -= queuedSizes.poll();
            queuedData.poll();
            queuedBufferIds.poll();
        }

        int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
        int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;
        for (int i = 0; i < BUFFER_COUNT; i++) {
            byte[] audioData = playBuffer.poll(0, TimeUnit.MILLISECONDS);
            if (audioData == null) break;

            ByteBuffer directBuffer = ByteBuffer.allocateDirect(audioData.length);
            directBuffer.put(audioData);
            directBuffer.flip();
            AL10.alBufferData(buffers[i], format, directBuffer, sampleRate);
            checkALError("alBufferData-Recycle");
            AL10.alSourceQueueBuffers(source, buffers[i]);
            checkALError("alSourceQueueBuffers-Recycle");
            queuedSizes.add(audioData.length);
            queuedData.add(audioData);
            queuedBufferIds.add(buffers[i]);
            fedBytes += audioData.length;
            queuedBytes += audioData.length;
        }
        if (AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED) == 0) {
            // Nothing to play yet; queue a silence filler so the deferred
            // restart has something to play.
            byte[] silence = new byte[BUFFER_SIZE];
            ByteBuffer directBuffer = ByteBuffer.allocateDirect(silence.length);
            directBuffer.put(silence);
            directBuffer.flip();
            AL10.alBufferData(buffers[0], format, directBuffer, sampleRate);
            checkALError("alBufferData-Recycle-Silence");
            AL10.alSourceQueueBuffers(source, buffers[0]);
            checkALError("alSourceQueueBuffers-Recycle-Silence");
            queuedSizes.add(silence.length);
            queuedData.add(silence);
            queuedBufferIds.add(buffers[0]);
            fedBytes += silence.length;
            queuedBytes += silence.length;
        }
        pendingRestart = true;
    }

    /**
     * DEBUG ONLY: verifies that the playback position can be computed from AL
     * queries. Logs raw AL values plus the bookkeeping-based interpretation
     * every ~500ms. Key verdict: while PLAYING, AL_SAMPLE_OFFSET must advance
     * at about the decoder sample rate (rate/sampleRate ≈ 1). If it does, AL
     * position tracking is feasible and any remaining discrepancy comes from
     * the fedBytes/queuedBytes bookkeeping.
     */
    private void debugVerifyPosition() {
        if (!DEBUG_VERIFY_POSITION || currentDecoder == null) return;
        if (++debugPositionCounter % 12 != 0) return;

        int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
        int sampleOffset = AL10.alGetSourcei(source, EXTOffset.AL_SAMPLE_OFFSET);
        int queueLenSamples = AL10.alGetSourcei(source, SOFTBufferSamples.AL_SAMPLE_LENGTH_SOFT);
        int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);

        long now = System.nanoTime();
        double rateHz = Double.NaN;
        if (debugLastOffsetSamples >= 0 && state == AL10.AL_PLAYING) {
            double dtSec = (now - debugLastNanos) / 1e9;
            if (dtSec > 0.01) {
                rateHz = (sampleOffset - debugLastOffsetSamples) / dtSec;
            }
        }
        debugLastOffsetSamples = sampleOffset;
        debugLastNanos = now;

        int bytesPerSample = getBytesPerSample(currentDecoder.getFormat());
        long bytesPerSecond = (long) currentDecoder.getSampleRate() * bytesPerSample;
        long offsetBytes = (long) sampleOffset * bytesPerSample;
        long queueStartBytes = fedBytes - queuedBytes;   // bookkeeping anchor: content position of the queue head
        long actualF1 = queueStartBytes + offsetBytes;   // bookkeeping-based actual
        long expectedBytes = serverStartTime == null ? 0
                : Duration.between(serverStartTime, ZonedDateTime.now()).toMillis() * bytesPerSecond / 1000;
        long expectedInQueueSamples = (expectedBytes - queueStartBytes) / bytesPerSample;
        long offsetErrorSamples = sampleOffset - expectedInQueueSamples;
        long expectedRateHz = currentDecoder.getSampleRate();

        String rateStr = Double.isNaN(rateHz) ? "n/a" : String.format("%.0f", rateHz);
        String ratioStr = "n/a";
        if (!Double.isNaN(rateHz)) {
            double ratio = rateHz / expectedRateHz;
            ratioStr = String.format("%.3f (%s)", ratio, ratio > 0.9 && ratio < 1.1 ? "OK" : (ratio < 0.9 ? "STALLED" : "FAST"));
        }
        LOGGER.info("POSDBG state={} offset={}smp({}ms) queueLen={}smp queued={} processed={} rate={}Hz ratio={} | " +
                        "queueStart={}ms expectedInQueue={}smp offsetError={}smp | fed={} queuedBytes={} actualF1={}ms expected={}ms | " +
                        "decoder={}/{}/{}Hz bufSize={} downloadDone={} status={}",
                state, sampleOffset, offsetBytes * 1000L / bytesPerSecond,
                queueLenSamples, queued, processed, rateStr, ratioStr,
                queueStartBytes * 1000L / bytesPerSecond, expectedInQueueSamples, offsetErrorSamples,
                fedBytes, queuedBytes, actualF1 * 1000L / bytesPerSecond, expectedBytes * 1000L / bytesPerSecond,
                currentDecoder.getFormat(), currentDecoder.getSampleRate(), expectedRateHz,
                audioBuffer.size(), downloadFuture != null && downloadFuture.isDone(), status.get());
    }

    /**
     * Detects playback falling behind the expected (wall clock) progress and
     * repairs it in place by discarding the stale audio that has already been
     * queued, so that playback resumes at the expected position.
     *
     * @return true if a repair was performed
     */
    private boolean checkAndRepairLag(BlockingQueue<byte[]> playBuffer) {
        ZonedDateTime startTime = serverStartTime;
        if (startTime == null || currentDecoder == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastRepairAt < RESYNC_COOLDOWN_MS) return false;
        long millis = Duration.between(startTime, ZonedDateTime.now()).toMillis();
        if (millis <= 0) return false;

        int bytesPerSample = getBytesPerSample(currentDecoder.getFormat());
        int bytesPerSecond = currentDecoder.getSampleRate() * bytesPerSample;
        long expectedBytes = millis * bytesPerSecond / 1000;

        int sampleOffset = AL10.alGetSourcei(source, EXTOffset.AL_SAMPLE_OFFSET);
        checkALError("alGetSourcei-SampleOffset");
        long offsetBytes = (long) sampleOffset * bytesPerSample;
        long actualBytes = fedBytes - queuedBytes + offsetBytes;
        long lagBytes = expectedBytes - actualBytes;

        if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) return false;
        long positionError = expectedBytes - actualBytes;
        long thresholdBytes = RESYNC_THRESHOLD_MS * (long) bytesPerSecond / 1000L;
        if (positionError > 0) {
            // Behind: only skip stale data when the pipeline already contains
            // data at or after the wall clock position. If the downloader is
            // still behind (fedBytes < expected), a skip would just drain
            // everything the downloader has produced; its own sync will align
            // the content once the data flows.
            if (expectedBytes > fedBytes) return false;
            if (positionError <= thresholdBytes) return false;
            LOGGER.info("Audio playback lagged behind expected progress by {} ms, resyncing" +
                            " [state={} queued={} processed={} fed={} queuedBytes={} offsetBytes={} actual={}ms expected={}ms]",
                    positionError * 1000L / bytesPerSecond,
                    AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE),
                    AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED),
                    AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED),
                    fedBytes, queuedBytes, offsetBytes,
                    actualBytes * 1000L / bytesPerSecond, expectedBytes * 1000L / bytesPerSecond);
            repairLag(positionError, actualBytes, offsetBytes, bytesPerSecond, playBuffer);
        } else {
            // Ahead: the content is running ahead of the wall clock. Insert a
            // delay silence in front of the queued content so it starts
            // exactly at the expected position.
            if (-positionError <= thresholdBytes) return false;
            LOGGER.info("Audio playback ahead of expected progress by {} ms, inserting delay silence",
                    -positionError * 1000L / bytesPerSecond);
            delayPlayback(-positionError);
        }
        return true;
    }

    /**
     * Drops the stale audio between the actual playback position and the
     * expected position, then restarts the source at the expected position.
     * Buffers already queued in OpenAL are trimmed precisely; chunks still in
     * the download buffer are discarded at chunk granularity (overshoot of at
     * most one chunk self-corrects as the wall clock catches up).
     */
    private void repairLag(long lagBytes, long actualBytes, long offsetBytes, int bytesPerSecond, BlockingQueue<byte[]> playBuffer) {
        lastRepairAt = System.currentTimeMillis();
        long targetBytes = actualBytes + lagBytes;

        AL10.alSourceStop(source);
        checkALError("alSourceStop-Repair");
        int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
        checkALError("alGetSourcei-Queued-Repair");

        List<Integer> keepBufferIds = new ArrayList<>();
        List<byte[]> keepData = new ArrayList<>();
        long cursor = actualBytes - offsetBytes;
        int bytesPerSample = getBytesPerSample(currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16);
        for (int i = 0; i < queued; i++) {
            int[] buffer = new int[1];
            AL10.alSourceUnqueueBuffers(source, buffer);
            checkALError("alSourceUnqueueBuffers-Repair");
            int size = queuedSizes.poll();
            byte[] data = queuedData.poll();
            queuedBufferIds.poll();
            long end = cursor + size;
            if (end > targetBytes) {
                // Sample-align the trim so the PCM stream keeps its frame
                // boundaries; cutting mid-sample would corrupt the audio.
                int from = (int) Math.max(0, targetBytes - cursor);
                from = (int) ((long) from / bytesPerSample * bytesPerSample);
                from = Math.min(from, size);
                byte[] kept = from == 0 ? data : Arrays.copyOfRange(data, from, size);
                keepBufferIds.add(buffer[0]);
                keepData.add(kept);
            }
            cursor = end;
        }

        // Discard stale chunks still sitting in the download buffer
        int drainedChunks = 0;
        while (cursor < targetBytes) {
            byte[] chunk = playBuffer.poll();
            if (chunk == null) break;
            totalBufferedBytes.addAndGet(-chunk.length);
            cursor += chunk.length;
            drainedChunks++;
        }

        int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
        int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;
        queuedSizes.clear();
        queuedData.clear();
        queuedBufferIds.clear();
        long keptBytes = 0;
        for (int i = 0; i < keepBufferIds.size(); i++) {
            byte[] data = keepData.get(i);
            ByteBuffer directBuffer = ByteBuffer.allocateDirect(data.length);
            directBuffer.put(data);
            directBuffer.flip();
            AL10.alBufferData(keepBufferIds.get(i), format, directBuffer, sampleRate);
            checkALError("alBufferData-Repair");
            AL10.alSourceQueueBuffers(source, keepBufferIds.get(i));
            checkALError("alSourceQueueBuffers-Repair");
            queuedSizes.add(data.length);
            queuedData.add(data);
            queuedBufferIds.add(keepBufferIds.get(i));
            keptBytes += data.length;
        }
        if (keepBufferIds.isEmpty()) {
            // Nothing playable left yet; queue a silence filler so the source
            // can restart. Its length is the drain overshoot (cursor - target),
            // so the following content starts exactly at the expected position
            // instead of jumping ahead of the wall clock.
            long silenceBytes = Math.max(0, cursor - targetBytes);
            silenceBytes = silenceBytes / bytesPerSample * bytesPerSample;
            int silenceLength = (int) Math.max(bytesPerSample, silenceBytes);
            byte[] silence = new byte[silenceLength];
            ByteBuffer directBuffer = ByteBuffer.allocateDirect(silence.length);
            directBuffer.put(silence);
            directBuffer.flip();
            AL10.alBufferData(buffers[0], format, directBuffer, sampleRate);
            checkALError("alBufferData-Repair-Silence");
            AL10.alSourceQueueBuffers(source, buffers[0]);
            checkALError("alSourceQueueBuffers-Repair-Silence");
            queuedSizes.add(silence.length);
            queuedData.add(silence);
            queuedBufferIds.add(buffers[0]);
            keptBytes = silence.length;
        }
        fedBytes = keptBytes;
        queuedBytes = keptBytes;
        LOGGER.info("Audio lag repaired, skipped {} ms of stale audio" +
                        " [droppedBuffers={} kept={} drainedChunks={} silence={} postQueued={} postState={}]",
                lagBytes * 1000L / bytesPerSecond,
                queued - keepBufferIds.size(), keepBufferIds.size(), drainedChunks, keepBufferIds.isEmpty(),
                AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED),
                AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE));
    }

    /**
     * The playback content is running ahead of the wall clock. Stop the source,
     * prepend a silence of exactly the excess duration to the first queued
     * buffer, re-queue everything and defer the restart, so the content starts
     * exactly at the expected position.
     */
    private void delayPlayback(long delayBytes) {
        lastRepairAt = System.currentTimeMillis();
        int bytesPerSample = getBytesPerSample(currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16);
        long delayAligned = delayBytes / bytesPerSample * bytesPerSample;
        int silenceLength = (int) Math.max(bytesPerSample, delayAligned);

        AL10.alSourceStop(source);
        checkALError("alSourceStop-Delay");
        int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
        checkALError("alGetSourcei-Queued-Delay");

        int[] collectedIds = new int[queued];
        byte[][] collectedData = new byte[queued][];
        for (int i = 0; i < queued; i++) {
            int[] buffer = new int[1];
            AL10.alSourceUnqueueBuffers(source, buffer);
            checkALError("alSourceUnqueueBuffers-Delay");
            collectedIds[i] = buffer[0];
            collectedData[i] = queuedData.poll();
            queuedSizes.poll();
            queuedBufferIds.poll();
        }

        int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
        int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;
        queuedSizes.clear();
        queuedData.clear();
        queuedBufferIds.clear();
        long keptBytes = 0;
        if (queued == 0) {
            // Defensive: nothing to delay; queue the silence alone.
            byte[] silence = new byte[silenceLength];
            ByteBuffer directBuffer = ByteBuffer.allocateDirect(silence.length);
            directBuffer.put(silence);
            directBuffer.flip();
            AL10.alBufferData(buffers[0], format, directBuffer, sampleRate);
            checkALError("alBufferData-Delay-Silence");
            AL10.alSourceQueueBuffers(source, buffers[0]);
            checkALError("alSourceQueueBuffers-Delay-Silence");
            queuedSizes.add(silence.length);
            queuedData.add(silence);
            queuedBufferIds.add(buffers[0]);
            keptBytes = silence.length;
        }
        for (int i = 0; i < collectedIds.length; i++) {
            byte[] data = collectedData[i];
            if (i == 0) {
                // Prepend the delay silence to the first buffer so no extra
                // buffer id is needed; the content then starts exactly at the
                // expected position.
                byte[] delayed = new byte[silenceLength + data.length];
                System.arraycopy(data, 0, delayed, silenceLength, data.length);
                data = delayed;
            }
            ByteBuffer directBuffer = ByteBuffer.allocateDirect(data.length);
            directBuffer.put(data);
            directBuffer.flip();
            AL10.alBufferData(collectedIds[i], format, directBuffer, sampleRate);
            checkALError("alBufferData-Delay");
            AL10.alSourceQueueBuffers(source, collectedIds[i]);
            checkALError("alSourceQueueBuffers-Delay");
            queuedSizes.add(data.length);
            queuedData.add(data);
            queuedBufferIds.add(collectedIds[i]);
            keptBytes += data.length;
        }
        fedBytes = keptBytes;
        queuedBytes = keptBytes;
        pendingRestart = true;
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
            audioBuffer = new LinkedBlockingQueue<>(30);
            totalBufferedBytes.set(0);
            playedBytes = 0;
            fedBytes = 0;
            queuedBytes = 0;
                queuedSizes.clear();
                queuedData.clear();
                queuedBufferIds.clear();
                lastRepairAt = 0;
                pendingRestart = false;
                lastDecoderFormat = -1;
                lastDecoderSampleRate = -1;
                watchdogLastPosition = -1;
                watchdogLastTime = 0;
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
