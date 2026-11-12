package indi.etern.musichud.client.ui.components;

import icyllis.arc3d.core.ColorSpaces;
import icyllis.modernui.animation.ColorEvaluator;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.graphics.LinearGradient;
import icyllis.modernui.graphics.Shader;
import icyllis.modernui.text.Layout;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.dto.LyricLine;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.lyric.LyricHighlightCalculator;
import indi.etern.musichud.client.utils.ui.SmoothBumpInterpolator;
import indi.etern.musichud.client.utils.ui.SpringInterpolator;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class LyricHighlightTextView extends TextView {
    private static final int FULL_LINE_HIGHLIGHT_DELAY = 300;
    private static final int ANIMATION_DURATION_MILLIS = 300;
    private static final int RAISE_ANIMATION_DURATION = 1000;
    private static final float BLOOM_STAGGER_RATE = 0.2f;
    private static final float BLOOM_FADE_IN = 0.3f;
    private static final float BLOOM_FADE_OUT = 0.5f;
    private static final SpringInterpolator SPRING = new SpringInterpolator(RAISE_ANIMATION_DURATION * 0.001f, 1);
    private static final SmoothBumpInterpolator SCALE_BUMP = new SmoothBumpInterpolator(1.6f, 2.4f);
    private final LyricLine lyricLine;
    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private final float phraseRaiseY = dp(2);
    private final Duration fadeAt;
    private final List<LyricLine.Phrase> phrases;
    private final LyricHighlightCalculator highlightCalculator;
    @Getter
    private HighlightStatus status = HighlightStatus.WAITING;
    private Duration statusUpdateTime = Duration.ZERO;
    private boolean statusUpdateProcessing = false;
    @Setter
    private Runnable onFade;


    public LyricHighlightTextView(Context context, LyricLine line) {
        super(context);
        line.parsePhrases();
        lyricLine = line;

        List<LyricLine.Phrase> phrases1 = lyricLine.getPhrases();
        phrases = phrases1;
        highlightCalculator = new LyricHighlightCalculator(lyricLine);

        Duration fadeAt1 = line.getStartTime().plus(line.getDuration()).minus(500, ChronoUnit.MILLIS);
        if (line.isWordByWord() && phrases1 != null && !phrases1.isEmpty()) {
            Duration lastPhraseEndAt = phrases1.getLast().endTime();
            if (lastPhraseEndAt.compareTo(fadeAt1) > 0) {
                fadeAt1 = lastPhraseEndAt;
            }
        }
        fadeAt = fadeAt1;

        setText(line.getSpannableString());
        getPaint().setLinearText(true);
    }

    public void emphasize() {
        setStatus(HighlightStatus.PERFORMING);
    }

    /**
     * Whether this line can still change on its own, i.e. whether the cached offscreen source must be
     * re-rendered. WAITING is static; PERFORMING and a DONE line that is still fading are dynamic.
     */
    public boolean isVisuallyAnimating() {
        if (status == HighlightStatus.PERFORMING) {
            return true;
        }
        if (status == HighlightStatus.DONE) {
            if (statusUpdateProcessing) {
                return true;
            }
            if (phrases != null && !phrases.isEmpty()) {
                long elapsed = nowPlayingInfo.getPlayedDuration().minus(fadeAt).toMillis();
                return elapsed < RAISE_ANIMATION_DURATION;
            }
        }
        return false;
    }

    /** True when this line contains at least one long-note phrase eligible for bloom. */
    public boolean hasDurablePhrase() {
        if (!lyricLine.isWordByWord() || phrases == null || phrases.isEmpty()) {
            return false;
        }
        for (LyricLine.Phrase phrase : phrases) {
            if (isDurablePhrase(phrase)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDurablePhrase(LyricLine.Phrase phrase) {
        return phrase.durationMillis() > LyricLine.DURABLE_PHRASE_MILLIS
                && phrase.charCount() > 1;
    }

    private void setStatus(HighlightStatus status) {
        this.status = status;
        statusUpdateTime = nowPlayingInfo.getPlayedDuration();
        statusUpdateProcessing = true;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        TextPaint textPaint = getPaint();
        if (status == HighlightStatus.WAITING) {
            super.setTextColor(Theme.FADE_LYRIC_COLOR);
            textPaint.setShader(null);
            super.onDraw(canvas);
            return;
        }

        Duration playedDuration = nowPlayingInfo.getPlayedDuration();

        if (status == HighlightStatus.DONE) {
            if (statusUpdateProcessing) {
                float fraction = (float) getMillisBetween(playedDuration, statusUpdateTime) / ANIMATION_DURATION_MILLIS;
                if (0 < fraction && fraction < 1) {
                    super.setTextColor(ColorEvaluator.evaluate(fraction, Theme.EMPHASIZE_LYRIC_COLOR, Theme.FADE_LYRIC_COLOR));
                } else if (fraction >= 1) {
                    super.setTextColor(Theme.FADE_LYRIC_COLOR);
                    statusUpdateProcessing = false;
                } else {
                    super.setTextColor(Theme.EMPHASIZE_LYRIC_COLOR);
                }
                textPaint.setShader(null);
            }
            if (phrases != null) {
                phrases.forEach(phrase -> lowerPhrase(phrase, fadeAt, playedDuration));
            }
            super.onDraw(canvas);
            return;
        }

        // PERFORMING 状态
        Layout layout = getLayout();
        if (layout == null) return;

        long range = layout.getLineRangeForDraw(canvas);
        if (range < 0) return;

        if (!lyricLine.isWordByWord()) {
            if (statusUpdateProcessing) {
                float fraction = (float) (getMillisBetween(playedDuration, statusUpdateTime) - FULL_LINE_HIGHLIGHT_DELAY) / ANIMATION_DURATION_MILLIS;
                if (0 <= fraction && fraction < 1) {
                    setTextColor(ColorEvaluator.evaluate(fraction, Color.toArgb(getCurrentTextColor()), Theme.EMPHASIZE_LYRIC_COLOR));
                } else if (fraction >= 1) {
                    setTextColor(Theme.EMPHASIZE_LYRIC_COLOR);
                    statusUpdateProcessing = false;
                }
            }
            super.onDraw(canvas);
            if (playedDuration.compareTo(fadeAt) >= 0) {
                setStatus(HighlightStatus.DONE);
                if (onFade != null) {
                    onFade.run();
                }
            }
            return;
        }

        if (statusUpdateProcessing) {
            statusUpdateProcessing = false;
        }

        LyricHighlightCalculator.SweepState sweepState = highlightCalculator.compute(playedDuration);
        if (sweepState == null) {
            super.setTextColor(Theme.EMPHASIZE_LYRIC_COLOR);
            textPaint.setShader(null);
            super.onDraw(canvas);
            return;
        }
        int phraseIndex = lyricLine.binarySearchPhraseIndex(playedDuration);
        LyricLine.Phrase currentPhrase = phraseIndex < phrases.size() ? phrases.get(phraseIndex) : null;

        if (playedDuration.compareTo(fadeAt) >= 0) {
            super.setTextColor(Theme.EMPHASIZE_LYRIC_COLOR);
            textPaint.setShader(null);
            super.onDraw(canvas);

            phrases.forEach(phrase -> lowerPhrase(phrase, fadeAt, playedDuration));
            setStatus(HighlightStatus.DONE);
            if (onFade != null) {
                onFade.run();
            }
            return;
        }

        for (int i = 0; i < phrases.size() && i <= phraseIndex; i++) {
            LyricLine.Phrase phrase = phrases.get(i);
            Duration animStart = i == 0 ? lyricLine.getStartTime() : phrases.get(i - 1).endTime();
            boolean isCurrent = i == phraseIndex;
            if (isCurrent || playedDuration.minus(animStart).toMillis() < RAISE_ANIMATION_DURATION) {
                raisePhrase(phrase, animStart, playedDuration);
            } else {
                phrase.spans().forEach(span -> {
                    span.setYOffset(-phraseRaiseY);
                    span.setScale(1f);
                });
            }
        }

        if (currentPhrase == null) {
            super.setTextColor(Theme.EMPHASIZE_LYRIC_COLOR);
            textPaint.setShader(null);
            super.onDraw(canvas);
            return;
        }

        int textLength = layout.getText().length();

        int lineCount = layout.getLineCount();
        float[] lineLogicalStart = new float[lineCount];
        float cumulative = 0;
        for (int i = 0; i < lineCount; i++) {
            lineLogicalStart[i] = cumulative;
            cumulative += layout.getLineWidth(i);
        }

        int dp18 = dp(18);
        int dp36 = dp18 * 2;
        float offset = Math.clamp(sweepState.offset(), 0f, textLength);
        float gradientPointLogicalX = logicalXAt(layout, lineLogicalStart, offset)
                - dp18
                - dp18 * sweepState.leadWeight()
                + dp36 * sweepState.trailWeight();
        float gradientLeftLogical = gradientPointLogicalX - dp18;
        float gradientRightLogical = gradientPointLogicalX + dp36;

        int firstLine = (int) (range >>> 32);
        int lastLine = (int) (range & 0xFFFFFFFFL);
        for (int line = firstLine; line <= lastLine; line++) {
            float lineLogicalLeft = lineLogicalStart[line];
            float lineLogicalRight = lineLogicalStart[line] + layout.getLineWidth(line);
            float lineActualLeft = layout.getLineLeft(line);
            float lineActualRight = layout.getLineRight(line);

            if (lineLogicalRight <= gradientLeftLogical) {
                textPaint.setColor(Theme.EMPHASIZE_LYRIC_COLOR);
                textPaint.setShader(null);
            } else if (lineLogicalLeft >= gradientRightLogical) {
                textPaint.setColor(Theme.FADE_LYRIC_COLOR);
                textPaint.setShader(null);
            } else {
                float t1 = (gradientLeftLogical - lineLogicalLeft) / (lineLogicalRight - lineLogicalLeft);
                float t2 = (gradientPointLogicalX - lineLogicalLeft) / (lineLogicalRight - lineLogicalLeft);
                float t3 = (gradientRightLogical - lineLogicalLeft) / (lineLogicalRight - lineLogicalLeft);

                int[] colors = {Theme.EMPHASIZE_LYRIC_COLOR, Theme.GLOW_LYRIC_COLOR, Theme.FADE_LYRIC_COLOR};
                float[] positions = {t1, t2, t3};
                textPaint.setShader(new LinearGradient(lineActualLeft, 0, lineActualRight, 0,
                        colors, positions, Shader.TileMode.CLAMP, null));
                textPaint.setColor(Theme.GLOW_LYRIC_COLOR);
            }
            layout.drawText(canvas, line, line);
        }
    }

    /**
     * Draws only the durable-phrase bloom mask. The base SDR line is intentionally not drawn:
     * the mask is blurred and composited as a glow, while the visible text stays sharp.
     */
    public void drawBloomMask(@NonNull Canvas canvas) {
        if (status != HighlightStatus.PERFORMING) {
            return;
        }
        Layout layout = getLayout();
        List<LyricLine.Phrase> phrases = this.phrases;
        if (layout == null || phrases == null || phrases.isEmpty()) {
            return;
        }
        Duration playedDuration = nowPlayingInfo.getPlayedDuration();
        if (playedDuration.compareTo(fadeAt) >= 0) {
            return;
        }
        LyricHighlightCalculator.SweepState sweepState = highlightCalculator.compute(playedDuration);
        if (sweepState == null) {
            return;
        }
        int phraseIndex = lyricLine.binarySearchPhraseIndex(playedDuration);
        updatePhraseRaise(playedDuration, phraseIndex);

        CharSequence text = layout.getText();
        int textLength = text.length();
        int lineCount = layout.getLineCount();
        float[] lineLogicalStart = new float[lineCount];
        float cumulative = 0;
        for (int i = 0; i < lineCount; i++) {
            lineLogicalStart[i] = cumulative;
            cumulative += layout.getLineWidth(i);
        }

        int dp18 = dp(18);
        int dp36 = dp18 * 2;
        float offset = Math.clamp(sweepState.offset(), 0f, textLength);
        float gradientPointLogicalX = logicalXAt(layout, lineLogicalStart, offset)
                - dp18
                - dp18 * sweepState.leadWeight()
                + dp36 * sweepState.trailWeight();
        float gradientRightLogical = gradientPointLogicalX + dp36;

        TextPaint textPaint = getPaint();

        canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        canvas.translate(getCompoundPaddingLeft(), getExtendedPaddingTop());

        long range = layout.getLineRangeForDraw(canvas);
        if (range < 0) {
            canvas.restore();
            return;
        }
        int firstLine = (int) (range >>> 32);
        int lastLine = (int) (range & 0xFFFFFFFFL);

        for (int i = 0; i < phrases.size() && i <= phraseIndex; i++) {
            LyricLine.Phrase phrase = phrases.get(i);
            if (!isDurablePhrase(phrase)) {
                continue;
            }
            int phraseStartOffset = i == 0 ? 0 : phrases.get(i - 1).endOffset();
            int phraseEndOffset = Math.min(phrase.endOffset(), textLength);
            if (phraseEndOffset <= phraseStartOffset) {
                continue;
            }
            float durability = Math.clamp(
                    (phrase.durationMillis() - LyricLine.DURABLE_PHRASE_MILLIS)
                            / (float) (LyricLine.FULL_DURABLE_PHRASE_MILLIS
                            - LyricLine.DURABLE_PHRASE_MILLIS),
                    0f, 1f);
            float bloomGain = 0.65f + 0.35f * durability;
            long phraseStartMillis = (i == 0 ? lyricLine.getStartTime()
                    : phrases.get(i - 1).endTime()).toMillis();
            long elapsedMillis = playedDuration.toMillis() - phraseStartMillis;
            int durationMillis = Math.max(1, phrase.durationMillis());
            int totalChars = Math.max(1,
                    Character.codePointCount(text, phraseStartOffset, phraseEndOffset));
            for (int line = firstLine; line <= lastLine; line++) {
                int lineStartOffset = layout.getLineStart(line);
                int lineEndOffset = layout.getLineEnd(line);
                int segmentStart = Math.max(phraseStartOffset, lineStartOffset);
                int segmentEnd = Math.min(phraseEndOffset, lineEndOffset);
                if (segmentEnd <= segmentStart) {
                    continue;
                }

                float lineLogicalLeft = lineLogicalStart[line];
                float lineLogicalRight = lineLogicalLeft + layout.getLineWidth(line);
                if (lineLogicalRight <= lineLogicalLeft) {
                    continue;
                }
                float segmentStartX = logicalXAtOffset(layout, lineLogicalStart, segmentStart);
                float segmentEndX = logicalXAtOffset(layout, lineLogicalStart, segmentEnd);
                float left = Math.max(segmentStartX, lineLogicalLeft);
                float right = Math.min(Math.min(segmentEndX, gradientRightLogical), lineLogicalRight);
                if (right <= left) {
                    continue;
                }

                float lineActualLeft = layout.getLineLeft(line);
                float lineActualRight = layout.getLineRight(line);
                float rampPx = Math.min(dp(12), (right - left) * 0.5f);
                if (rampPx <= 0.5f) {
                    rampPx = 0.5f;
                }
                float lineSpan = lineLogicalRight - lineLogicalLeft;
                float tLeft = Math.clamp((left - lineLogicalLeft) / lineSpan, 0f, 1f);
                float tRight = Math.clamp((right - lineLogicalLeft) / lineSpan, 0f, 1f);
                float tRamp = rampPx / lineSpan;
                boolean startsAtPhrase = segmentStart <= phraseStartOffset;
                boolean endsHere = segmentEnd >= phraseEndOffset
                        || gradientRightLogical < segmentEndX;
                float tLeftEnd = startsAtPhrase ? Math.min(tRight, tLeft + tRamp) : tLeft;
                float tRightEnd = endsHere ? Math.min(1f, tRight + tRamp) : tRight;

                int segmentStartIndex = Character.codePointCount(text, phraseStartOffset, segmentStart);
                int segmentEndIndex = Math.max(segmentStartIndex,
                        Character.codePointCount(text, phraseStartOffset, segmentEnd) - 1);
                float firstAlpha = bloomEnvelope(elapsedMillis, durationMillis,
                        segmentStartIndex, totalChars) * bloomGain;
                float lastAlpha = bloomEnvelope(elapsedMillis, durationMillis,
                        segmentEndIndex, totalChars) * bloomGain;
                if (firstAlpha <= 0.001f && lastAlpha <= 0.001f) {
                    continue;
                }

                List<Float> stopPositions = new ArrayList<>(4);
                List<Float> stopColors = new ArrayList<>(16);
                if (startsAtPhrase) {
                    addBloomStop(stopPositions, stopColors, tLeft, 0f);
                    addBloomStop(stopPositions, stopColors, tLeftEnd, firstAlpha);
                } else {
                    addBloomStop(stopPositions, stopColors, tLeft, firstAlpha);
                }
                addBloomStop(stopPositions, stopColors, tRight, lastAlpha);
                if (endsHere) {
                    addBloomStop(stopPositions, stopColors, tRightEnd, 0f);
                }

                float[] positions = new float[stopPositions.size()];
                for (int p = 0; p < positions.length; p++) {
                    positions[p] = stopPositions.get(p);
                }
                float[] colors = new float[stopColors.size()];
                for (int c = 0; c < colors.length; c++) {
                    colors[c] = stopColors.get(c);
                }
                textPaint.setShader(new LinearGradient(lineActualLeft, 0, lineActualRight, 0,
                        colors, ColorSpaces.SRGB, positions, Shader.TileMode.CLAMP, null));
                textPaint.setColor(Color.WHITE);
                textPaint.setAlphaF(1f);
                layout.drawText(canvas, line, line);
            }
        }

        textPaint.setShader(null);
        textPaint.setColor(getCurrentTextColor());
        canvas.restore();
    }

    private static void addBloomStop(List<Float> positions, List<Float> colors,
                                     float position, float alpha) {
        positions.add(Math.clamp(position, 0f, 1f));
        colors.add(1f);
        colors.add(1f);
        colors.add(1f);
        colors.add(Math.clamp(alpha, 0f, 1f));
    }

    /**
     * Per-character bloom envelope over the phrase lifetime: 0.3t fade in, 0.2t hold, 0.5t
     * fade out. Characters are delayed linearly across the phrase like the raise stagger.
     */
    private static float bloomEnvelope(long elapsedMillis, int durationMillis,
                                       int charIndex, int charCount) {
        float t = Math.max(1f, durationMillis);
        float delay = charCount > 1
                ? (charIndex / (float) (charCount - 1)) * BLOOM_STAGGER_RATE * t
                : 0f;
        float available = Math.max(1f, t - delay);
        float u = (elapsedMillis - delay) / available;
        if (u <= 0f || u >= 1f) {
            return 0f;
        }
        if (u < BLOOM_FADE_IN) {
            float x = u / BLOOM_FADE_IN;
            return x * x * (3f - 2f * x);
        }
        float fadeOutStart = 1f - BLOOM_FADE_OUT;
        if (u < fadeOutStart) {
            return 1f;
        }
        float x = (u - fadeOutStart) / BLOOM_FADE_OUT;
        return 1f - x * x * (3f - 2f * x);
    }

    private void updatePhraseRaise(Duration playedDuration, int phraseIndex) {
        for (int i = 0; i < phrases.size() && i <= phraseIndex; i++) {
            LyricLine.Phrase phrase = phrases.get(i);
            Duration animStart = i == 0 ? lyricLine.getStartTime() : phrases.get(i - 1).endTime();
            boolean isCurrent = i == phraseIndex;
            if (isCurrent || playedDuration.minus(animStart).toMillis() < RAISE_ANIMATION_DURATION) {
                raisePhrase(phrase, animStart, playedDuration);
            } else {
                phrase.spans().forEach(span -> {
                    span.setYOffset(-phraseRaiseY);
                    span.setScale(1f);
                });
            }
        }
    }

    private static float logicalXAt(Layout layout, float[] lineLogicalStart, float offset) {
        int textLength = layout.getText().length();
        int floor = Math.clamp((int) Math.floor(offset), 0, textLength);
        int ceil = Math.min(textLength, floor + 1);
        float from = logicalXAtOffset(layout, lineLogicalStart, floor);
        if (ceil == floor) {
            return from;
        }
        float to = logicalXAtOffset(layout, lineLogicalStart, ceil);
        return from + (offset - floor) * (to - from);
    }

    private static float logicalXAtOffset(Layout layout, float[] lineLogicalStart, int offset) {
        int line = layout.getLineForOffset(offset);
        return lineLogicalStart[line] + layout.getPrimaryHorizontal(offset);
    }

    private void raisePhrase(LyricLine.Phrase phrase, Duration startAt, Duration now) {
        long startAtMillis = startAt.toMillis();
        long nowMillis = now.toMillis();
        long totalDuration = RAISE_ANIMATION_DURATION;
        long progressMillis = nowMillis - startAtMillis;

        List<LyricLine.HighlightSpan> spans = phrase.spans();
        if (spans.isEmpty()) return;
        int charCount = phrase.charCount();
        // Non-durable phrases (single span covering the whole phrase) animate as one unit;
        // durable phrases stagger per char across their word-level spans.
        if (phrase.durationMillis() <= LyricLine.DURABLE_PHRASE_MILLIS || charCount <= 1) {
            LyricLine.HighlightSpan span = spans.getFirst();
            float t = Math.clamp((float) progressMillis / totalDuration, 0, 1);
            span.setYOffset(-phraseRaiseY * SPRING.getInterpolation(t));
        } else {
            float staggerRate = 0.2f; // stagger ratio
            // Raise uses the fixed RAISE_ANIMATION_DURATION.
            long staggerDuration = (long) (totalDuration * staggerRate);
            long animDuration = totalDuration - staggerDuration;
            // Scale keeps the original phrase-duration-based waveform.
            long phraseDuration = phrase.durationMillis();
            long scaleStaggerDuration = (long) (phraseDuration * staggerRate);
            long scaleDuration = phraseDuration - scaleStaggerDuration;
            // Peak of the smooth bump is 1 (the old symmetric parabola peaked at 0.5), so the
            // amplitude is halved to keep the same maximum scale.
            float scaleAmplitude = 0.125f * Math.min(phraseDuration, LyricLine.FULL_DURABLE_PHRASE_MILLIS)
                    / LyricLine.FULL_DURABLE_PHRASE_MILLIS;

            int globalIndex = 0;
            for (LyricLine.HighlightSpan span : spans) {
                for (int j = 0; j < span.getCharCount(); j++, globalIndex++) {
                    // the last char gets the full stagger
                    double staggerFraction = (globalIndex == charCount - 1)
                            ? 1.0
                            : (double) globalIndex / (charCount - 1);
                    long raiseStart = startAtMillis + (long) (staggerFraction * staggerDuration);
                    long scaleStart = startAtMillis + (long) (staggerFraction * scaleStaggerDuration);
                    float tRaise = Math.clamp((float) (nowMillis - raiseStart) / animDuration, 0, 1);
                    float tScale = Math.clamp((float) (nowMillis - scaleStart) / scaleDuration, 0, 1);
                    span.setCharState(j,
                            -phraseRaiseY * SPRING.getInterpolation(tRaise),
                            1 + scaleAmplitude * SCALE_BUMP.getInterpolation(tScale));
                }
            }
        }
    }

    private void lowerPhrase(LyricLine.Phrase phrase, Duration startAt, Duration now) {
        long startAtMillis = startAt.toMillis();
        long nowMillis = now.toMillis();
        float t = Math.clamp((float) (nowMillis - startAtMillis) / RAISE_ANIMATION_DURATION, 0, 1);
        float yOffset = -phraseRaiseY * (1 - SPRING.getInterpolation(t));

        phrase.spans().forEach(span -> span.setYOffset(yOffset));
    }

    private long getMillisBetween(Duration duration1, Duration duration2) {
        return duration1.minus(duration2).toMillis();
    }

    public enum HighlightStatus {WAITING, PERFORMING, DONE}
}