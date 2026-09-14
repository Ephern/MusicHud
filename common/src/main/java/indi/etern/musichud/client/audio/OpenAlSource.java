package indi.etern.musichud.client.audio;

import indi.etern.musichud.MusicHud;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTFloat32;
import org.lwjgl.openal.EXTMCFormats;
import org.lwjgl.openal.SOFTDirectChannels;
import org.lwjgl.openal.SOFTDirectChannelsRemix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Facade over a single OpenAL source and its internal buffer pool; drives the
 * {@link PlaybackLedger} and optionally delivers the playing PCM to a {@link PcmSink}.
 */
public final class OpenAlSource implements AutoCloseable {
    /**
     * How a multi-channel buffer is fed to the output; resolved against the live
     * context capabilities when the source is created.
     */
    public enum DirectChannels {
        /** Let OpenAL Soft virtualize/downmix the buffer. */
        OFF,
        /** Direct output, dropping channels the device has no slot for. */
        DROP_UNMATCHED,
        /**
         * Direct output, remixing unmatched channels into the closest outputs
         * ({@code AL_REMIX_UNMATCHED_SOFT}). Preferred for discrete surround:
         * matching devices get the true layout, others still hear every channel.
         */
        REMIX_UNMATCHED
    }

    public record Config(int bufferCount, DirectChannels directChannels) {
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

