package indi.etern.musichud.mixin;

import icyllis.modernui.text.SpanSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Arrays;

@SuppressWarnings("UnstableApiUsage")
@Pseudo
@Mixin(value = SpanSet.class, remap = false)
public abstract class SpanSetMixin {

    @Shadow(remap = false)
    public int[] spanStarts;

    @Shadow(remap = false)
    public int[] spanEnds;

    @Shadow(remap = false)
    public int[] spanFlags;

    /**
     * @author Etern
     * @reason Fixes which allocates new arrays without copying old data,
     * causing all previously stored span boundaries to be lost on reallocation.
     * This bug manifests when a SpannableString has 11+ MetricAffectingSpans (e.g. word-level
     * HighlightSpans in word-by-word lyric lines, one per word, plus other spans), which
     * exceeds the default buffer size of 10.
     */
    @Overwrite(remap = false)
    private void grow(int length) {
        if (spanStarts == null) {
            length = Math.max(length, 10);
        } else if (spanStarts.length < length) {
            length = Math.max(length, spanStarts.length + (spanStarts.length >> 1));
        } else {
            length = 0;
        }
        if (length > 0) {
            spanStarts = spanStarts == null ? new int[length] : Arrays.copyOf(spanStarts, length);
            spanEnds = spanEnds == null ? new int[length] : Arrays.copyOf(spanEnds, length);
            spanFlags = spanFlags == null ? new int[length] : Arrays.copyOf(spanFlags, length);
        }
    }
}
