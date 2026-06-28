package indi.etern.musichud.beans.music;

import lombok.*;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.*;

@Getter
@Setter
@ToString
@AllArgsConstructor
@Builder
public class LyricLine implements Comparable<LyricLine> {
    public static final int DURABLE_PHRASE_MILLIS = 1000;
    public static final int FULL_DURABLE_PHRASE_MILLIS = 1200;
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
    @Builder.Default
    private boolean phraseParsed = false;

    private static LyricLine.HighlightSpan createSpan() {
        return new LyricLine.HighlightSpan(0);
    }

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
                        if (i + 1 <= getText().length()) {
                            spans.add(createSpan());
                        }
                    }
                } else {
                    if (phraseEnd <= getText().length()) {
                        spans.add(createSpan());
                    }
                }
                lastPhraseEnd = phraseEnd;
                LyricLine.Phrase phrase = new LyricLine.Phrase(entry.getValue(), endTime, durationMillis, spans);
                phraseEndDurationMap.put(endTime, phrase);
                phrases.add(phrase);
                lastEndTime = endTime;
            }
        }
    }

    // 二分查找第一个结束时间 > playedDuration 的短语索引
    public int binarySearchPhraseIndex(Duration playedDuration) {
        List<Phrase> phrases = getPhrases();
        int low = 0, high = phrases.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (phrases.get(mid).endTime().compareTo(playedDuration) <= 0) {
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

    public record Phrase(int endOffset, Duration endTime, int durationMillis, List<HighlightSpan> spans) {
    }

    @ToString
    @Getter
    @Setter
    public static class HighlightSpan {
        float yOffset;
        float scale = 1;

        public HighlightSpan(float yOffset) {
            this.yOffset = yOffset;
        }
    }
}
