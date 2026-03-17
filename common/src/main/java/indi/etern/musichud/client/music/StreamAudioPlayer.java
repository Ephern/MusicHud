package indi.etern.musichud.client.music;

import dev.architectury.networking.NetworkManager;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.FormatType;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.MusicResourceInfo;
import indi.etern.musichud.beans.music.Quality;
import indi.etern.musichud.client.config.ClientConfigDefinition;
import indi.etern.musichud.client.music.decoder.AudioDecoder;
import indi.etern.musichud.client.music.decoder.AudioFormatDetector;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.network.requestResponseCycle.GetMusicResourceRequest;
import indi.etern.musichud.network.requestResponseCycle.GetMusicResourceResponse;
import lombok.Getter;
import lombok.SneakyThrows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundSource;
import org.apache.logging.log4j.Logger;
import org.lwjgl.openal.AL10;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class StreamAudioPlayer {
    private static final int BUFFER_COUNT = 4;
    private static final int BUFFER_SIZE = 65536;
    private static final Logger LOGGER = MusicHud.getLogger(StreamAudioPlayer.class);
    private static volatile StreamAudioPlayer instance = null;

    private final int[] buffers = new int[BUFFER_COUNT];
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicReference<Status> status = new AtomicReference<>(Status.IDLE);
    private final BlockingQueue<byte[]> audioBuffer = new LinkedBlockingQueue<>(30); // 最大30个数据块的缓冲区

    @Getter
    private final Set<Consumer<Status>> statusChangeListener = new HashSet<>();
    private final AtomicLong totalBufferedBytes = new AtomicLong(0);
    ZonedDateTime currentStartTime;
    private int source = 0;
    private float lastVolume;
    private Future<?> playingFuture;
    private Future<?> downloadFuture;
    private volatile boolean shouldContinuePlaying = false;
    private volatile boolean shouldContinueDownloading = false;
    private volatile AudioDecoder currentDecoder;
    private volatile long bytesPerSecond = 0;
    private volatile boolean isBuffering = false;
    private volatile ZonedDateTime serverStartTime;
    private MusicDetail currentMusicDetail;

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

    private static AudioDecoder loadAudioDecoder(String urlString, FormatType formatType) throws URISyntaxException, IOException {
        URL url = new URI(urlString).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        InputStream inputStream = connection.getInputStream();
        BufferedInputStream bufferedStream = new BufferedInputStream(inputStream, 8192);

        if (formatType != FormatType.AUTO) {
            FormatType detectedFormatType = AudioFormatDetector.detectFormat(bufferedStream);
            if (detectedFormatType != formatType) {
                LOGGER.warn("Detected format type is not equals to resource format type, using detected");
            }

            return detectedFormatType.newDecoder(bufferedStream);
        } else {
            return formatType.newDecoder(bufferedStream);
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

    protected void fullyRetryCurrent() {
        cleanup();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        } finally {
            LOGGER.info("Fully retrying");
            playAsync(currentMusicDetail, currentStartTime);
        }
    }

    public CompletableFuture<ZonedDateTime> playAsync(MusicDetail musicDetail, ZonedDateTime startTime) {
        synchronized (StreamAudioPlayer.class) {
            try {
                currentMusicDetail = musicDetail;
                currentStartTime = startTime == null ? ZonedDateTime.now() : startTime;
                stop(); // 先停止之前的播放

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
                checkALError("source configuration");
                lastVolume = 1;

                initialized.set(true);
            } catch (Exception e) {
                cleanup();
                return CompletableFuture.failedFuture(e);
            }

            // 清空缓冲区
            audioBuffer.clear();
            totalBufferedBytes.set(0);
            shouldContinuePlaying = true;
            shouldContinueDownloading = true;

            CompletableFuture<Void> downloadInitializedFuture = new CompletableFuture<>();
            CompletableFuture<ZonedDateTime> startPlayingFuture = new CompletableFuture<>();
            downloadFuture = MusicHud.EXECUTOR.submit(() -> {
                Thread.currentThread().setName("Downloader");
                try {
                    downloadAudioWithRetry(startTime != null, downloadInitializedFuture);
                } catch (Exception e) {
                    LOGGER.error("Download thread error", e);
                    setStatus(Status.ERROR);
                    try {
                        fullyRetryCurrent();
                    } catch (RuntimeException e1) {
                        LOGGER.error("Retry failed: {}: {}", e1.getClass(), e1.getMessage());
                    }
                }
            });
            downloadInitializedFuture.thenAccept(ignore -> {
                serverStartTime = startTime == null ? ZonedDateTime.now() : startTime;
                playingFuture = MusicHud.EXECUTOR.submit(() -> {
                    Thread.currentThread().setName("Music Player");
                    try {
                        playAudioWithRetry(startPlayingFuture);
                    } catch (Exception e) {
                        LOGGER.error("Play thread error", e);
                        if (!startPlayingFuture.isDone()) {
                            startPlayingFuture.completeExceptionally(e);
                        }
                    }
                });
            });

            return startPlayingFuture;
        }
    }

    @SuppressWarnings("BusyWait")
    private void playAudioWithRetry(CompletableFuture<ZonedDateTime> startPlayingFuture) {
        boolean finished = false;
        try {
            // 等待一些数据缓冲
            while (shouldContinuePlaying && totalBufferedBytes.get() < BUFFER_SIZE * BUFFER_COUNT) {
                Thread.sleep(50);
            }

            if (totalBufferedBytes.get() == 0) {
                LOGGER.error("No audio data available");
                setStatus(Status.ERROR);
                startPlayingFuture.completeExceptionally(new IOException("No audio data available"));
            } else {
                synchronized (StreamAudioPlayer.class) {
                    if (!initialized.get() || source == 0) {
                        startPlayingFuture.completeExceptionally(new IllegalStateException("Audio player not initialized"));
                        finished = true;
                    } else {// 从缓冲区填充初始数据
                        for (int i = 0; i < BUFFER_COUNT; i++) {
                            byte[] audioData = audioBuffer.poll(1, TimeUnit.SECONDS);
                            if (audioData == null) break;

                            ByteBuffer directBuffer = ByteBuffer.allocateDirect(audioData.length);
                            directBuffer.put(audioData);
                            directBuffer.flip();

                            int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
                            int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;

                            AL10.alBufferData(buffers[i], format, directBuffer, sampleRate);
                            checkALError("alBufferData");
                            AL10.alSourceQueueBuffers(source, buffers[i]);
                            checkALError("alSourceQueueBuffers");

                            totalBufferedBytes.addAndGet(-audioData.length);
                        }
                        if (ClientConfigDefinition.disableVanillaMusic.get())
                            Minecraft.getInstance().getSoundManager().stop(null, SoundSource.MUSIC);
                        startPlayingFuture.complete(serverStartTime);
                        setStatus(Status.PLAYING);
                        AL10.alSourcePlay(source);
                        checkALError("alSourcePlay");
                    }

                }
                if (!finished) {// 主播放循环
                    while (shouldContinuePlaying) {
                        try {
                            synchronized (StreamAudioPlayer.class) {
                                updateVolumeIfNecessary();
                                if (!initialized.get() || source == 0) break;

                                int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
                                //noinspection SpellCheckingInspection
                                checkALError("alGetSourcei");

                                while (processed-- > 0) {
                                    int[] buffer = new int[1];
                                    AL10.alSourceUnqueueBuffers(source, buffer);
                                    //noinspection SpellCheckingInspection
                                    checkALError("alSourceUnqueueBuffers");

                                    // 从缓冲区获取音频数据，最多等待500ms
                                    byte[] audioData = audioBuffer.poll(500, TimeUnit.MILLISECONDS);

                                    if (audioData == null) {
                                        // 没有数据可用
                                        if (audioBuffer.isEmpty() && NowPlayingInfo.getInstance().isCompleted()) {
                                            // 播放已完成且缓冲区为空，结束播放
                                            LOGGER.debug("No more audio data available");
                                            shouldContinuePlaying = false;
                                            isBuffering = false;
                                            setStatus(Status.PLAYING);
                                            break;
                                        } else if (shouldContinueDownloading) {
                                            audioData = new byte[BUFFER_SIZE];
                                            isBuffering = true;
                                            if (status.get() != Status.ERROR && status.get() != Status.RETRYING) {
                                                setStatus(Status.BUFFERING);
                                            }
                                        } else {
                                            audioData = new byte[BUFFER_SIZE];
                                            isBuffering = false;
                                        }
                                    } else {
                                        isBuffering = false;
                                        setStatus(Status.PLAYING);
                                    }

                                    ByteBuffer directBuffer = ByteBuffer.allocateDirect(audioData.length);
                                    directBuffer.put(audioData);
                                    directBuffer.flip();

                                    int format = currentDecoder != null ? currentDecoder.getFormat() : AL10.AL_FORMAT_STEREO16;
                                    int sampleRate = currentDecoder != null ? currentDecoder.getSampleRate() : 44100;

                                    AL10.alBufferData(buffer[0], format, directBuffer, sampleRate);
                                    checkALError("alBufferData");
                                    AL10.alSourceQueueBuffers(source, buffer[0]);
                                    checkALError("alSourceQueueBuffers");

                                    if (audioData.length == BUFFER_SIZE) { // 不是静音数据
                                        totalBufferedBytes.addAndGet(-audioData.length);
                                    }
                                }

                                int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
                                //noinspection SpellCheckingInspection
                                checkALError("alGetSourcei");
                                if (state != AL10.AL_PLAYING && shouldContinuePlaying) {
                                    AL10.alSourcePlay(source);
                                    checkALError("alSourcePlay");
                                }
                            }

                            // 检查缓冲状态
                            if (status.get() != Status.RETRYING && status.get() != Status.ERROR
                                    && isBuffering && shouldContinueDownloading) {
                                setStatus(Status.BUFFERING);
                            }

                            Thread.sleep(40);
                        } catch (InterruptedException e) {
                            break;
                        } catch (Exception e) {
                            LOGGER.error("Playback error: {}", e.getMessage(), e);
                            try {
                                fullyRetryCurrent();
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
            if (!startPlayingFuture.isDone()) {
                startPlayingFuture.completeExceptionally(e);
            }
            fullyRetryCurrent();
        } finally {
            LOGGER.debug("Play task finished");
        }
    }

    @SuppressWarnings("BusyWait")
    private void downloadAudioWithRetry(boolean forceSync, CompletableFuture<Void> downloadInitializedFuture) {
        int localRetryCount = 0;
        boolean forceSyncInternal = forceSync;

        MusicResourceInfo musicResourceInfo = MusicResourceInfo.NONE;
        while (shouldContinueDownloading) {
            try {
                if (musicResourceInfo.equals(MusicResourceInfo.NONE) || localRetryCount % 3 == 0) {
                    musicResourceInfo = getCurrentMusicResourceInfo(Quality.valueOf(ClientConfigDefinition.primaryChosenQuality.get()), musicResourceInfo).get();
                }
                LOGGER.debug("Starting audio download (attempt {})", localRetryCount + 1);

                AudioDecoder decoder = loadAudioDecoder(musicResourceInfo.getUrl(), musicResourceInfo.getType());
                currentDecoder = decoder;
                downloadInitializedFuture.complete(null);

                if (status.get() != Status.ERROR && status.get() != Status.RETRYING) {
                    setStatus(Status.BUFFERING);
                }

                if (forceSyncInternal) {
                    int bytesPerSample = getBytesPerSample(decoder.getFormat());
                    int bytesPerSecond = decoder.getSampleRate() * bytesPerSample;

                    long bytesSkipped = 0;
                    long startSyncTimestamp = System.currentTimeMillis();
                    while (shouldContinueDownloading) {
                        long seconds = Duration.between(serverStartTime, ZonedDateTime.now()).getSeconds();
                        long skipBytes = seconds
                                * bytesPerSecond;
                        if (bytesSkipped >= skipBytes) {
                            break;
                        }
                        byte[] chunk = decoder.readChunk(skipBytes - bytesSkipped);
                        if (chunk == null) break;
                        bytesSkipped += chunk.length;
                    }
                    long current = System.currentTimeMillis();
                    LOGGER.debug("Skipped {} bytes in {} millis", bytesSkipped, current - startSyncTimestamp);
                }

                // 先填充一些数据到缓冲区
                int initialBuffers = 0;
                long startTimeForSpeedCalc = System.currentTimeMillis();
                long totalBytesForSpeedCalc = 0;
                while (shouldContinueDownloading && initialBuffers < BUFFER_COUNT * 2) {
                    byte[] audioData = decoder.readChunk(BUFFER_SIZE);
                    if (audioData == null) break;

                    if (!audioBuffer.offer(audioData, 100, TimeUnit.MILLISECONDS)) {
                        // 缓冲区满，继续尝试
                        continue;
                    }
                    totalBufferedBytes.addAndGet(audioData.length);
                    initialBuffers++;
                    totalBytesForSpeedCalc += audioData.length;

                    if (initialBuffers == 5) { // 收集5个数据块后计算速度
                        long elapsedTime = System.currentTimeMillis() - startTimeForSpeedCalc;
                        if (elapsedTime > 0) {
                            bytesPerSecond = (totalBytesForSpeedCalc * 1000L) / elapsedTime;
                            LOGGER.debug("Calculated download speed: {} bytes/sec", bytesPerSecond);
                        }
                    }

                    if (calculateBufferedSeconds(decoder.getFormat()) >= 2) {
                        setStatus(Status.PLAYING);
                    }
                }

                // 继续下载剩余数据
                while (shouldContinueDownloading) {
                    byte[] audioData = decoder.readChunk(BUFFER_SIZE);

                    // 如果缓冲区已满，等待一会儿
                    while (shouldContinueDownloading && audioBuffer.remainingCapacity() == 0) {
                        Thread.sleep(50);
                    }
                    if (audioData == null) continue;

                    if (!shouldContinueDownloading) break;

                    audioBuffer.put(audioData);
                    totalBufferedBytes.addAndGet(audioData.length);
                }

                // 下载完成
                LOGGER.debug("Audio download completed");
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

    private void updateVolumeIfNecessary() {
        float musicVolume = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MUSIC);
        if (lastVolume != musicVolume && source != 0 && AL10.alIsSource(source)) {
            AL10.alSourcef(source, AL10.AL_GAIN, musicVolume);
            lastVolume = musicVolume;
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

    private float calculateBufferedSeconds(int format) {
        if (bytesPerSecond == 0) return 0;
        int bytesPerSample = getBytesPerSample(format);
        long samplesPerSecond = bytesPerSecond / bytesPerSample;
        if (samplesPerSecond == 0) return 0;

        long bufferedSamples = totalBufferedBytes.get() / bytesPerSample;
        return (float) bufferedSamples / samplesPerSecond;
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
        shouldContinuePlaying = false;
        shouldContinueDownloading = false;

        // 取消任务
        if (playingFuture != null) {
            playingFuture.cancel(true);
            playingFuture = null;
        }

        if (downloadFuture != null) {
            downloadFuture.cancel(true);
            downloadFuture = null;
        }

        if (currentDecoder != null) {
            try {
                currentDecoder.close();
            } catch (Exception e) {
                // Ignore
            }
            currentDecoder = null;
        }

        lastVolume = 1;
        setStatus(Status.IDLE);
        cleanup();
    }

    private void cleanup() {
        synchronized (StreamAudioPlayer.class) {
            try {
                if (source != 0 && AL10.alIsSource(source)) {
                    AL10.alSourceStop(source);
                    int error = AL10.alGetError();
                    if (error != AL10.AL_NO_ERROR) {
                        LOGGER.warn("Error stopping source: {}", error);
                    }

                    int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
                    //noinspection SpellCheckingInspection
                    checkALError("alGetSourcei");
                    while (processed-- > 0) {
                        int[] buffer = new int[1];
                        AL10.alSourceUnqueueBuffers(source, buffer);
                        try {
                            //noinspection SpellCheckingInspection
                            checkALError("alSourceUnqueueBuffers");
                        } catch (Exception ignored) {
                            //noinspection SpellCheckingInspection
                            LOGGER.warn("Failed to unqueue buffers: {}", processed);
                        }
                    }

                    AL10.alDeleteSources(source);
                    try {
                        checkALError("alDeleteSources");
                    } catch (Exception ignored) {
                        LOGGER.warn("Failed to delete sources");
                    }

                    source = 0;
                }

                for (int i = 0; i < buffers.length; i++) {
                    if (buffers[i] != 0 && AL10.alIsBuffer(buffers[i])) {
                        AL10.alDeleteBuffers(buffers[i]);
                        try {
                            checkALError("alDeleteBuffers");
                        } catch (Exception ignored) {
                            LOGGER.warn("Failed to delete buffers: {}", i);
                        }
                        buffers[i] = 0;
                    }
                }

                initialized.set(false);
                lastVolume = 1;

                // 清空缓冲区
                audioBuffer.clear();
                totalBufferedBytes.set(0);

                LOGGER.debug("Cleanup completed");
            } catch (Exception e) {
                LOGGER.error("Cleanup error", e);
            }
        }
    }

    // 获取当前缓冲状态（秒）
    @SuppressWarnings("unused")
    public float getBufferedSeconds() {
        if (currentDecoder == null) return 0;
        return calculateBufferedSeconds(currentDecoder.getFormat());
    }

    public CompletableFuture<MusicResourceInfo> getCurrentMusicResourceInfo(Quality quality, MusicResourceInfo previous) {
        CompletableFuture<MusicResourceInfo> future = new CompletableFuture<>();
        GetMusicResourceResponse.setReceiver(currentMusicDetail.getId(), value -> {
            if (value == null || value == MusicResourceInfo.NONE) {
                MusicService.getInstance().switchMusic(MusicDetail.NONE, null, I18n.get("music_hud.text.failedToLoadMusicResource"));
                setStatus(Status.ERROR);
            } else {
                future.complete(value);
            }
        });
        String url = previous.getUrl() == null ? "" : previous.getUrl();
        NetworkManager.sendToServer(new GetMusicResourceRequest(currentMusicDetail.getId(), quality, url));
        return future;
    }

    public enum Status {
        IDLE, BUFFERING, PLAYING, RETRYING, ERROR
    }
}