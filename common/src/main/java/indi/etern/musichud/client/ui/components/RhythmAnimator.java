package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.PropertyValuesHolder;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.view.View;
import indi.etern.musichud.client.ui.utils.Easings;

/**
 * Unified RHYTHM lyric animation. The breathing scale uses a phase-shifted cosine
 * whose phase offset is derived from the fixed fade-out start time, guaranteeing
 * that the oscillation reaches its minimum exactly when the fade-out begins.
 */
public class RhythmAnimator extends ValueAnimator {
    public static final float MIN_SCALE = 0.85f;
    public static final float BREATHE_MAX_SCALE = 1.0f;
    public static final float FADE_PEAK_SCALE = 1.12f;
    public static final float FADE_END_SCALE = 0.7f;

    public static final long BREATHE_CYCLE_MS = 4000;
    public static final long FADE_IN_DELAY_MS = 800;
    public static final long FADE_IN_DURATION_MS = 400;
    public static final long FADE_OUT_PEAK_MS = 1000;
    public static final long FADE_OUT_SHRINK_MS = 400;

    public static final long FADE_IN_END = FADE_IN_DELAY_MS + FADE_IN_DURATION_MS;
    public static final long FADE_OUT_DURATION = FADE_OUT_PEAK_MS + FADE_OUT_SHRINK_MS;

    private final View row;
    private final View mainText;

    /** Play time at which the fade-out begins (equals the stay duration). */
    private final long fadeOutStartTime;

    /**
     * Phase offset (ms) added to the elapsed breathing time so that
     * {@code cos(2π * (t + offset) / CYCLE)} evaluates to 1 (minimum scale)
     * exactly at {@code t = fadeOutStartTime - FADE_IN_END}.
     */
    private final long phaseOffsetMs;

    /**
     * @param row          the row View whose scaleX/scaleY are animated
     * @param mainText     the main text View whose alpha is animated
     * @param stayDuration total time the lyric should stay emphasized (ms)
     */
    public RhythmAnimator(View row, View mainText, long stayDuration) {
        this.row = row;
        this.mainText = mainText;

        this.fadeOutStartTime = stayDuration;
        this.phaseOffsetMs = computePhaseOffset(stayDuration - FADE_IN_END, BREATHE_CYCLE_MS);

        long totalDuration = fadeOutStartTime + FADE_OUT_DURATION;

        setValues(PropertyValuesHolder.ofFloat(0f, 1f));
        setDuration(totalDuration);
        addUpdateListener(this::onUpdate);
    }

    /** Returns true if there is enough time for at least the fade-in phase. */
    public boolean isValid() {
        return fadeOutStartTime > FADE_IN_END;
    }

    public long getDotFadeDuration(long stayDuration) {
        return Math.max(0, (stayDuration - FADE_IN_DELAY_MS) / 3);
    }

    private void onUpdate(ValueAnimator anim) {
        long playTime = anim.getCurrentPlayTime();
        if (playTime < 0) playTime = 0;

        if (playTime <= fadeOutStartTime) {
            handleBreatheAndFadeIn(playTime);
        } else {
            handleFadeOut(playTime);
        }
    }

    private void handleBreatheAndFadeIn(long playTime) {
        if (playTime >= FADE_IN_DELAY_MS) {
            float alphaFrac = Math.clamp(
                    (playTime - FADE_IN_DELAY_MS) / (float) FADE_IN_DURATION_MS, 0f, 1f);
            mainText.setAlpha(Easings.EASE_OUT_QUAD.getInterpolation(alphaFrac));
        }
        float scale = computeBreathingScale(playTime);
        row.setScaleX(scale);
        row.setScaleY(scale);
    }

    private void handleFadeOut(long playTime) {
        long fadeElapsed = playTime - fadeOutStartTime;

        if (fadeElapsed <= FADE_OUT_PEAK_MS) {
            float frac = Easings.EASE_IN_OUT_QUAD.getInterpolation(
                    (float) fadeElapsed / FADE_OUT_PEAK_MS);
            float scale = MIN_SCALE + (FADE_PEAK_SCALE - MIN_SCALE) * frac;
            row.setScaleX(scale);
            row.setScaleY(scale);
        } else {
            float frac = (float) (fadeElapsed - FADE_OUT_PEAK_MS) / FADE_OUT_SHRINK_MS;
            frac = Math.clamp(frac, 0f, 1f);
            float easedFrac = Easings.EASE_IN_QUINT.getInterpolation(frac);
            float scale = FADE_PEAK_SCALE + (FADE_END_SCALE - FADE_PEAK_SCALE) * easedFrac;
            row.setScaleX(scale);
            row.setScaleY(scale);
            row.setAlpha(1f - easedFrac);
        }
    }

    /**
     * Periodic breathing value [0, 1] with zero derivative at both ends of each cycle.
     * The phase offset guarantees that {@code breathe(t) = 0} at {@code t = fadeOutStartTime}.
     */
    private float computeBreathingScale(long playTime) {
        long breatheElapsed = playTime - FADE_IN_END + phaseOffsetMs;
        float cycleFrac = (float) (breatheElapsed % BREATHE_CYCLE_MS) / BREATHE_CYCLE_MS;
        float t = (1f - (float) Math.cos(cycleFrac * 2 * Math.PI)) / 2f;
        return MIN_SCALE + (BREATHE_MAX_SCALE - MIN_SCALE) * t;
    }

    /**
     * Computes the smallest non-negative offset such that
     * {@code (breatheMs + offset) % cycleMs == 0}.
     */
    private static long computePhaseOffset(long breatheMs, long cycleMs) {
        long remainder = breatheMs % cycleMs;
        return remainder == 0 ? 0 : cycleMs - remainder;
    }
}
