package indi.etern.musichud.client.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.EXTFloat32.*;
import static org.lwjgl.openal.EXTMCFormats.*;

/**
 * Bounded latest-window analyzer. The audio callback only copies a finite PCM window;
 * FFT runs on the supplied executor. Session/epoch checks reject late computation.
 * LyricsBlossom default analyzer: 2048 rectangular mono FFT, relative band power/dB,
 * four-block weighting, peak hold, and a fixed 120 Hz smoothed output.
 */
public final class PlaybackSpectrum {
    public static final int WINDOW_FRAMES = 2048;
    private final Executor executor;
    private final LongSupplier clock;
    private long generation;
    private long epoch;
    private long sequence;
    private boolean active;
    private boolean paused;
    private boolean scheduled;
    private Frame pending;
    private float[] partial = new float[WINDOW_FRAMES];
    private int partialFrames;
    private int inputRate;
    private int inputFormat;
    private long previousWindowStart = -1;
    private long consumedThrough = -1;
    private final float[][] history = new float[4][3];
    private final float[] peaks = new float[3];
    private final float[] smoothed = new float[3];
    private long tickNanos;
    private double tickAccumulator;
    private static final double NATIVE_TICK_SECONDS = 0.008333333767950535;
    private SpectrumSnapshot published = SpectrumSnapshot.silence(0, 0);

    public PlaybackSpectrum(Executor executor) {
        this(executor, System::nanoTime);
    }

