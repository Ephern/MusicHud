package indi.etern.musichud.client.ui.render;

import java.util.HashMap;
import java.util.Map;

/**
 * Render-thread-only counters for the scene pipeline: offscreen passes per frame, live render
 * targets and their VRAM, uniform/mesh upload bytes. {@link RenderRuntime} samples a snapshot every
 * few seconds while the debug option is on.
 */
public final class RenderStats {
    private static int passes;
    private static int targetsCreated;
    private static int targetsReleased;
    private static int liveTargets;
    private static long liveTargetBytes;
    private static long uniformBytes;
    private static long meshBytes;

    // Section timings accumulate until the next snapshot is logged (not per scene frame).
    private static long sceneNanos;
    private static int sceneCalls;
    private static long offscreenNanos;
    private static int offscreenCalls;
    private static long uniformNanos;
    private static int uniformCalls;
    // Worst single call inside the current window; averages hide one-off stalls.
    private static long sceneMaxNanos;
    private static long offscreenMaxNanos;
    private static long uniformMaxNanos;
    // Phase maxima inside the background chain, used to attribute offscreen spikes.
    private static long transitionMaxNanos;
    private static long audioMaxNanos;
    private static long layersMaxNanos;
    private static long blurMaxNanos;
    private static long warpMaxNanos;
    private static long submitMaxNanos;
    // Fluid/capture blur split: target (re)allocation vs the mip passes themselves.
    private static long blurAllocMaxNanos;
    private static long blurPassMaxNanos;
    private static long captureMaxNanos;
    private static long sceneBlurMaxNanos;
    // Window averages: a maximum cannot tell a per-frame cost from a one-off reallocation.
    private static long transitionNanos;
    private static int transitionCalls;
    private static long audioNanos;
    private static int audioCalls;
    private static long layersNanos;
    private static int layersCalls;
    private static long warpNanos;
    private static int warpCalls;
    private static long submitNanos;
    private static int submitCalls;
    private static long blurAllocNanos;
    private static int blurAllocCalls;
    private static long blurPassNanos;
    private static int blurPassCalls;
    private static long captureNanos;
    private static int captureCalls;
    private static long sceneBlurNanos;
    private static int sceneBlurCalls;
    // Allocations inside the current reporting window, so a spike can be matched to a realloc.
    private static int windowTargetsCreated;
    private static int windowTargetsReleased;
    private static long windowTargetBytes;
    // Per-label churn: {count, bytes}. Only touched when a target is actually created.
    private static final Map<String, long[]> windowAllocByLabel = new HashMap<>();
    // Text/layout time accumulated inside the running scene frame; folded into a max at endFrame.
    private static long frameTextNanos;
    private static long textMaxNanos;

    private RenderStats() {
    }

    /** Resets the per-scene-frame counters; the live target counters are cumulative. */
    public static void beginFrame() {
        passes = 0;
        targetsCreated = 0;
        targetsReleased = 0;
        uniformBytes = 0;
        meshBytes = 0;
        frameTextNanos = 0;
    }

    public static void pass() {
        passes++;
    }

    public static void targetCreated(String label, long bytes) {
        targetsCreated++;
        liveTargets++;
        liveTargetBytes += bytes;
        windowTargetsCreated++;
        windowTargetBytes += bytes;
        long[] entry = windowAllocByLabel.computeIfAbsent(label, k -> new long[2]);
        entry[0]++;
        entry[1] += bytes;
    }

    public static void targetReleased(long bytes) {
        targetsReleased++;
        liveTargets--;
        liveTargetBytes -= bytes;
        windowTargetsReleased++;
    }

    /** Counts the requested upload; the uniform ring may reuse an identical block. */
    public static void uniformUploaded(long bytes) {
        uniformBytes += bytes;
    }

    public static void meshUploaded(long bytes) {
        meshBytes += bytes;
    }

    /** Wall time of one {@code renderFrame} call (scene submission, including the offscreen chain). */
    public static void addSceneNanos(long nanos) {
        sceneNanos += nanos;
        sceneCalls++;
        if (nanos > sceneMaxNanos) {
            sceneMaxNanos = nanos;
        }
    }

    /** Wall time of one background chain build (capture/blur/fluid/composite). */
    public static void addOffscreenNanos(long nanos) {
        offscreenNanos += nanos;
        offscreenCalls++;
        if (nanos > offscreenMaxNanos) {
            offscreenMaxNanos = nanos;
        }
    }

    /** Wall time of one uniform preparation pass. */
    public static void addUniformNanos(long nanos) {
        uniformNanos += nanos;
        uniformCalls++;
        if (nanos > uniformMaxNanos) {
            uniformMaxNanos = nanos;
        }
    }

