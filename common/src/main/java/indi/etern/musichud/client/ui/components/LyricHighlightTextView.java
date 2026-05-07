package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.ColorEvaluator;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.LinearGradient;
import icyllis.modernui.graphics.Shader;
import icyllis.modernui.text.Layout;
import icyllis.modernui.text.TextPaint;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.beans.music.LyricLine;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.utils.Easings;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

@Slf4j
public class LyricHighlightTextView extends TextView {
    private static final int fullLineHighlightDelay = 300;
    private static final int fullLineFadeDelay = -200;
    private static final int animationDurationMillis = 300;
    private final LyricLine lyricLine;
    private static final Duration animationDuration = Duration.ofMillis(animationDurationMillis);
    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private final Duration lineEndAnimationCallTime;
    private final float phraseRaiseY = dp(1) * 1.5f;
    private HighlightStatus status = HighlightStatus.WAITING;
    private Duration statusUpdateTime = Duration.ZERO;
    private boolean statusUpdateProcessing = false;
    @Setter
    private Runnable onFade;

    public LyricHighlightTextView(Context context, LyricLine line) {
        super(context);
        line.parsePhrases();
        lyricLine = line;

        Duration lineDuration = line.getDuration();
        lineEndAnimationCallTime = line.getStartTime().plus(lineDuration).minus(animationDuration);

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
    protected void onDraw(@Nonnull Canvas canvas) {
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
            super.onDraw(canvas);
            return;
        }

        // PERFORMING 状态
        Layout layout = getLayout();
        if (layout == null) return;

        long range = layout.getLineRangeForDraw(canvas);
        if (range < 0) return;

        Duration fadeAt = lineEndAnimationCallTime.plusMillis(fullLineFadeDelay);
        List<LyricLine.Phrase> phrases = lyricLine.getPhrases();
        if (!lyricLine.isWordByWord()) {
            if (statusUpdateProcessing) {
                float fraction = (float) (getMillisBetween(playedDuration, statusUpdateTime) - fullLineHighlightDelay) / animationDurationMillis;
                if (0 <= fraction && fraction < 1) {
                    setTextColor(ColorEvaluator.evaluate(fraction, getTextColors().getDefaultColor(), Theme.EMPHASIZE_LYRIC_COLOR));
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

        // 非 fullLineMode，处理渐变高亮
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
            phraseEnd = currentPhrase.endTime();
        } else if (phraseIndex >= phrases.size()) {
            // 已经超过最后一个短语，整行显示强调色（已唱完）
            super.setTextColor(Theme.EMPHASIZE_LYRIC_COLOR);
            textPaint.setShader(null);
            super.onDraw(canvas);
            phrases.forEach(phrase -> lowerPhrase(phrase, fadeAt, fadeAt.plusMillis(animationDurationMillis), playedDuration));
            setStatus(HighlightStatus.DONE);
            if (onFade != null) {
                onFade.run();
            }
            return;
        } else {
            phraseStart = phrases.get(phraseIndex - 1).endTime();
            currentPhrase = phrases.get(phraseIndex);
            phraseEnd = currentPhrase.endTime();
        }
        for (int i = 0; i < phraseIndex; i++) {
            List<LyricLine.HighlightSpan> spans = phrases.get(i).spans();
            for (LyricLine.HighlightSpan span : spans) {
                span.setYOffset(-phraseRaiseY);
            }
        }
        raisePhrase(currentPhrase, phraseStart, phraseEnd, playedDuration);

        long phraseDurationMillis = phraseEnd.minus(phraseStart).toMillis();
        if (phraseDurationMillis <= 0) {
            // 无效短语，直接绘制强调色
            textPaint.setColor(Theme.EMPHASIZE_LYRIC_COLOR);
            textPaint.setShader(null);
            super.onDraw(canvas);
            return;
        }

        long playedInPhrase = Math.min(playedDuration.minus(phraseStart).toMillis(), phraseDurationMillis);
        LyricLine.Phrase lastPhrase = lyricLine.getPhraseEndDurationMap().get(phraseStart);
        int startOffset = lastPhrase == null ? 0 : lastPhrase.endOffset();
        int endOffset = currentPhrase.endOffset();

        int textLength = layout.getText().length();
        startOffset = Math.min(textLength, startOffset);
        endOffset = Math.min(textLength, endOffset);

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
        float gradientPointLogicalX = startLogicalX + (endLogicalX - startLogicalX) * playedInPhrase / phraseDurationMillis;
        float gradientLeftLogical = gradientPointLogicalX - dp(16);
        float gradientRightLogical = gradientPointLogicalX + dp(36);

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
            span.setYOffset(-phraseRaiseY * Easings.EASE_IN_OUT_QUAD.getInterpolation(t));
        } else {
            float staggerRate = 0.3f; // 错开比例，最后一个比第一个晚 totalDuration * staggerRate 毫秒
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
                    span.setYOffset(-phraseRaiseY * Easings.EASE_IN_OUT_QUAD.getInterpolation(t));
                    span.setScale(1 + 0.3f * Math.min(phrase.durationMillis(), LyricLine.FULL_DURABLE_PHRASE_MILLIS) / LyricLine.FULL_DURABLE_PHRASE_MILLIS * quadratic(t));
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
        float yOffset = -phraseRaiseY * Easings.EASE_OUT_QUAD.getInterpolation(t);

        phrase.spans().forEach(span -> span.setYOffset(yOffset));
    }

    private long getMillisBetween(Duration duration1, Duration duration2) {
        return duration1.minus(duration2).toMillis();
    }

    // 动画过渡相关
    public enum HighlightStatus {WAITING, PERFORMING, DONE}
}