    public PlaybackSpectrum(Executor executor, LongSupplier clock) {
        this.executor = java.util.Objects.requireNonNull(executor);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public synchronized long beginSession() {
        generation++;
        active = true;
        invalidate();
        return generation;
    }

    public synchronized void stop() {
        generation++;
        active = false;
        invalidate();
    }

    public synchronized void setActive(long token, boolean playing) {
        if (token == generation && active != playing) {
            active = playing;
            invalidate();
        }
    }

    private void resetInput() {
        epoch++;
        pending = null;
        partialFrames = 0;
        previousWindowStart = consumedThrough = -1;
    }

    private void invalidate() {
        resetInput();
        for (float[] bands : history) java.util.Arrays.fill(bands, 0);
        java.util.Arrays.fill(peaks, 0);
        java.util.Arrays.fill(smoothed, 0);
        tickNanos = clock.getAsLong();
        tickAccumulator = 0;
        published = SpectrumSnapshot.silence(generation, sequence);
    }

    /** Consume the borrowed buffer now; only independent complete mono blocks reach the FFT worker. */
    public void accept(long token, PcmChunk chunk) {
        boolean launch = false;
        synchronized (this) {
            if (token != generation || !active || paused || chunk == null || chunk.data() == null) return;
            Layout layout = Layout.of(chunk.format());
            if (layout == null || chunk.sampleRate() < 1 || chunk.sampleRate() > 768_000) return;
            if ((inputRate != 0 && (inputRate != chunk.sampleRate() || inputFormat != chunk.format()))
                    || (chunk.firstSampleFrame() >= 0 && previousWindowStart >= 0
                    && chunk.firstSampleFrame() < previousWindowStart)) invalidate();
            inputRate = chunk.sampleRate();
            inputFormat = chunk.format();
            ByteBuffer pcm = chunk.data().duplicate().order(ByteOrder.LITTLE_ENDIAN);
            int frames = Math.min(WINDOW_FRAMES, pcm.remaining() / layout.frameBytes());
            int start = 0;
            if (chunk.firstSampleFrame() >= 0) {
                if (consumedThrough >= 0 && chunk.firstSampleFrame() > consumedThrough) partialFrames = 0;
                start = (int) Math.min(frames, Math.max(0, consumedThrough - chunk.firstSampleFrame()));
                previousWindowStart = chunk.firstSampleFrame();
                consumedThrough = Math.max(consumedThrough, chunk.firstSampleFrame() + frames);
            }
            for (int i = start; i < frames; i++) {
                partial[partialFrames++] = sampleMono(pcm, layout, i, chunk.gain());
                if (partialFrames == WINDOW_FRAMES) {
                    // Freshness clock only: content times are exposed separately by the mixer diagnostics.
                    pending = new Frame(token, epoch, clock.getAsLong(), partial, inputRate);
                    partial = new float[WINDOW_FRAMES];
                    partialFrames = 0;
                }
            }
            if (pending != null && !scheduled) {
                scheduled = true;
                launch = true;
            }
        }
        if (launch) {
            try {
                executor.execute(this::drain);
            } catch (RuntimeException rejected) {
                synchronized (this) {
                    scheduled = false;
                    invalidate();
                }
            }
        }
    }

    public synchronized void setPaused(boolean muted) {
        // Silent capture preserves native history/envelope, but late worker/input fragments are invalidated.
        if (paused != muted) {
            paused = muted;
            resetInput();
        }
    }

    public synchronized SpectrumSnapshot snapshot(boolean muted) {
        setPaused(muted);
        long now = clock.getAsLong();
        if (!active) {
            return SpectrumSnapshot.silence(generation, sequence);
        }
        tickAccumulator += Math.clamp((now - tickNanos) / 1_000_000_000.0, 0, .5);
        tickNanos = now;
        int ticks = 0;
        while (tickAccumulator >= NATIVE_TICK_SECONDS && ticks++ < 8) {
            tickAccumulator -= NATIVE_TICK_SECONDS;
            for (int band = 0; band < 3; band++) {
                float weighted = history[0][band] * .1f + history[1][band] * .2f
                        + history[2][band] * .3f + history[3][band] * .4f;
                peaks[band] -= peaks[band] / (band == 0 ? 50 : band == 1 ? 100 : 1000);
                peaks[band] = Math.max(peaks[band], weighted);
                smoothed[band] += (peaks[band] - smoothed[band]) * .5f;
            }
        }
        if (tickAccumulator >= NATIVE_TICK_SECONDS) tickAccumulator = 0;
        // Native fixed-step scheduling runs even before the first complete FFT result arrives.
        if (published.frames() == 0) return SpectrumSnapshot.silence(generation, sequence);
        return new SpectrumSnapshot(generation, sequence, published.timestampNanos(),
                shape(smoothed[0]), shape(smoothed[1]), shape(smoothed[2]), inputRate, WINDOW_FRAMES);
    }

    static float[] decodeMono(PcmChunk chunk) {
        return decodeMono(chunk, WINDOW_FRAMES);
    }

    static float[] decodeMono(PcmChunk chunk, int maxFrames) {
        if (chunk == null || chunk.data() == null || chunk.sampleRate() < 1 || chunk.sampleRate() > 768_000) return null;
        Layout layout = Layout.of(chunk.format());
        if (layout == null) return null;
        ByteBuffer pcm = chunk.data().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int frames = Math.min(Math.clamp(maxFrames, 0, 16384), pcm.remaining() / layout.frameBytes());
        float[] mono = new float[frames];
        for (int i = 0; i < frames; i++) mono[i] = sampleMono(pcm, layout, i, chunk.gain());
        return mono;
    }

    private static float sampleMono(ByteBuffer pcm, Layout layout, int frame, float gain) {
        float mono = 0;
        for (int channel = 0; channel < layout.channels; channel++) {
            int offset = pcm.position() + (frame * layout.channels + channel) * layout.bytes;
            float sample = switch (layout.bytes) {
                case 1 -> ((pcm.get(offset) & 255) - 128) / 128f;
                case 2 -> pcm.getShort(offset) / 32768f;
                default -> pcm.getFloat(offset);
            };
            mono += Float.isFinite(sample) ? Math.clamp(sample, -1, 1) : 0;
        }
        return mono / layout.channels * (Float.isFinite(gain) ? Math.max(0, gain) : 0);
    }

    private static float shape(float value) {
        float x = Math.clamp(value, 0, 1);
        return x * x * x * (x * (6 * x - 15) + 10);
    }

    private void drain() {
        while (true) {
            Frame frame;
            synchronized (this) {
                frame = pending;
                pending = null;
                if (frame == null) {
                    scheduled = false;
                    return;
                }
            }
            float[] energy = analyze(frame);
            synchronized (this) {
                if (frame.token != generation || frame.epoch != epoch || !active) continue;
                for (int i = 0; i < history.length - 1; i++) {
                    System.arraycopy(history[i + 1], 0, history[i], 0, 3);
                }
                System.arraycopy(energy, 0, history[3], 0, 3);
                published = new SpectrumSnapshot(generation, ++sequence, frame.nanos,
                        energy[0], energy[1], energy[2], frame.rate, WINDOW_FRAMES);
            }
        }
    }

    private static float[] analyze(Frame frame) {
        double[] real = new double[WINDOW_FRAMES];
        double[] imaginary = new double[WINDOW_FRAMES];
        for (int i = 0; i < real.length; i++) real[i] = frame.mono[i];
        fft(real, imaginary);
        double[] power = new double[WINDOW_FRAMES / 2];
        double total = 0;
        for (int bin = 0; bin < power.length; bin++) {
            power[bin] = real[bin] * real[bin] + imaginary[bin] * imaginary[bin];
            if (bin == 0) {
                // Native real FFT packs Nyquist into imaginary[0], so it also contributes to total power.
                power[bin] += real[WINDOW_FRAMES / 2] * real[WINDOW_FRAMES / 2]
                        + imaginary[WINDOW_FRAMES / 2] * imaginary[WINDOW_FRAMES / 2];
            }
            total += power[bin];
        }
        float[] result = new float[3];
        if (!(total > 0) || !Double.isFinite(total)) return result;
        // Native default gain=1, AGC disabled; no Hann window or invented amplitude scale.
        double intensity = 20 * Math.log10(total) / 120;
        int[] edges = { bin(20, frame.rate), bin(300, frame.rate), bin(3500, frame.rate),
                bin(Math.min(frame.rate / 2.0, 23000), frame.rate) };
        for (int band = 0; band < result.length; band++) {
            double sum = 0;
            for (int i = edges[band]; i < edges[band + 1]; i++) sum += power[i];
            result[band] = (float) Math.clamp(sum / total * intensity, 0, 1);
        }
        return result;
    }

    private static int bin(double frequency, int rate) {
        return Math.clamp((int) (frequency / ((double) rate / WINDOW_FRAMES)), 1, WINDOW_FRAMES / 2 - 1);
    }

    private static void fft(double[] real, double[] imaginary) {
        int n = real.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double value = real[i]; real[i] = real[j]; real[j] = value;
            }
        }
        for (int length = 2; length <= n; length <<= 1) {
            double angle = -2 * Math.PI / length;
            double wr = Math.cos(angle), wi = Math.sin(angle);
            for (int start = 0; start < n; start += length) {
                double cr = 1, ci = 0;
                for (int j = 0; j < length / 2; j++) {
                    int a = start + j, b = a + length / 2;
                    double tr = cr * real[b] - ci * imaginary[b];
                    double ti = cr * imaginary[b] + ci * real[b];
                    real[b] = real[a] - tr; imaginary[b] = imaginary[a] - ti;
                    real[a] += tr; imaginary[a] += ti;
                    double next = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr; cr = next;
                }
            }
        }
    }

    private record Frame(long token, long epoch, long nanos, float[] mono, int rate) {}
    private record Layout(int channels, int bytes) {
        int frameBytes() { return channels * bytes; }
        static Layout of(int format) {
            return switch (format) {
                case AL_FORMAT_MONO8 -> new Layout(1, 1);
                case AL_FORMAT_MONO16 -> new Layout(1, 2);
                case AL_FORMAT_STEREO8 -> new Layout(2, 1);
                case AL_FORMAT_STEREO16 -> new Layout(2, 2);
                case AL_FORMAT_MONO_FLOAT32 -> new Layout(1, 4);
                case AL_FORMAT_STEREO_FLOAT32 -> new Layout(2, 4);
                case AL_FORMAT_QUAD8 -> new Layout(4, 1);
                case AL_FORMAT_QUAD16 -> new Layout(4, 2);
                case AL_FORMAT_51CHN8 -> new Layout(6, 1);
                case AL_FORMAT_51CHN16 -> new Layout(6, 2);
                case AL_FORMAT_61CHN8 -> new Layout(7, 1);
                case AL_FORMAT_61CHN16 -> new Layout(7, 2);
                case AL_FORMAT_71CHN8 -> new Layout(8, 1);
                case AL_FORMAT_71CHN16 -> new Layout(8, 2);
                default -> null;
            };
        }
    }
}
