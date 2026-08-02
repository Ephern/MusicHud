package indi.etern.musichud.client.ui.dto;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.text.FontMetricsInt;
import icyllis.modernui.graphics.text.ShapedText;
import icyllis.modernui.text.*;
import icyllis.modernui.text.style.ReplacementSpan;
import lombok.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    SpannableString spannableString;
    @Builder.Default
    private boolean phraseParsed = false;

    private static HighlightSpan applySpan(SpannableString spannableString, int start, int end) {
        HighlightSpan span = new HighlightSpan(0);
        spannableString.setSpan(
                span,
                start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        return span;
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
                List<HighlightSpan> spans = new ArrayList<>(1);
                int durationMillis = Math.toIntExact(endTime.minus(lastEndTime).toMillis());

                if (durationMillis > DURABLE_PHRASE_MILLIS) {
                    for (int i = lastPhraseEnd; i < phraseEnd; i++) {
                        if (i + 1 <= spannableString.length()) {
                            spans.add(applySpan(spannableString, i, i + 1));
                        }
                    }
                } else {
                    if (phraseEnd <= spannableString.length()) {
                        spans.add(applySpan(spannableString, lastPhraseEnd, phraseEnd));
                    }
                }
                lastPhraseEnd = phraseEnd;
                Phrase phrase = new Phrase(entry.getValue(), endTime, durationMillis, spans);
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
    public static class HighlightSpan extends ReplacementSpan {
        // Edging value for SkFont::kSubpixelAntiAlias_Edging in native Skia
//        private static final byte SUBPIXEL_EDGING = (byte) 2;
//        private static final VarHandle SHAPED_TEXT_NATIVE_FONT;
//        private static final VarHandle FONT_EDGING;

/*
        static {
            VarHandle nativeFontHandle = null;
            VarHandle edgingHandle = null;
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(ShapedText.class, MethodHandles.lookup());
                nativeFontHandle = lookup.findVarHandle(
                        ShapedText.class, "mNativeFont", icyllis.arc3d.sketch.Font.class);
                lookup = MethodHandles.privateLookupIn(
                        icyllis.arc3d.sketch.Font.class, MethodHandles.lookup());
                edgingHandle = lookup.findVarHandle(
                        icyllis.arc3d.sketch.Font.class, "mEdging", byte.class);
            } catch (Exception ignored) {
            }
            SHAPED_TEXT_NATIVE_FONT = nativeFontHandle;
            FONT_EDGING = edgingHandle;
        }
*/

        // Large Y offset for far-pivot scale trick — encodes yOffset as a near-identity
        // non-uniform Y scale, which Skia renders at sub-pixel precision unlike translate.
        private static final float Y_OFFSET_PIVOT_DISTANCE = 100000.0f;
        float yOffset;
        float scale = 1;
        ShapedText cachedShapedText;
        int cachedWidth;
        int textHeight;

        public HighlightSpan(float yOffset) {
            this.yOffset = yOffset;
        }

        @Override
        public int getSize(@NonNull TextPaint paint, CharSequence text,
                           int start, int end, @Nullable FontMetricsInt fm) {
            if (cachedShapedText == null) {
                String subText = text.subSequence(start, end).toString();
                cachedShapedText = TextShaper.shapeText(
                        subText, 0, subText.length(),
                        TextDirectionHeuristics.FIRSTSTRONG_LTR, paint
                );
                cachedWidth = Math.round(cachedShapedText.getAdvance());
//                float advance = cachedShapedText.getAdvance();
//                MusicHud.LOGGER.info("text = {}, start = {}, end = {}, cachedShapedText.getAdvance() = {}" , text, start, end, advance);
                textHeight = cachedShapedText.getDescent() - cachedShapedText.getAscent();

                // Enable SkFont subpixel anti-aliasing edging via VarHandle.
                // Must happen before getTextBlob() lazily builds the native TextBlob.
/*
                if (SHAPED_TEXT_NATIVE_FONT != null && FONT_EDGING != null) {
                    try {
                        if (SHAPED_TEXT_NATIVE_FONT.get(cachedShapedText) instanceof icyllis.arc3d.sketch.Font nativeFont) {
                            FONT_EDGING.set(nativeFont, SUBPIXEL_EDGING);
                        }
                    } catch (Exception ignored) {
                    }
                }
*/
            }
            if (fm != null) {
                paint.getFontMetricsInt(fm);
            }
            return cachedWidth;
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text,
                         int start, int end, float x, int top, int y, int bottom,
                         @NonNull TextPaint paint) {
            float pivotX = x + 0.5f * cachedWidth;
            canvas.save();
//            canvas.translate(0, yOffset);
            float sy = 1.0f + yOffset / Y_OFFSET_PIVOT_DISTANCE;
            canvas.scale(1.0f, sy, pivotX, y - Y_OFFSET_PIVOT_DISTANCE);
            canvas.scale(scale, scale, pivotX, y + 0.4f * textHeight);
            canvas.drawShapedText(cachedShapedText, x, y, paint);
            canvas.restore();
        }
    }
}