    /** Clears the section timing accumulators after they have been reported. */
    public static void resetTimings() {
        sceneNanos = 0;
        sceneCalls = 0;
        offscreenNanos = 0;
        offscreenCalls = 0;
        uniformNanos = 0;
        uniformCalls = 0;
        sceneMaxNanos = 0;
        offscreenMaxNanos = 0;
        uniformMaxNanos = 0;
        transitionMaxNanos = 0;
        audioMaxNanos = 0;
        layersMaxNanos = 0;
        blurMaxNanos = 0;
        warpMaxNanos = 0;
        submitMaxNanos = 0;
        blurAllocMaxNanos = 0;
        blurPassMaxNanos = 0;
        captureMaxNanos = 0;
        sceneBlurMaxNanos = 0;
        transitionNanos = 0;
        transitionCalls = 0;
        audioNanos = 0;
        audioCalls = 0;
        layersNanos = 0;
        layersCalls = 0;
        warpNanos = 0;
        warpCalls = 0;
        submitNanos = 0;
        submitCalls = 0;
        blurAllocNanos = 0;
        blurAllocCalls = 0;
        blurPassNanos = 0;
        blurPassCalls = 0;
        captureNanos = 0;
        captureCalls = 0;
        sceneBlurNanos = 0;
        sceneBlurCalls = 0;
        windowTargetsCreated = 0;
        windowTargetsReleased = 0;
        windowTargetBytes = 0;
        windowAllocByLabel.clear();
        frameTextNanos = 0;
        textMaxNanos = 0;
    }

    /** Cover resolve + transition update. */
    public static void phaseTransitionNanos(long nanos) {
        transitionNanos += nanos;
        transitionCalls++;
        if (nanos > transitionMaxNanos) transitionMaxNanos = nanos;
    }

    /** Audio spectrum read + reactive scale computation. */
    public static void phaseAudioNanos(long nanos) {
        audioNanos += nanos;
        audioCalls++;
        if (nanos > audioMaxNanos) audioMaxNanos = nanos;
    }

    /** Cover layer composition. */
    public static void phaseLayersNanos(long nanos) {
        layersNanos += nanos;
        layersCalls++;
        if (nanos > layersMaxNanos) layersMaxNanos = nanos;
    }

    /** Blur pyramid for the fluid canvas. */
    public static void phaseBlurNanos(long nanos) {
        if (nanos > blurMaxNanos) blurMaxNanos = nanos;
    }

    /** Blur target (re)allocation, i.e. the size/level cache miss. */
    public static void phaseBlurAllocNanos(long nanos) {
        blurAllocNanos += nanos;
        blurAllocCalls++;
        if (nanos > blurAllocMaxNanos) blurAllocMaxNanos = nanos;
    }

    /** The blur mip passes themselves, with the targets already allocated. */
    public static void phaseBlurPassNanos(long nanos) {
        blurPassNanos += nanos;
        blurPassCalls++;
        if (nanos > blurPassMaxNanos) blurPassMaxNanos = nanos;
    }

    /** Scene capture into the capture target. */
    public static void phaseCaptureNanos(long nanos) {
        captureNanos += nanos;
        captureCalls++;
        if (nanos > captureMaxNanos) captureMaxNanos = nanos;
    }

    /** Scene blur: allocation and passes together. */
    public static void phaseSceneBlurNanos(long nanos) {
        sceneBlurNanos += nanos;
        sceneBlurCalls++;
        if (nanos > sceneBlurMaxNanos) sceneBlurMaxNanos = nanos;
    }

    /** {@code name=avgus/calls} for every phase that ran in the window. */
    public static String phaseSummary() {
        StringBuilder builder = new StringBuilder(" | phases:");
        appendAverage(builder, "transition", transitionNanos, transitionCalls);
        appendAverage(builder, "capture", captureNanos, captureCalls);
        appendAverage(builder, "sceneBlur", sceneBlurNanos, sceneBlurCalls);
        appendAverage(builder, "layers", layersNanos, layersCalls);
        appendAverage(builder, "blurAlloc", blurAllocNanos, blurAllocCalls);
        appendAverage(builder, "blurPass", blurPassNanos, blurPassCalls);
        appendAverage(builder, "warp", warpNanos, warpCalls);
        appendAverage(builder, "submit", submitNanos, submitCalls);
        appendAverage(builder, "audio", audioNanos, audioCalls);
        return builder.length() == " | phases:".length() ? "" : builder.toString();
    }

