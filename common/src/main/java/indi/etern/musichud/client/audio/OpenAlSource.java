package indi.etern.musichud.client.audio;

import indi.etern.musichud.MusicHud;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTFloat32;
import org.lwjgl.openal.SOFTDirectChannels;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Facade over a single OpenAL source: owns the source and its buffer pool,
 * performs all mechanical AL operations with built-in error checks, drives the
 * {@link PlaybackLedger} and optionally delivers the currently playing PCM to a
 * {@link PcmSink}.
 * <p>
 * The buffer pool (queue/available round-robin) is fully internal, so the
 * orchestrator can never hit buffer-id reuse errors. The ledger queue is kept
 * in sync: {@code queueData} adds an entry, unqueue removes the head, and
 * {@link #flush()} resets it together with the sample-offset anchor (the
 * offset is only meaningful within a single format segment).
 */
public final class OpenAlSource implements AutoCloseable {
    public record Config(int bufferCount, boolean directChannels) {
        public Config {
            if (bufferCount <= 0) {
                throw new IllegalArgumentException("bufferCount must be positive");
            }
        }
    }

    private static final Logger LOGGER = MusicHud.getLogger(OpenAlSource.class);

    private static final Set<Integer> OWNED = ConcurrentHashMap.newKeySet();

    public static boolean isMusicHudSource(int sourceId) {
        return OWNED.contains(sourceId);
    }

    private static void register(int sourceId) {
        OWNED.add(sourceId);
    }

    private static void unregister(int sourceId) {
        OWNED.remove(sourceId);
    }

    public static Set<Integer> ownedSourceIds() {
        return Set.copyOf(OWNED);
    }

    /**
     * Whether the current OpenAL context supports float32 PCM buffers
     * ({@code AL_EXT_FLOAT32}, i.e. {@link EXTFloat32#AL_FORMAT_STEREO_FLOAT32}).
     * OpenAL Soft (bundled with LWJGL/Minecraft) exposes it natively, so 24/32-bit
     * sources can be fed losslessly instead of being dithered down to 16-bit.
     */
    public static boolean isFloat32Supported() {
        try {
            return AL.getCapabilities().AL_EXT_FLOAT32;
        } catch (RuntimeException e) {
            // OpenAL not ready yet (e.g. download thread racing engine init);
            // fall back to the 16-bit resampling pipeline.
            return false;
        }
    }

    private final PlaybackLedger ledger;
    private final Config config;
    private final int[] buffers;
    private int source = 0;
    private int roundRobinIndex = 0;
    private long lastSampleOffset = -1;
    private volatile PcmSink pcmSink;

    private OpenAlSource(Config config, PlaybackLedger ledger) {
        this.config = config;
        this.ledger = ledger;
        this.buffers = new int[config.bufferCount()];
    }

    public static OpenAlSource create(Config config, PlaybackLedger ledger) {
        OpenAlSource alSource = new OpenAlSource(config, ledger);
        alSource.initSource();
        register(alSource.source);
        return alSource;
    }

    public void setPcmSink(@Nullable PcmSink pcmSink) {
        this.pcmSink = pcmSink;
    }

    public int sourceId() {
        return source;
    }

