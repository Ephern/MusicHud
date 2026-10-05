package indi.etern.musichud.client.ui.lyric;

import indi.etern.musichud.client.dto.LyricLine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Renderer-agnostic calculation of the karaoke highlight sweep shared by the ModernUI
 * lyric view and the HUD lyric renderer.
 * <p>
 * A word-by-word line is modelled as one monotone cubic spline whose keyframes are the
 * <b>phrase starts</b>: {@code time -> character offset}. Interior tangents use PCHIP
 * (Fritsch-Carlson weighted harmonic mean plus the one-sided endpoint rule), which is
 * C1, passes through every keyframe and is <b>monotone by construction</b>. This avoids
 * the non-monotone excursions of a natural cubic spline on very uneven phrase timings
 * (e.g. a long phrase followed by a zero/30 ms phrase), which used to pin the sweep at
 * a phrase start and delay it until late in the phrase. No clamping is needed because
 * overshoot never occurs.
 * <p>
 * The evaluated value is a fractional character offset; pixel mapping (gradient bands,
 * substring widths, scissors) stays in each renderer.
 */
public final class LyricHighlightCalculator {
    private final MonotoneCubicSpline spline; // null when the line is not word-by-word
    private final long lineStartMillis;
    private final long totalMillis;
    private final float firstBoundaryOffset;
    private final float lastBoundaryOffset;
    private final float lastEndOffset;

    public LyricHighlightCalculator(LyricLine line) {
        line.parsePhrases();
        List<LyricLine.Phrase> phrases = line.getPhrases();
        if (phrases == null || phrases.isEmpty()) {
            spline = null;
            lineStartMillis = 0L;
            totalMillis = 1L;
            firstBoundaryOffset = 0f;
            lastBoundaryOffset = 0f;
            lastEndOffset = 0f;
            return;
        }

        int count = phrases.size();
        lineStartMillis = line.getStartTime().toMillis();
        long lastEndMillis = phrases.get(count - 1).endTime().toMillis();
        totalMillis = Math.max(1L, lastEndMillis - lineStartMillis);
        lastEndOffset = phrases.get(count - 1).endOffset();
        firstBoundaryOffset = count > 1 ? phrases.getFirst().endOffset() : lastEndOffset;
        lastBoundaryOffset = count > 1 ? phrases.get(count - 2).endOffset() : 0f;

        // keyframes: line start, every phrase boundary (end of phrase i == start of phrase i+1), text end.
        // Keyframes that share a time (zero-duration phrases) are merged by taking the larger offset.
        List<float[]> points = new ArrayList<>(count + 1);
        points.add(new float[]{0f, 0f});
        for (int i = 1; i < count; i++) {
            LyricLine.Phrase previous = phrases.get(i - 1);
            float time = (previous.endTime().toMillis() - lineStartMillis) / (float) totalMillis;
            float offset = previous.endOffset();
            if (time <= 0f || time >= 1f) {
                continue;
            }
            float[] last = points.getLast();
            if (time <= last[0] + 1e-6f) {
                last[1] = Math.max(last[1], offset);
            } else if (offset > last[1]) {
                points.add(new float[]{time, offset});
            }
        }
        float[] last = points.getLast();
        if (lastEndOffset > last[1]) {
            if (last[0] < 1f - 1e-6f) {
                points.add(new float[]{1f, lastEndOffset});
            } else {
                last[1] = lastEndOffset;
            }
        }

        if (points.size() < 2) {
            spline = null;
            return;
        }
        float[] times = new float[points.size()];
        float[] values = new float[points.size()];
        for (int i = 0; i < points.size(); i++) {
            times[i] = points.get(i)[0];
            values[i] = points.get(i)[1];
        }
        spline = new MonotoneCubicSpline(times, values);
    }

    /**
     * @param offset      interpolated character offset along the line, in {@code [0, lastEndOffset]}
     * @param leadWeight  1 at line start, 0 from the first phrase boundary onwards
     * @param trailWeight 0 until the last phrase boundary, 1 at the line text end
     */
    public record SweepState(float offset, float leadWeight, float trailWeight) {
    }

