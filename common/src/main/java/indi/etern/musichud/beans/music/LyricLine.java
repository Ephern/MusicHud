package indi.etern.musichud.beans.music;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.text.FontMetricsInt;
import icyllis.modernui.graphics.text.ShapedText;
import icyllis.modernui.text.*;
import icyllis.modernui.text.style.ReplacementSpan;
import lombok.*;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.*;

@Getter
@Setter
@ToString
@AllArgsConstructor
@Builder
public class LyricLine implements Comparable<LyricLine> {
    public static final int DURABLE_PHRASE_MILLIS = 1000;
    public static final int FULL_DURABLE_PHRASE_MILLIS = 1500;

    @Builder.Default
    private boolean phraseParsed = false;
    @Builder.Default
    Type type = Type.NORMAL;
    Duration startTime;
    Duration duration;
    String text;
    String translatedText;
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    LyricLine previous;
    @Setter
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    LyricLine next;
    Map<Duration, Integer> phraseEndingOffsetMap;
    Map<Duration, Phrase> phraseEndDurationMap;
    List<Phrase> phrases;
    @Builder.Default
    boolean wordByWord = false;
    SpannableString spannableString;

    public Map<Duration, Integer> getPhraseEndingOffsetMap() {
        if (phraseEndingOffsetMap == null) {
            phraseEndingOffsetMap = new LinkedHashMap<>(0);
        }
        return phraseEndingOffsetMap;
    }

    @Override
    public int compareTo(@NotNull LyricLine o) {
        return startTime.compareTo(o.startTime);
    }

    public boolean isAfter(@NotNull LyricLine o) {
        return compareTo(o) > 0;
    }

    public String getTranslatedText() {
        return Objects.requireNonNullElse(translatedText, "");
    }

    public String getText() {
        return Objects.requireNonNullElse(text, "");
    }

    public Duration getStartTime() {
        return Objects.requireNonNullElse(startTime, Duration.ZERO);
    }

    public void parsePhrases() {
        if (!phraseParsed) {
            forceParsePhrases();
        }
    }
    public void forceParsePhrases() {
        phraseParsed = true;
        spannableString = new SpannableString(text);
        if (wordByWord && phraseEndingOffsetMap != null) {
            int size = phraseEndingOffsetMap.size();
            phraseEndDurationMap = new LinkedHashMap<>(size);
            phrases = new ArrayList<>(size);
            int lastPhraseEnd = 0;
            Duration lastEndTime = startTime;
            //LinkedHashMap
            for (Map.Entry<Duration, Integer> entry : phraseEndingOffsetMap.entrySet()) {
                Integer phraseEnd = entry.getValue();
                Duration endTime = entry.getKey();
                List<LyricLine.HighlightSpan> spans = new ArrayList<>(1);
                int durationMillis = Math.toIntExact(endTime.minus(lastEndTime).toMillis());
                if (durationMillis > DURABLE_PHRASE_MILLIS) {
                    for (int i = lastPhraseEnd; i < phraseEnd; i++) {
                        spans.add(applySpan(spannableString, i, i + 1));
                    }
                } else {
                    spans.add(applySpan(spannableString, lastPhraseEnd, phraseEnd));
                }
                lastPhraseEnd = phraseEnd;
                LyricLine.Phrase phrase = new LyricLine.Phrase(entry.getValue(), endTime, durationMillis, spans);
                phraseEndDurationMap.put(endTime, phrase);
                phrases.add(phrase);
                lastEndTime = endTime;
            }
        }
    }

    private static LyricLine.HighlightSpan applySpan(SpannableString spannableString, int start, int end) {
        LyricLine.HighlightSpan span = new LyricLine.HighlightSpan(0);
        spannableString.setSpan(
                span,
                start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return span;
    }

    // 二分查找第一个结束时间 > playedDuration 的短语索引
    public int binarySearchPhraseIndex(Duration playedDuration) {
        List<Phrase> phrases = getPhrases();
        int low = 0, high = phrases.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (phrases.get(mid).getEndTime().compareTo(playedDuration) <= 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low; // 返回第一个大于 playedDuration 的索引，可能等于 size()
    }

    public enum Type {
        NORMAL, META_DATA, RHYTHM
    }

    @ToString
    @EqualsAndHashCode
    @Getter
    public static final class Phrase {
        private final int endOffset;
        private final Duration endTime;
        private final int durationMillis;
        private final List<HighlightSpan> spans;

        public Phrase(int endOffset, Duration endTime, int durationMillis, List<HighlightSpan> spans) {
            this.endOffset = endOffset;
            this.endTime = endTime;
            this.durationMillis = durationMillis;
            this.spans = spans;
        }
    }

    @ToString
    @Getter
    @Setter
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