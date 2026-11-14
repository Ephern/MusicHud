package indi.etern.musichud.client.audio;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTThreadLocalContext;
import org.lwjgl.openal.SOFTDeviceClock;
import org.lwjgl.openal.SOFTSourceLatency;

/** Read-only mapping from a queued source frame to the estimated audible monotonic time. */
public final class SourcePcmClock {
    public enum Mode { DEVICE_CLOCK, SOURCE_LATENCY, QUERY_TIME }

    /**
     * {@code sampleOffset} is relative to the whole remaining queue (processed buffers included);
     * {@code outputLatencyNanos} is -1 when unavailable; {@code extensionError} preserves a failed
     * optional query so callers can report degradation.
     */
    public record Reading(long sampleOffset, long capturedNanos, long queryDurationNanos,
                   long outputLatencyNanos, Mode mode, int extensionError) { }

    private SourcePcmClock() { }

    /** Caller must own the source/ledger lifecycle and clear unrelated stale AL errors first. */
    static Reading query(int source, int sampleRate) {
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate must be positive");
        var al = AL.getCapabilities();
        var alc = ALC.getCapabilities();
        int optionalError = 0;
        if (al.AL_SOFT_source_latency && alc.ALC_SOFT_device_clock) {
            long context = alc.ALC_EXT_thread_local_context ? EXTThreadLocalContext.alcGetThreadContext() : 0;
            if (context == 0) context = ALC10.alcGetCurrentContext();
            long device = context == 0 ? 0 : ALC10.alcGetContextsDevice(context);
            if (device != 0) {
                long[] positionClock = new long[2];
                SOFTSourceLatency.alGetSourcei64vSOFT(source,
                        SOFTDeviceClock.AL_SAMPLE_OFFSET_CLOCK_SOFT, positionClock);
                optionalError = AL10.alGetError();
                if (optionalError == AL10.AL_NO_ERROR && positionClock[0] >= 0) {
                    long[] clockLatency = new long[2];
                    long before = System.nanoTime();
                    SOFTDeviceClock.alcGetInteger64vSOFT(device,
                            SOFTDeviceClock.ALC_DEVICE_CLOCK_LATENCY_SOFT, clockLatency);
                    long after = System.nanoTime();
                    optionalError = ALC10.alcGetError(device);
                    if (optionalError == ALC10.ALC_NO_ERROR && clockLatency[1] >= 0) {
                        // The source offset was mixed at Cs; device latency L is paired with Cd.
                        // Its audible time is host(Cd) + L + Cs - Cd, not host(Cd) - L.
                        long adjustment = positionClock[1] - clockLatency[0];
                        // A reset/disconnected clock must not produce an arbitrarily distant anchor.
                        if (adjustment >= -2_000_000_000L && adjustment <= 2_000_000_000L) {
                            long audible = midpoint(before, after) + clockLatency[1] + adjustment;
                            return reading(positionClock[0], audible, after - before,
                                    clockLatency[1], Mode.DEVICE_CLOCK, 0, sampleRate);
                        }
                    }
                }
            }
        }
        if (al.AL_SOFT_source_latency) {
            long[] offsetLatency = new long[2];
            long before = System.nanoTime();
            SOFTSourceLatency.alGetSourcei64vSOFT(source,
                    SOFTSourceLatency.AL_SAMPLE_OFFSET_LATENCY_SOFT, offsetLatency);
            long after = System.nanoTime();
            int error = AL10.alGetError();
            if (error == AL10.AL_NO_ERROR && offsetLatency[0] >= 0 && offsetLatency[1] >= 0) {
                return reading(offsetLatency[0], midpoint(before, after) + offsetLatency[1],
                        after - before, offsetLatency[1], Mode.SOURCE_LATENCY, optionalError, sampleRate);
            }
            if (optionalError == 0) optionalError = error;
        }
        long before = System.nanoTime();
        int offset = AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
        long after = System.nanoTime();
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            throw new IllegalStateException("OpenAL PCM offset query failed: " + error);
        }
        return new Reading(offset, midpoint(before, after), after - before, -1,
                Mode.QUERY_TIME, optionalError);
    }

    private static Reading reading(long fixedOffset, long audible, long duration, long latency,
                                   Mode mode, int error, int sampleRate) {
        // The copied PCM begins at floor(offset), slightly before the fractional mixer position.
        double fraction = (fixedOffset & 0xffff_ffffL) / 4294967296.0;
        return new Reading(fixedOffset >>> 32, audible - Math.round(fraction * 1_000_000_000.0 / sampleRate),
                duration, latency, mode, error);
    }

    private static long midpoint(long before, long after) { return before + (after - before) / 2; }
}