    /**
     * @return the sweep state, or {@code null} when the line has no parsed phrases
     * (i.e. it is not a word-by-word line)
     */
    public SweepState compute(Duration played) {
        if (spline == null) {
            return null;
        }
        float t = (played.toMillis() - lineStartMillis) / (float) totalMillis;
        t = Math.clamp(t, 0f, 1f);
        float offset = spline.valueAt(t);

        float leadWeight = firstBoundaryOffset > 0f
                ? Math.clamp(1f - offset / firstBoundaryOffset, 0f, 1f)
                : (offset <= 0f ? 1f : 0f);
        float trailSpan = lastEndOffset - lastBoundaryOffset;
        float trailWeight = trailSpan > 0f
                ? Math.clamp((offset - lastBoundaryOffset) / trailSpan, 0f, 1f)
                : (offset >= lastEndOffset ? 1f : 0f);
        return new SweepState(offset, leadWeight, trailWeight);
    }

    /**
     * Monotone cubic Hermite interpolation with PCHIP tangents (Fritsch-Carlson).
     * Guarantees monotonicity for monotone keyframes, so no overshoot and no clamping.
     */
    private static final class MonotoneCubicSpline {
        private final float[] times;
        private final float[] values;
        private final float[] tangents;

        MonotoneCubicSpline(float[] times, float[] values) {
            this.times = times;
            this.values = values;
            int count = times.length;
            this.tangents = new float[count];
            if (count == 2) {
                float slope = (values[1] - values[0]) / (times[1] - times[0]);
                tangents[0] = slope;
                tangents[1] = slope;
                return;
            }

            int intervals = count - 1;
            float[] h = new float[intervals];
            float[] slope = new float[intervals];
            for (int i = 0; i < intervals; i++) {
                h[i] = times[i + 1] - times[i];
                slope[i] = (values[i + 1] - values[i]) / h[i];
            }

            for (int i = 1; i < count - 1; i++) {
                if (slope[i - 1] == 0f || slope[i] == 0f
                        || Math.signum(slope[i - 1]) != Math.signum(slope[i])) {
                    tangents[i] = 0f;
                } else {
                    float w1 = 2f * h[i] + h[i - 1];
                    float w2 = h[i] + 2f * h[i - 1];
                    tangents[i] = (w1 + w2) / (w1 / slope[i - 1] + w2 / slope[i]);
                }
            }

            tangents[0] = endTangent(h[0], h[1], slope[0], slope[1]);
            tangents[count - 1] = endTangent(h[intervals - 1], h[intervals - 2],
                    slope[intervals - 1], slope[intervals - 2]);
        }

        private static float endTangent(float hNear, float hFar, float slopeNear, float slopeFar) {
            float tangent = ((2f * hNear + hFar) * slopeNear - hNear * slopeFar) / (hNear + hFar);
            if (Math.signum(tangent) != Math.signum(slopeNear)) {
                return 0f;
            }
            if (Math.signum(slopeNear) != Math.signum(slopeFar)
                    && Math.abs(tangent) > 3f * Math.abs(slopeNear)) {
                return 3f * slopeNear;
            }
            return tangent;
        }

        float valueAt(float t) {
            int last = times.length - 1;
            if (t <= times[0]) return values[0];
            if (t >= times[last]) return values[last];
            int i = locate(t);
            float h = times[i + 1] - times[i];
            if (h <= 0f) return values[i + 1];
            float u = (t - times[i]) / h;
            float u2 = u * u;
            float u3 = u2 * u;
            float h00 = 2f * u3 - 3f * u2 + 1f;
            float h10 = u3 - 2f * u2 + u;
            float h01 = -2f * u3 + 3f * u2;
            float h11 = u3 - u2;
            return h00 * values[i]
                    + h10 * h * tangents[i]
                    + h01 * values[i + 1]
                    + h11 * h * tangents[i + 1];
        }

        private int locate(float t) {
            for (int i = 0; i < times.length - 1; i++) {
                if (t < times[i + 1]) return i;
            }
            return times.length - 2;
        }
    }
}
