package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.ColorEvaluator;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.LinearGradient;
import icyllis.modernui.graphics.Shader;
import icyllis.modernui.graphics.text.FontMetricsInt;
import icyllis.modernui.graphics.text.ShapedText;
import icyllis.modernui.text.*;
import icyllis.modernui.text.style.ReplacementSpan;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.beans.music.LyricLine;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.utils.Easings;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class LyricHighlightTextView extends TextView {
    private static final int fullLineHighlightDelay = 300;
    private static final int fullLineFadeDelay = -200;
    private static final int animationDurationMillis = 300;
    private static final int durablePhraseMillis = 1000;
    private static final int fullDurablePhraseMillis = 1500;
    private static final Duration animationDuration = Duration.ofMillis(animationDurationMillis);
    private final List<Phrase> phrases;
    private final boolean fullLineMode;
    private final Map<Duration, Phrase> phraseMap;
    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private final Duration lineStart;
    private final Duration lineEnd;
    private final float phraseRaiseY = dp(1) * 1.5f;
    private HighlightStatus status = HighlightStatus.WAITING;
    private Duration statusUpdateTime = Duration.ZERO;
    private boolean statusUpdateProcessing = false;
    @Setter
    private Runnable onFade;

    public LyricHighlightTextView(Context context, LyricLine line) {
        super(context);
        lineStart = line.getStartTime();
        Duration lineDuration = line.getDuration();
        if (lineDuration == null) {
            lineEnd = nowPlayingInfo.getMusicDuration().minus(lineStart);
        } else {
            lineEnd = line.getStartTime().plus(lineDuration).minus(animationDuration);
        }
        Map<Duration, Integer> phraseEndingMap = line.getPhraseEndingMap();
        int size = phraseEndingMap.size();
        fullLineMode = phraseEndingMap.isEmpty();
        phraseMap = new LinkedHashMap<>(size);
        phrases = new ArrayList<>(size);
        String text = line.getText();

        SpannableString spannableString = new SpannableString(text);
        if (!fullLineMode) {
            int lastPhraseEnd = 0;
            Duration lastEndTime = lineStart;
            //LinkedHashMap
            for (Map.Entry<Duration, Integer> entry : phraseEndingMap.entrySet()) {
                Integer phraseEnd = entry.getValue();
                Duration endTime = entry.getKey();
                List<HighlightSpan> spans = new ArrayList<>(1);
                int durationMillis = Math.toIntExact(endTime.minus(lastEndTime).toMillis());
                if (durationMillis > durablePhraseMillis) {
                    for (int i = lastPhraseEnd; i < phraseEnd; i++) {
                        spans.add(applySpan(spannableString, i, i + 1));
                    }
                } else {
                    spans.add(applySpan(spannableString, lastPhraseEnd, phraseEnd));
                }
                lastPhraseEnd = phraseEnd;
                Phrase phrase = new Phrase(entry.getValue(), endTime, durationMillis, spans);
                phraseMap.put(endTime, phrase);
                phrases.add(phrase);
                lastEndTime = endTime;
            }
        }
        setText(spannableString);
        getPaint().setLinearText(true);
    }

    private static HighlightSpan applySpan(SpannableString spannableString, int start, int end) {
        HighlightSpan span = new HighlightSpan(0);
        spannableString.setSpan(
                span,
                start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return span;
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

        Duration fadeAt = lineEnd.plusMillis(fullLineFadeDelay);
        if (fullLineMode) {
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
                phrases.forEach(phrase -> lowerPhrase(phrase, fadeAt, fadeAt.plusMillis(animationDurationMillis), playedDuration));//TODO Animation
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

        // 使用二分查找定位当前短语索引
        int phraseIndex = binarySearchPhraseIndex(playedDuration);
        if (phraseIndex < 0) phraseIndex = 0;
        Duration phraseStart, phraseEnd;
        Phrase currentPhrase;
        if (phraseIndex == 0) {
            phraseStart = lineStart;
            phraseEnd = phrases.getFirst().endTime;
            currentPhrase = phrases.get(phraseIndex);
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
            phraseStart = phrases.get(phraseIndex - 1).endTime;
            currentPhrase = phrases.get(phraseIndex);
            phraseEnd = currentPhrase.endTime;
        }
        for (int i = 0; i < phraseIndex; i++) {
            List<HighlightSpan> spans = phrases.get(i).spans;
            for (HighlightSpan span : spans) {
                span.yOffset = -phraseRaiseY;
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
        Phrase lastPhrase = phraseMap.get(phraseStart);
        int startOffset = lastPhrase == null ? 0 : lastPhrase.endOffset;
        int endOffset = currentPhrase.endOffset;

        int textLength = layout.getText().length();
        startOffset = Math.min(textLength, startOffset);
        endOffset = Math.min(textLength, endOffset);

        // 2. 预计算每行的逻辑起始坐标（累积宽度）
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
        float gradientRightLogical = gradientPointLogicalX + dp(12);

        // 3. 遍历可见行，设置样式
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

    private void raisePhrase(Phrase phrase, Duration startAt, Duration endAt, Duration now) {
        long startAtMillis = startAt.toMillis();
        long endAtMillis = endAt.toMillis();
        long nowMillis = now.toMillis();
        long totalDuration = endAtMillis - startAtMillis;
        long progressMillis = nowMillis - startAtMillis;
        if (totalDuration <= 0) return;

        int spanCount = phrase.spans.size();
        if (spanCount == 1) {
            HighlightSpan span = phrase.spans.getFirst();
            float t = Math.clamp((float) progressMillis / totalDuration, 0, 1);
            span.yOffset = -phraseRaiseY * Easings.EASE_OUT_QUAD.getInterpolation(t);
        } else {
            float staggerRate = 0.3f; // 错开比例，最后一个比第一个晚 totalDuration * staggerRate 毫秒
            long staggerDuration = (long) (totalDuration * staggerRate); // 错开总时长
            long animDuration = totalDuration - staggerDuration; // 每个 span 的动画时长
            if (animDuration <= 0) animDuration = 1;

            for (int i = 0; i < spanCount; i++) {
                HighlightSpan span = phrase.spans.get(i);
                // 错开偏移量：i / (spanCount-1) * staggerDuration，最后一个偏移 staggerDuration
                long delay = (i == spanCount - 1) ? staggerDuration : (long) ((double) i / (spanCount - 1) * staggerDuration);
                long animStart = startAtMillis + delay;
                if (nowMillis <= animStart) {
                    span.yOffset = 0;
                    span.scale = 1;
                } else if (nowMillis >= animStart + animDuration) {
                    span.yOffset = -phraseRaiseY;
                    span.scale = 1;
                } else {
                    float t = (float) (nowMillis - animStart) / animDuration;
                    span.yOffset = -phraseRaiseY * Easings.EASE_OUT_QUAD.getInterpolation(t);
                    span.scale = 1 + 0.3f * Math.min(phrase.durationMillis , fullDurablePhraseMillis) / fullDurablePhraseMillis * quadratic(t);
                }
            }
        }
    }

    private float quadratic(float f) {
        return -f * (f - 1);
    }

    private void lowerPhrase(Phrase phrase, Duration startAt, Duration endAt, Duration now) {
        long startAtMillis = startAt.toMillis();
        long endAtMillis = endAt.toMillis();
        long nowMillis = now.toMillis();
        float t = Math.clamp((float) (nowMillis - startAtMillis) / (endAtMillis - startAtMillis), 0, 1);
        float yOffset = -phraseRaiseY * Easings.EASE_OUT_QUAD.getInterpolation(t);

        phrase.spans.forEach(span -> span.yOffset = yOffset);
    }

    private long getMillisBetween(Duration duration1, Duration duration2) {
        return duration1.minus(duration2).toMillis();
    }

    // 二分查找第一个结束时间 > playedDuration 的短语索引
    private int binarySearchPhraseIndex(Duration playedDuration) {
        int low = 0, high = phrases.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (phrases.get(mid).endTime.compareTo(playedDuration) <= 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low; // 返回第一个大于 playedDuration 的索引，可能等于 size()
    }

    // 动画过渡相关
    public enum HighlightStatus {WAITING, PERFORMING, DONE}

    @ToString
    @EqualsAndHashCode
    static final class Phrase {
        private final Integer endOffset;
        private final Duration endTime;
        private final int durationMillis;
        private final List<HighlightSpan> spans;

        Phrase(Integer endOffset, Duration endTime, int durationMillis, List<HighlightSpan> spans) {
            this.endOffset = endOffset;
            this.endTime = endTime;
            this.durationMillis = durationMillis;
            this.spans = spans;
        }
    }

    public static class HighlightSpan extends ReplacementSpan {
        float yOffset;
        float scale = 1;
        ShapedText cachedShapedText;
        int cachedWidth;
        int textHeight;

        public HighlightSpan(float yOffset) {
            this.yOffset = yOffset;
        }

        @Override
        public int getSize(@Nonnull TextPaint paint, CharSequence text,
                           int start, int end, @Nullable FontMetricsInt fm) {
            if (cachedShapedText == null) {
                String subText = text.subSequence(start, end).toString();
                cachedShapedText = TextShaper.shapeText(
                        subText, 0, subText.length(),
                        TextDirectionHeuristics.FIRSTSTRONG_LTR, paint
                );
                cachedWidth = Math.round(cachedShapedText.getAdvance());
                textHeight = cachedShapedText.getDescent() - cachedShapedText.getAscent();
            }
            if (fm != null) {
                paint.getFontMetricsInt(fm);
            }
            return cachedWidth;
        }

        @Override
        public void draw(@Nonnull Canvas canvas, CharSequence text,
                         int start, int end, float x, int top, int y, int bottom,
                         @Nonnull TextPaint paint) {
            canvas.save();
            canvas.scale(scale, scale, x + 0.5f * cachedWidth, y + 0.5f * textHeight);
            canvas.drawShapedText(cachedShapedText, x, y + yOffset, paint);
            canvas.restore();
        }
    }
}