    /**
     * Whether the current OpenAL context supports discrete multichannel PCM
     * buffers ({@code AL_EXT_MCFORMATS}, i.e. quad/5.1/6.1/7.1 layouts).
     */
    public static boolean isMultichannelSupported() {
        try {
            return AL.getCapabilities().AL_EXT_MCFORMATS;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Discrete multichannel buffers (quad/5.1/6.1/7.1) use
     * {@link DirectChannels#REMIX_UNMATCHED}; everything else uses plain direct output.
     */
    public static DirectChannels directChannelsFor(int format) {
        return switch (format) {
            case EXTMCFormats.AL_FORMAT_QUAD8, EXTMCFormats.AL_FORMAT_QUAD16,
                 EXTMCFormats.AL_FORMAT_51CHN8, EXTMCFormats.AL_FORMAT_51CHN16,
                 EXTMCFormats.AL_FORMAT_61CHN8, EXTMCFormats.AL_FORMAT_61CHN16,
                 EXTMCFormats.AL_FORMAT_71CHN8, EXTMCFormats.AL_FORMAT_71CHN16 -> DirectChannels.REMIX_UNMATCHED;
            default -> DirectChannels.DROP_UNMATCHED;
        };
    }

    private static int directChannelsValue(DirectChannels mode) {
        if (mode == DirectChannels.OFF) return 0;
        boolean direct;
        boolean remix;
        try {
            var caps = AL.getCapabilities();
            direct = caps.AL_SOFT_direct_channels;
            remix = caps.AL_SOFT_direct_channels_remix;
        } catch (RuntimeException e) {
            return 0;
        }
        if (!direct) return 0;
        if (mode == DirectChannels.REMIX_UNMATCHED && remix) {
            return SOFTDirectChannelsRemix.AL_REMIX_UNMATCHED_SOFT;
        }
        return AL10.AL_TRUE;
    }

    /**
     * Whether the throwable looks like a buffer-upload rejection from
     * {@code alBufferData} (e.g. the device refused a discrete multichannel
     * format it advertised). Used to trigger the downmix fallback.
     */
    public static boolean isBufferDataError(Throwable e) {
        // A reload/device loss is not a format rejection; the fast recovery handles it.
        if (isInvalidSourceError(e)) return false;
        if (e instanceof OpenAlException alError) {
            return alError.operation().startsWith("alBufferData") && isFormatRejection(alError.errorCode());
        }
        return false;
    }

    private static boolean isFormatRejection(int errorCode) {
        return errorCode == AL10.AL_INVALID_ENUM
                || errorCode == AL10.AL_INVALID_VALUE
                || errorCode == AL10.AL_OUT_OF_MEMORY;
    }

    /**
     * A checked OpenAL error carrying the failing operation and raw AL error code,
     * so callers can tell a real buffer-format rejection from a transient error.
     */
    public static final class OpenAlException extends RuntimeException {
        private final int errorCode;
        private final String operation;

        public OpenAlException(String operation, int errorCode, String message) {
            super(message);
            this.operation = operation;
            this.errorCode = errorCode;
        }

        public int errorCode() {
            return errorCode;
        }

        public String operation() {
            return operation;
        }
    }

    /** Whether the throwable means the source name went invalid (reload/device loss). */
    public static boolean isInvalidSourceError(Throwable e) {
        if (e instanceof SourceInvalidException) return true;
        String message = e == null ? null : e.getMessage();
        return message != null && message.contains("AL_INVALID_NAME");
    }

    private final PlaybackLedger ledger;
    private final Config config;
    private final int[] buffers;
    /** Reload generation at creation; a mismatch means the context was rebuilt. */
    private final long contextGeneration;
    private int source = 0;
    private int roundRobinIndex = 0;
    private long lastSampleOffset = -1;
    private volatile PcmSink pcmSink;

    private OpenAlSource(Config config, PlaybackLedger ledger) {
        this.config = config;
        this.ledger = ledger;
        this.buffers = new int[config.bufferCount()];
        this.contextGeneration = SoundEngineState.getReloadGeneration();
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

    /** Whether this source still belongs to the live OpenAL context. */
    public boolean isContextCurrent() {
        return source != 0 && contextGeneration == SoundEngineState.getReloadGeneration();
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
            clearStaleALError();
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
            AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0);
            int directValue = directChannelsValue(config.directChannels());
            if (directValue != 0) {
                AL10.alSourcei(source, SOFTDirectChannels.AL_DIRECT_CHANNELS_SOFT, directValue);
            }
            checkALError("source configuration");
        } catch (RuntimeException e) {
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
        if (source == 0
                || contextGeneration != SoundEngineState.getReloadGeneration()
                || !AL10.alIsSource(source)) {
            throw new SourceInvalidException("OpenAL source is invalid");
        }
    }

    /** Fill the queue from the supplier (buffer pool round-robin); returns chunks queued. */
    public int fill(AudioChunkSupplier supplier, int maxSlots, int format, int sampleRate) {
        // Skip uploads while the context is half-dead during a reload.
        if (SoundEngineState.getCurrent() == SoundEngineState.LOADING) {
            return 0;
        }
        checkSourceValid();
        clearStaleALError();
        if (queuedCount() == 0) {
            return fillEmpty(supplier, maxSlots, format, sampleRate);
        }
        int slots = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        checkQueryALError("alGetSourcei-Processed");
        int filled = 0;
        for (int i = 0; i < slots && filled < maxSlots; i++) {
            int[] buffer = new int[1];
            clearStaleALError();
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

    public void queueChunk(byte[] data, int format, int sampleRate) {
        checkSourceValid();
        int bufferId = buffers[roundRobinIndex];
        roundRobinIndex = (roundRobinIndex + 1) % buffers.length;
        queueData(bufferId, data, format, sampleRate);
    }

    private void queueData(int bufferId, byte[] data, int format, int sampleRate) {
        validateChunk(data, format);
        ByteBuffer direct = ByteBuffer.allocateDirect(data.length);
        direct.put(data);
        direct.flip();
        clearStaleALError();
        AL10.alBufferData(bufferId, format, direct, sampleRate);
        checkALError("alBufferData format=" + format + " bytes=" + data.length + " buffer=" + bufferId + " rate=" + sampleRate);
        clearStaleALError();
        AL10.alSourceQueueBuffers(source, bufferId);
        checkALError("alSourceQueueBuffers buffer=" + bufferId);
        ledger.fedBytes.addAndGet(data.length);
        ledger.add(new PlaybackLedger.LedgerEntry(direct, format, sampleRate, data.length,
                data.length / Math.max(1, bytesPerSample(format))));
    }

    /** Flags frame-misaligned chunks and NaN/Inf float samples instead of failing silently. */
    private void validateChunk(byte[] data, int format) {
        int frameBytes = bytesPerSample(format);
        if (data.length % frameBytes != 0) {
            LOGGER.warn("Misaligned PCM chunk: {} bytes is not a multiple of frame size {} (format={}), glitch likely",
                    data.length, frameBytes, format);
        }
        if (format != EXTFloat32.AL_FORMAT_STEREO_FLOAT32 && format != EXTFloat32.AL_FORMAT_MONO_FLOAT32) {
            return;
        }
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int samples = data.length / 4;
        int nanInf = 0;
        int overRange = 0;
        float peak = 0;
        for (int i = 0; i < samples; i++) {
            float v = buf.getFloat(i * 4);
            if (Float.isNaN(v) || Float.isInfinite(v)) {
                nanInf++;
            } else {
                float a = Math.abs(v);
                if (a > peak) peak = a;
                if (a > 1.0f + 1e-6f) overRange++;
            }
        }
        if (nanInf > 0) {
            LOGGER.warn("Float32 PCM chunk contains {} NaN/Inf samples out of {}", nanInf, samples);
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Float32 PCM chunk ok: {} samples, peak={}, overRange={}", samples, peak, overRange);
        }
    }

    /** Update the playback position from {@code AL_SAMPLE_OFFSET} and feed the PCM sink. */
    public void updatePlaybackPosition() {
        checkSourceValid();
        ledger.playbackBytes = Math.max(0, ledger.fedBytes.get() - ledger.queuedBytes.get());
        try {
            clearStaleALError();
            long offset = AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
            checkQueryALError("alGetSourcei-SampleOffset");
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
        clearStaleALError();
        AL10.alSourceStop(source);
        checkALError("alSourceStop-Flush");
        clearStaleALError();
        int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
        checkQueryALError("alGetSourcei-Queued-Flush");
        for (int i = 0; i < queued; i++) {
            int[] buffer = new int[1];
            clearStaleALError();
            AL10.alSourceUnqueueBuffers(source, buffer);
            checkALError("alSourceUnqueueBuffers-Flush");
        }
        ledger.resetQueue();
        roundRobinIndex = 0;
        lastSampleOffset = -1;
    }

    public void play() {
        checkSourceValid();
        clearStaleALError();
        AL10.alSourcePlay(source);
        checkALError("alSourcePlay");
    }

    public void stop() {
        checkSourceValid();
        clearStaleALError();
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
            case EXTMCFormats.AL_FORMAT_QUAD8 -> 4;
            case EXTMCFormats.AL_FORMAT_QUAD16 -> 8;
            case EXTMCFormats.AL_FORMAT_51CHN8 -> 6;
            case EXTMCFormats.AL_FORMAT_51CHN16 -> 12;
            case EXTMCFormats.AL_FORMAT_61CHN8 -> 7;
            case EXTMCFormats.AL_FORMAT_61CHN16 -> 14;
            case EXTMCFormats.AL_FORMAT_71CHN8 -> 8;
            case EXTMCFormats.AL_FORMAT_71CHN16 -> 16;
            default -> 4;
        };
    }

    @Override
    public void close() {
        release();
        ledger.resetAll();
    }

    /** Delete the AL source/buffers without touching the ledger (format-change rebuild). */
    public void release() {
        // Old names may be reused by other objects in the rebuilt context; don't delete them.
        boolean contextChanged = contextGeneration != SoundEngineState.getReloadGeneration();
        if (source != 0) {
            if (!contextChanged && AL10.alIsSource(source)) {
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
            } else {
                AL10.alGetError();
            }
            unregister(source);
            source = 0;
        }
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] != 0) {
                if (!contextChanged && AL10.alIsBuffer(buffers[i])) {
                    AL10.alDeleteBuffers(buffers[i]);
                    AL10.alGetError();
                }
                buffers[i] = 0;
            }
        }
    }

    /**
     * Drains a pending AL error left by another thread sharing this context, so
     * it can't be misattributed to our next checked operation.
     */
    private static void clearStaleALError() {
        AL10.alGetError();
    }

    private void checkALError(String operation) {
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            String errorMsg = getALErrorString(error);
            String context = diagnosticContext();
            LOGGER.warn("OpenAL Error during {}: {} ({}) {}", operation, errorMsg, error, context);
            // A reload or half-dead context turns any AL error into a stale name:
            // route it to fast recovery, never the permanent downmix fallback.
            if (error == AL10.AL_INVALID_NAME
                    || contextGeneration != SoundEngineState.getReloadGeneration()
                    || SoundEngineState.getCurrent() != SoundEngineState.RUNNING) {
                throw new SourceInvalidException("al error occurred while \"" + operation + "\": " + errorMsg + " " + context);
            }
            throw new OpenAlException(operation, error, "al error occurred while \"" + operation + "\": " + errorMsg + " " + context);
        }
    }

    /** A failed read-only query was raised by another thread; log it without aborting. */
    private void checkQueryALError(String operation) {
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            LOGGER.warn("Ignoring non-fatal OpenAL error during {}: {} ({}) {}",
                    operation, getALErrorString(error), error, diagnosticContext());
        }
    }

    /** Source/engine snapshot for error logs. */
    private String diagnosticContext() {
        boolean valid = false;
        try {
            valid = source != 0 && AL10.alIsSource(source);
        } catch (RuntimeException ignored) {
        }
        return "[source=" + source + ", valid=" + valid + ", engine=" + SoundEngineState.getCurrent() + "]";
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
