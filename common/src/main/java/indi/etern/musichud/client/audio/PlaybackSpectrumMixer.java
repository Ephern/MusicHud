package indi.etern.musichud.client.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Bounded two-source playing-PCM mix on a shared monotonic timeline, independent of audio rendering. */
final class PlaybackSpectrumMixer {
    static final int RATE = 48000;
    static final int CAPTURE_FRAMES = 4096;
    private static final int FRAMES = PlaybackSpectrum.WINDOW_FRAMES;
    private static final double STEP_NANOS = 1_000_000_000.0 / RATE;
    private final LongSupplier clock;
    private final Map<Long, Source> sources = new LinkedHashMap<>();
    private double nextNanos = Double.NaN;
    private long mixedBlocks;
    private long missingSamples;
    private long contentStartNanos;
    private long contentEndNanos;
    private long assemblyNanos;
    private long minimumEndLeadNanos = Long.MAX_VALUE;
    private long maximumEndLeadNanos = Long.MIN_VALUE;

    PlaybackSpectrumMixer() { this(System::nanoTime); }
    PlaybackSpectrumMixer(LongSupplier clock) { this.clock = java.util.Objects.requireNonNull(clock); }


    void retain(Set<Long> active) {
        if (active.size() > 2) throw new IllegalArgumentException("At most current and pending sources");
        sources.keySet().retainAll(active);
        for (long key : active) sources.computeIfAbsent(key, ignored -> new Source());
        if (sources.isEmpty()) nextNanos = Double.NaN;
    }

    void clear() {
        sources.clear();
        nextNanos = Double.NaN;
    }

    void accept(long source, PcmChunk chunk, Consumer<PcmChunk> output) {
        Source state = sources.get(source);
        if (state == null || chunk == null || chunk.captureNanos() == 0) return;
        float[] mono = PlaybackSpectrum.decodeMono(chunk, CAPTURE_FRAMES);
        if (mono == null || mono.length == 0) return;
        if (!state.windows.isEmpty() && chunk.captureNanos() < state.windows.getLast().start) return;
        state.windows.addLast(new Window(chunk.captureNanos(), chunk.sampleRate(), mono));
        while (state.windows.size() > 4) state.windows.removeFirst();
        if (Double.isNaN(state.firstNanos)) state.firstNanos = chunk.captureNanos();
        if (Double.isNaN(nextNanos)) nextNanos = chunk.captureNanos();
        // Four windows bound both memory and backlog. Never manufacture old data after a stall.
        double earliest = sources.values().stream().filter(s -> !s.windows.isEmpty())
                .mapToDouble(s -> s.windows.getFirst().start).max().orElse(nextNanos);
        if (nextNanos < earliest - 250_000_000) nextNanos = earliest;
        for (int block = 0; block < 4; block++) {
            double end = nextNanos + (FRAMES - 1) * STEP_NANOS;
            // Analyze completed engine-side blocks, not PCM ahead of the observed mixer read position.
            double realtimeHorizon = sources.values().stream().filter(s -> !s.windows.isEmpty())
                    .mapToDouble(s -> s.windows.getLast().start).min().orElse(Double.NEGATIVE_INFINITY);
            if (end > realtimeHorizon) return;
            if (sources.values().stream().anyMatch(s -> s.windows.isEmpty() || s.windows.getLast().end() < end)) return;
            ByteBuffer mixed = ByteBuffer.allocate(FRAMES * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int frame = 0; frame < FRAMES; frame++) {
                double time = nextNanos + frame * STEP_NANOS;
                float value = 0;
                for (Source entry : sources.values()) {
                    float sample = entry.sample(time);
                    if (Float.isNaN(sample)) missingSamples++;
                    else value += sample;
                }
                mixed.putFloat(Math.clamp(value, -1, 1));
            }
            contentStartNanos = Math.round(nextNanos);
            contentEndNanos = Math.round(end);
            assemblyNanos = clock.getAsLong();
            long endLead = contentEndNanos - assemblyNanos;
            minimumEndLeadNanos = Math.min(minimumEndLeadNanos, endLead);
            maximumEndLeadNanos = Math.max(maximumEndLeadNanos, endLead);
            output.accept(new PcmChunk(mixed.flip(), 0x10010, RATE, -1, 1, (long) nextNanos));
            mixedBlocks++;
            nextNanos += FRAMES * STEP_NANOS;
        }
    }

    long contentStartNanos() { return contentStartNanos; }
    long contentEndNanos() { return contentEndNanos; }
    long assemblyNanos() { return assemblyNanos; }
    long minimumEndLeadNanos() { return mixedBlocks == 0 ? 0 : minimumEndLeadNanos; }
    long maximumEndLeadNanos() { return mixedBlocks == 0 ? 0 : maximumEndLeadNanos; }

    long mixedBlocks() { return mixedBlocks; }
    long missingSamples() { return missingSamples; }

    private static final class Source {
        final ArrayDeque<Window> windows = new ArrayDeque<>();
        double firstNanos = Double.NaN;
        float sample(double nanos) {
            if (nanos < firstNanos) return 0; // This source had not entered its observed playing timeline yet.
            // Newest overlapping observation carries the most recent applied source gain.
            for (var iterator = windows.descendingIterator(); iterator.hasNext();) {
                Window window = iterator.next();
                double position = (nanos - window.start) * window.rate / 1_000_000_000.0;
                if (position < 0 || position >= window.samples.length) continue;
                int first = (int) position;
                int second = Math.min(first + 1, window.samples.length - 1);
                return (float) (window.samples[first] + (window.samples[second] - window.samples[first]) * (position - first));
            }
            return Float.NaN;
        }
    }
    private record Window(long start, int rate, float[] samples) {
        double end() { return start + (samples.length - 1) * 1_000_000_000.0 / rate; }
    }
}
