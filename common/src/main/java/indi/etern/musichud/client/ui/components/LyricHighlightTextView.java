package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.ColorEvaluator;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.graphics.LinearGradient;
import icyllis.modernui.graphics.Shader;
import icyllis.modernui.text.Layout;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.client.ui.dto.LyricLine;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.utils.ui.Easing;
import lombok.NonNull;
import lombok.Setter;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;

public class LyricHighlightTextView extends TextView {
    private static final int fullLineHighlightDelay = 300;
    private static final int animationDurationMillis = 300;
    private final LyricLine lyricLine;
    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private final float phraseRaiseY = dp(1) * 1.5f;
    private final Duration fadeAt;
    private final List<LyricLine.Phrase> phrases;
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

        Duration fadeAt1 = line.getStartTime().plus(line.getDuration()).minus(500, ChronoUnit.MILLIS);
        if (line.isWordByWord() && phrases1 != null) {
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
                float fraction = (float) getMillisBetween(playedDuration, statusUpdateTime) / animationDurationMillis;
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
                phrases.forEach(phrase -> lowerPhrase(phrase, fadeAt, fadeAt.plusMillis(animationDurationMillis), playedDuration));
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
                float fraction = (float) (getMillisBetween(playedDuration, statusUpdateTime) - fullLineHighlightDelay) / animationDurationMillis;
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

        int phraseIndex = lyricLine.binarySearchPhraseIndex(playedDuration);
        if (phraseIndex < 0) phraseIndex = 0;
        Duration phraseStart, phraseEnd;
        LyricLine.Phrase currentPhrase;
        if (phraseIndex == 0) {
            phraseStart = lyricLine.getStartTime();
            currentPhrase = phrases.getFirst();
        } else if (phraseIndex >= phrases.size()) {
            phraseStart = null;
            currentPhrase = null;
        } else {
            phraseStart = phrases.get(phraseIndex - 1).endTime();
            currentPhrase = phrases.get(phraseIndex);
        }
        for (int i = 0; i < phraseIndex; i++) {
            List<LyricLine.HighlightSpan> spans = phrases.get(i).spans();
            for (LyricLine.HighlightSpan span : spans) {
                span.setYOffset(-phraseRaiseY);
            }
        }
        if (playedDuration.compareTo(fadeAt) >= 0) {
            super.setTextColor(Theme.EMPHASIZE_LYRIC_COLOR);
            textPaint.setShader(null);
            super.onDraw(canvas);

            phrases.forEach(phrase -> lowerPhrase(phrase, fadeAt, fadeAt.plusMillis(animationDurationMillis), playedDuration));
            setStatus(HighlightStatus.DONE);
            if (onFade != null) {
                onFade.run();
            }
            return;
        }

        phraseEnd = currentPhrase == null ? null : currentPhrase.endTime();
        if (currentPhrase != null) {
            raisePhrase(currentPhrase, phraseStart, phraseEnd, playedDuration);
        }

        long phraseDurationMillis = currentPhrase == null ? -1 : phraseEnd.minus(phraseStart).toMillis();
        if (phraseDurationMillis <= 0) {
            super.setTextColor(Theme.EMPHASIZE_LYRIC_COLOR);
            textPaint.setShader(null);
            super.onDraw(canvas);
            return;
        }

        long playedInPhrase = playedDuration.minus(phraseStart).toMillis();
        LyricLine.Phrase lastPhrase = lyricLine.getPhraseEndDurationMap().get(phraseStart);
        int textLength = layout.getText().length();
        int startOffset = Math.min(textLength, lastPhrase == null ? 0 : lastPhrase.endOffset());
        int endOffset = Math.min(textLength, currentPhrase.endOffset());

        int lineCount = layout.getLineCount();
        float[] lineLogicalStart = new float[lineCount];
        float cumulative = 0;
        for (int i = 0; i < lineCount; i++) {
            lineLogicalStart[i] = cumulative;
            cumulative += layout.getLineWidth(i);
        }

        Function<Integer, Float> getLogicalX = offset -> {
            int line = layout.getLineForOffset(offset);
            float lineStartX = lineLogicalStart[line];
            float charXInLine = layout.getPrimaryHorizontal(offset);
            return lineStartX + charXInLine;
        };

        float startLogicalX = getLogicalX.apply(startOffset);
        float endLogicalX = getLogicalX.apply(endOffset);
        int dp18 = dp(18);
        int dp36 = dp18 * 2;
        int additionalSpaceForLast = phraseIndex == phrases.size() - 1 ? dp36 : 0;
        float gradientPointLogicalX = startLogicalX + (endLogicalX + additionalSpaceForLast - startLogicalX) * playedInPhrase / phraseDurationMillis - dp18;
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

    private void raisePhrase(LyricLine.Phrase phrase, Duration startAt, Duration endAt, Duration now) {
        long startAtMillis = startAt.toMillis();
        long endAtMillis = endAt.toMillis();
        long nowMillis = now.toMillis();
        long totalDuration = endAtMillis - startAtMillis;
        long progressMillis = nowMillis - startAtMillis;
        if (totalDuration <= 0) return;

        int spanCount = phrase.spans().size();
        if (spanCount == 1) {
            LyricLine.HighlightSpan span = phrase.spans().getFirst();
            float t = Math.clamp((float) progressMillis / totalDuration, 0, 1);
            span.setYOffset(-phraseRaiseY * Easing.EASE_IN_OUT_QUAD.getInterpolation(t));
        } else {
            float staggerRate = 0.2f; // 错开比例，最后一个比第一个晚 totalDuration * staggerRate 毫秒
            long staggerDuration = (long) (totalDuration * staggerRate); // 错开总时长
            long animDuration = totalDuration - staggerDuration; // 每个 span 的动画时长
            if (animDuration <= 0) animDuration = 1;

            for (int i = 0; i < spanCount; i++) {
                LyricLine.HighlightSpan span = phrase.spans().get(i);
                // 错开偏移量：i / (spanCount-1) * staggerDuration，最后一个偏移 staggerDuration
                long delay = (i == spanCount - 1) ? staggerDuration : (long) ((double) i / (spanCount - 1) * staggerDuration);
                long animStart = startAtMillis + delay;
                if (nowMillis <= animStart) {
                    span.setYOffset(0);
                    span.setScale(1);
                } else if (nowMillis >= animStart + animDuration) {
                    span.setYOffset(-phraseRaiseY);
                    span.setScale(1);
                } else {
                    float t = (float) (nowMillis - animStart) / animDuration;
                    span.setYOffset(-phraseRaiseY * Easing.EASE_IN_OUT_QUAD.getInterpolation(t));
                    span.setScale(1 + 0.35f * Math.min(phrase.durationMillis(), LyricLine.FULL_DURABLE_PHRASE_MILLIS) / LyricLine.FULL_DURABLE_PHRASE_MILLIS * quadratic(t));
                }
            }
        }
    }

    private float quadratic(float f) {
        return -f * (f - 1);
    }

    private void lowerPhrase(LyricLine.Phrase phrase, Duration startAt, Duration endAt, Duration now) {
        long startAtMillis = startAt.toMillis();
        long endAtMillis = endAt.toMillis();
        long nowMillis = now.toMillis();
        float t = Math.clamp((float) (nowMillis - startAtMillis) / (endAtMillis - startAtMillis), 0, 1);
        float yOffset = -phraseRaiseY * Easing.EASE_OUT_QUAD.getInterpolation(t);

        phrase.spans().forEach(span -> span.setYOffset(yOffset));
    }

    private long getMillisBetween(Duration duration1, Duration duration2) {
        return duration1.minus(duration2).toMillis();
    }

    // 动画过渡相关
    public enum HighlightStatus {WAITING, PERFORMING, DONE}
}