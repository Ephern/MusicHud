package indi.etern.musichud.client.ui.dto;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Matrix;
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

    private static HighlightSpan applySpan(SpannableString spannableString, int start, int end, int charStartInPhrase) {
        HighlightSpan span = new HighlightSpan(charStartInPhrase, Character.codePointCount(spannableString, start, end));
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
            int textLength = spannableString.length();
            //LinkedHashMap
            for (Map.Entry<Duration, Integer> entry : phraseEndingOffsetMap.entrySet()) {
                Integer phraseEnd = entry.getValue();
                Duration endTime = entry.getKey();
                List<HighlightSpan> spans = new ArrayList<>(1);
                int durationMillis = Math.toIntExact(endTime.minus(lastEndTime).toMillis());

                if (durationMillis > DURABLE_PHRASE_MILLIS) {
                    // Split the phrase into word-level spans: each word becomes one atomic
                    // replacement run for the line breaker, so wrapping never happens
                    // mid-word; per-char animation is handled inside the span's draw.
                    // Whitespace is left unspanned to preserve word-boundary break points.
                    int spanEnd = Math.min(phraseEnd, textLength);
                    int wordStart = -1;
                    for (int i = lastPhraseEnd; i <= spanEnd; i++) {
                        boolean boundary = i >= spanEnd
                                || Character.isWhitespace(spannableString.charAt(i));
                        if (!boundary && wordStart < 0) {
                            wordStart = i;
                        } else if (boundary && wordStart >= 0) {
                            spans.add(applySpan(spannableString, wordStart, i,
                                    Character.codePointCount(spannableString, lastPhraseEnd, wordStart)));
                            wordStart = -1;
                        }
                    }
                } else {
                    if (phraseEnd <= textLength) {
                        spans.add(applySpan(spannableString, lastPhraseEnd, phraseEnd, 0));
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

        public int charCount() {
            int total = 0;
            for (HighlightSpan span : spans) {
                total += span.getCharCount();
            }
            return total;
        }
    }

    @ToString
    public static class HighlightSpan extends ReplacementSpan {
        // Near-identity projective matrix (persp0 = 1e-8): makes the position matrix
        // report hasPerspective() so arc3d skips the direct-mask glyph path (which floors
        // every glyph to whole device pixels) and uses the transformed-mask path, which
        // keeps fractional positions and samples the atlas with bilinear filtering.
        // The epsilon must live in m14 (persp0), NOT m44 (persp2): Device normalizes the
        // CTM via Matrix.normalizePerspective(), which divides m44 back to exactly 1
        // whenever m14/m24 are zero, silently undoing an m44-based flag. With m14 != 0
        // the normalization is skipped entirely. The resulting w-distortion
        // (~1e-8 * scale^2 * x) is far below one physical pixel.
        private static final Matrix SUBPIXEL_MATRIX = new Matrix(
                1f, 0f, 1e-8f,
                0f, 1f, 0f,
                0f, 0f, 1f
        );
        // Index of the first char (code point) of this span within its phrase, used by the
        // view to compute per-char stagger delays for the karaoke raise animation.
        @Getter
        private final int charStartInPhrase;
        // Per-char (code point) animation state
        private final float[] charYOffsets;
        private final float[] charScales;
        // Lazily shaped per-char glyphs. The cache window is established by the first
        // getSize call, which covers the full span range (MeasuredParagraph), and each
        // slot is shaped independently (same kerning behavior as per-char spans).
        private ShapedText[] charShapedTexts;
        private int[] charWidths;
        private int[] charCodeUnitLengths;
        private int cacheStart;
        private int cacheEnd;

        public HighlightSpan(int charStartInPhrase, int charCount) {
            this.charStartInPhrase = charStartInPhrase;
            this.charYOffsets = new float[charCount];
            this.charScales = new float[charCount];
            Arrays.fill(charScales, 1f);
        }

        public int getCharCount() {
            return charYOffsets.length;
        }

        // Applies the same offset to every char of this span
        public void setYOffset(float yOffset) {
            Arrays.fill(charYOffsets, yOffset);
        }

        // Applies the same scale to every char of this span
        public void setScale(float scale) {
            Arrays.fill(charScales, scale);
        }

        public void setCharState(int index, float yOffset, float scale) {
            charYOffsets[index] = yOffset;
            charScales[index] = scale;
        }

        private void ensureShaped(@NonNull TextPaint paint, CharSequence text,
                                  int start, int end) {
            if (charShapedTexts != null && start >= cacheStart && end <= cacheEnd) {
                return;
            }
            cacheStart = start;
            cacheEnd = end;
            int count = Math.min(Character.codePointCount(text, start, end), charYOffsets.length);
            charShapedTexts = new ShapedText[count];
            charWidths = new int[count];
            charCodeUnitLengths = new int[count];
            int cu = start;
            for (int j = 0; j < count; j++) {
                // cu is always within [start, end) here, so the 2-arg overload is safe
                int cp = Character.codePointAt(text, cu);
                int len = Character.charCount(cp);
                ShapedText shaped = TextShaper.shapeText(
                        text, cu, len,
                        TextDirectionHeuristics.FIRSTSTRONG_LTR, paint
                );
                charShapedTexts[j] = shaped;
                charWidths[j] = Math.round(shaped.getAdvance());
                charCodeUnitLengths[j] = len;
                cu += len;
            }
        }

        @Override
        public int getSize(@NonNull TextPaint paint, CharSequence text,
                           int start, int end, @Nullable FontMetricsInt fm) {
            ensureShaped(paint, text, start, end);
            if (fm != null) {
                paint.getFontMetricsInt(fm);
            }
            int total = 0;
            int cu = cacheStart;
            for (int j = 0; j < charShapedTexts.length; j++) {
                ShapedText shaped = charShapedTexts[j];
                if (shaped == null) {
                    break;
                }
                int len = charCodeUnitLengths[j];
                if (cu + len > start && cu < end) {
                    total += charWidths[j];
                }
                cu += len;
                if (cu >= end) {
                    break;
                }
            }
            return total;
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text,
                         int start, int end, float x, int top, int y, int bottom,
                         @NonNull TextPaint paint) {
            ensureShaped(paint, text, start, end);
            // Per-char draw: the raise offset rides the float draw origin, while scaling
            // pivots on the baseline. The near-identity perspective concat routes glyphs
            // through arc3d's transformed-mask path: the direct-mask path floors every
            // glyph position to whole device pixels (Y has no subpixel bins), which
            // quantizes animation into visible steps; the transformed path keeps
            // fractional positions and samples the glyph atlas with bilinear filtering,
            // giving subpixel-smooth motion.
            canvas.save();
            canvas.concat(SUBPIXEL_MATRIX);
            float cx = x;
            int cu = cacheStart;
            for (int j = 0; j < charShapedTexts.length; j++) {
                ShapedText shaped = charShapedTexts[j];
                if (shaped == null) {
                    break;
                }
                int len = charCodeUnitLengths[j];
                if (cu + len > start && cu < end) {
                    float w = charWidths[j];
                    float s = charScales[j];
                    float yOff = charYOffsets[j];
                    if (s == 1f) {
                        canvas.drawShapedText(shaped, cx, y + yOff, paint);
                    } else {
                        float pivotX = cx + 0.5f * w;
                        canvas.save();
                        canvas.scale(s, s, pivotX, y);
                        canvas.drawShapedText(shaped, cx, y + yOff, paint);
                        canvas.restore();
                    }
                    cx += w;
                }
                cu += len;
                if (cu >= end) {
                    break;
                }
            }
            canvas.restore();
        }
    }
}