    private void initSource() {
        source = AL10.alGenSources();
        if (source == 0) {
            throw new SourceInvalidException("Failed to create OpenAL source (context unavailable?)");
        }
        try {
            for (int i = 0; i < buffers.length; i++) {
                buffers[i] = AL10.alGenBuffers();
                if (buffers[i] == 0) {
                    throw new SourceInvalidException("Failed to create OpenAL buffer");
                }
            }
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
            AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0);
            if (config.directChannels() && AL.getCapabilities().AL_SOFT_direct_channels) {
                AL10.alSourcei(source, SOFTDirectChannels.AL_DIRECT_CHANNELS_SOFT, AL10.AL_TRUE);
            }
            checkALError("source configuration");
        } catch (RuntimeException e) {
            // 部分创建失败时清理已分配的资源，避免泄漏
            if (source != 0) {
                AL10.alDeleteSources(source);
                AL10.alGetError();
                source = 0;
            }
            for (int i = 0; i < buffers.length; i++) {
                if (buffers[i] != 0) {
                    AL10.alDeleteBuffers(buffers[i]);
                    AL10.alGetError();
                    buffers[i] = 0;
                }
            }
            throw e;
        }
    }

    private void checkSourceValid() {
        if (source == 0 || !AL10.alIsSource(source)) {
            throw new SourceInvalidException("OpenAL source is invalid");
        }
    }

    /**
     * Feed the source: if the source queue is empty, actively fill it from the
     * supplier (buffer pool round-robin) and start playback; otherwise refill
     * the processed slots. Returns the number of chunks queued.
     */
    public int fill(AudioChunkSupplier supplier, int maxSlots, int format, int sampleRate) {
        checkSourceValid();
        if (queuedCount() == 0) {
            return fillEmpty(supplier, maxSlots, format, sampleRate);
        }
        int slots = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        checkALError("alGetSourcei-Processed");
        int filled = 0;
        for (int i = 0; i < slots && filled < maxSlots; i++) {
            int[] buffer = new int[1];
            AL10.alSourceUnqueueBuffers(source, buffer);
            checkALError("alSourceUnqueueBuffers");
            ledger.removeHead();
            byte[] data = supplier.next();
            if (data == null) break;
            queueData(buffer[0], data, format, sampleRate);
            filled++;
        }
        return filled;
    }

    private int fillEmpty(AudioChunkSupplier supplier, int maxSlots, int format, int sampleRate) {
        int filled = 0;
        while (filled < maxSlots) {
            byte[] data = supplier.next();
            if (data == null) break;
            queueChunk(data, format, sampleRate);
            filled++;
        }
        if (filled > 0) {
            play();
        }
        return filled;
    }

    /**
     * Explicitly queue one chunk (initial prefill), taking the next buffer from
     * the pool. The source queue must be empty.
     */
    public void queueChunk(byte[] data, int format, int sampleRate) {
        checkSourceValid();
        int bufferId = buffers[roundRobinIndex];
        roundRobinIndex = (roundRobinIndex + 1) % buffers.length;
        queueData(bufferId, data, format, sampleRate);
    }

    private void queueData(int bufferId, byte[] data, int format, int sampleRate) {
        ByteBuffer direct = ByteBuffer.allocateDirect(data.length);
        direct.put(data);
        direct.flip();
        AL10.alBufferData(bufferId, format, direct, sampleRate);
        checkALError("alBufferData");
        AL10.alSourceQueueBuffers(source, bufferId);
        checkALError("alSourceQueueBuffers");
        ledger.fedBytes.addAndGet(data.length);
        ledger.add(new PlaybackLedger.LedgerEntry(direct, format, sampleRate, data.length,
                data.length / Math.max(1, bytesPerSample(format))));
    }

    /**
     * Update the actual playback position from the OpenAL sample offset and
     * deliver the currently playing PCM chunk to the PCM sink.
     */
    public void updatePlaybackPosition() {
        checkSourceValid();
        ledger.playbackBytes = Math.max(0, ledger.fedBytes.get() - ledger.queuedBytes.get());
        try {
            long offset = AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
            checkALError("alGetSourcei-SampleOffset");
            if (offset >= 0 && offset >= lastSampleOffset) {
                PlaybackLedger.LedgerEntry head = ledger.peekFirst();
                int bytesPerFrame = head != null && head.sampleCount() > 0 ? head.bytes() / head.sampleCount() : 4;
                ledger.playbackBytes = offset * bytesPerFrame;
                lastSampleOffset = offset;
            } else {
                lastSampleOffset = -1;
            }
        } catch (Exception ignored) {
        }
        deliverPcm();
    }

    private void deliverPcm() {
        PcmSink sink = pcmSink;
        if (sink == null) return;
        PlaybackLedger.LedgerEntry head = ledger.peekFirst();
        if (head == null) return;
        sink.onPcm(new PcmChunk(head.pcm().slice(), head.format(), head.sampleRate()));
    }

    public void flush() {
        checkSourceValid();
        AL10.alSourceStop(source);
        checkALError("alSourceStop-Flush");
        int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
        checkALError("alGetSourcei-Queued-Flush");
        for (int i = 0; i < queued; i++) {
            int[] buffer = new int[1];
            AL10.alSourceUnqueueBuffers(source, buffer);
            checkALError("alSourceUnqueueBuffers-Flush");
        }
        ledger.resetQueue();
        roundRobinIndex = 0;
        lastSampleOffset = -1;
    }

    public void play() {
        checkSourceValid();
        AL10.alSourcePlay(source);
        checkALError("alSourcePlay");
    }

    public void stop() {
        checkSourceValid();
        AL10.alSourceStop(source);
        checkALError("alSourceStop");
    }

    public void setGain(float gain) {
        checkSourceValid();
        AL10.alSourcef(source, AL10.AL_GAIN, gain);
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            LOGGER.warn("Failed to set source gain to {}: {} (source: {})", gain, getALErrorString(error), source);
        }
    }

    public boolean isPlaying() {
        checkSourceValid();
        return AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
    }

    public boolean isEmpty() {
        return queuedCount() == 0;
    }

    public int queuedCount() {
        checkSourceValid();
        return AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
    }

    public static int bytesPerSample(int format) {
        return switch (format) {
            case AL10.AL_FORMAT_MONO8 -> 1;
            case AL10.AL_FORMAT_MONO16, AL10.AL_FORMAT_STEREO8 -> 2;
            case AL10.AL_FORMAT_STEREO16, EXTFloat32.AL_FORMAT_MONO_FLOAT32 -> 4;
            case EXTFloat32.AL_FORMAT_STEREO_FLOAT32 -> 8;
            default -> 4;
        };
    }

    @Override
    public void close() {
        if (source != 0 && AL10.alIsSource(source)) {
            AL10.alSourceStop(source);
            AL10.alGetError();
            int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
            AL10.alGetError();
            for (int i = 0; i < queued; i++) {
                int[] buffer = new int[1];
                AL10.alSourceUnqueueBuffers(source, buffer);
                AL10.alGetError();
            }
            AL10.alDeleteSources(source);
            AL10.alGetError();
            unregister(source);
            source = 0;
        }
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] != 0) {
                if (AL10.alIsBuffer(buffers[i])) {
                    AL10.alDeleteBuffers(buffers[i]);
                    AL10.alGetError();
                }
                buffers[i] = 0;
            }
        }
        ledger.resetAll();
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
}