    private static void appendAverage(StringBuilder builder, String name, long nanos, int calls) {
        if (calls <= 0) {
            return;
        }
        builder.append(' ').append(name).append('=').append(nanos / (1000L * calls)).append("us/").append(calls);
    }

    /** Text layout + glyph submission time inside the running scene frame. */
    public static void addTextNanos(long nanos) {
        frameTextNanos += nanos;
    }

    /** Closes the text accumulator for the frame and folds it into the window maximum. */
    public static void endTextFrame() {
        if (frameTextNanos > textMaxNanos) textMaxNanos = frameTextNanos;
        frameTextNanos = 0;
    }

    /** Textures created inside the current reporting window. */
    public static int targetsCreatedInWindow() {
        return windowTargetsCreated;
    }

    /** Bytes of the textures created inside the current reporting window. */
    public static long targetBytesInWindow() {
        return windowTargetBytes;
    }

    /** Compact allocation suffix, so a spike can be matched to a texture (re)allocation. */
    public static String windowAllocations() {
        if (windowTargetsCreated == 0 && windowTargetsReleased == 0) return "";
        StringBuilder builder = new StringBuilder(" | alloc: +")
                .append(windowTargetsCreated).append("/-").append(windowTargetsReleased)
                .append(' ').append(windowTargetBytes / 1024L).append("KiB");
        if (!windowAllocByLabel.isEmpty()) {
            builder.append(" by ");
            String[] ordered = windowAllocByLabel.entrySet().stream()
                    .sorted((left, right) -> Long.compare(right.getValue()[1], left.getValue()[1]))
                    .map(entry -> {
                        long[] value = entry.getValue();
                        return entry.getKey() + "=" + value[0] + "/" + (value[1] / 1024L) + "K";
                    })
                    .toArray(String[]::new);
            int shown = Math.min(ordered.length, 5);
            for (int i = 0; i < shown; i++) {
                if (i > 0) builder.append(", ");
                builder.append(ordered[i]);
            }
            if (ordered.length > shown) {
                builder.append(", +").append(ordered.length - shown);
            }
        }
        return builder.toString();
    }

    /** Procedural warp pass (including the per-cycle mesh rebuild). */
    public static void phaseWarpNanos(long nanos) {
        warpNanos += nanos;
        warpCalls++;
        if (nanos > warpMaxNanos) warpMaxNanos = nanos;
    }

    /** Submitting the display element into the GUI batch. */
    public static void phaseSubmitNanos(long nanos) {
        submitNanos += nanos;
        submitCalls++;
        if (nanos > submitMaxNanos) submitMaxNanos = nanos;
    }

    /** Compact "phase=max" suffix listing only the phases that exceeded {@code thresholdNanos}. */
    public static String phaseSpikes(long thresholdNanos) {
        long[] values = {transitionMaxNanos, audioMaxNanos, layersMaxNanos, blurMaxNanos,
                blurAllocMaxNanos, blurPassMaxNanos, warpMaxNanos, submitMaxNanos, textMaxNanos};
        String[] names = {"transition", "audio", "layers", "blur", "blurAlloc", "blurPass",
                "warp", "submit", "text"};
        StringBuilder builder = null;
        for (int i = 0; i < values.length; i++) {
            if (values[i] < thresholdNanos) continue;
            if (builder == null) builder = new StringBuilder(" | phases:");
            builder.append(' ').append(names[i]).append('=').append(values[i] / 1000L).append("us");
        }
        return builder == null ? "" : builder.toString();
    }

    public static Snapshot snapshot() {
        return new Snapshot(passes, targetsCreated, targetsReleased, liveTargets,
                liveTargetBytes, uniformBytes, meshBytes,
                sceneNanos, sceneCalls, offscreenNanos, offscreenCalls, uniformNanos, uniformCalls,
                sceneMaxNanos, offscreenMaxNanos, uniformMaxNanos);
    }

    public record Snapshot(int passes,
                           int targetsCreated,
                           int targetsReleased,
                           int liveTargets,
                           long liveTargetBytes,
                           long uniformBytes,
                           long meshBytes,
                           long sceneNanos,
                           int sceneCalls,
                           long offscreenNanos,
                           int offscreenCalls,
                           long uniformNanos,
                           int uniformCalls,
                           long sceneMaxNanos,
                           long offscreenMaxNanos,
                           long uniformMaxNanos) {

        /** Average microseconds per call, 0 when the section did not run in this window. */
        public static long averageMicros(long nanos, int calls) {
            return calls <= 0 ? 0L : nanos / 1000L / calls;
        }

        /** Worst single call in microseconds. */
        public static long maxMicros(long nanos) {
            return nanos / 1000L;
        }
    }
}
