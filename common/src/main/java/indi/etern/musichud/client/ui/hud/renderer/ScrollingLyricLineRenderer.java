package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.render.*;
import indi.etern.musichud.client.ui.render.pipeline.*;
import indi.etern.musichud.client.ui.render.text.*;

import icyllis.modernui.mc.FontResourceManager;
import icyllis.modernui.mc.text.ModernStringSplitter;
import icyllis.modernui.mc.text.TextLayoutEngine;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.dto.LyricLine;
import indi.etern.musichud.client.ui.render.SceneStyle;
import indi.etern.musichud.client.ui.render.text.GradientSpec;
import indi.etern.musichud.client.ui.lyric.LyricHighlightCalculator;
import indi.etern.musichud.client.utils.ui.Easing;
import indi.etern.musichud.client.utils.ui.SpringValue;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;

public class ScrollingLyricLineRenderer implements RenderLayer {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private static final int LYRICS_ANIMATION_DURATION = 300;
    private static final float BASE_RESPONSE_SECONDS = (float) LYRICS_ANIMATION_DURATION / 1000f;
    // Very short lines collapse the switch to a near-instant slide instead of dropping the line.
    private static final float MIN_RESPONSE_SECONDS = 0.05f;
    private static final float DURATION_RESPONSE_FACTOR = 0.75f;
    private static final float SWITCH_DAMPING = 1f;
    // Gradient highlight smoothing band half width, in unscaled font pixels; it scales with the
    // text and stays within a few pixels of the classic hard scissor sweep.
    private static final float GRADIENT_SMOOTH_PX = 4f;
    // Horizontal edge mask for overflowing lines: ramp width in units of the line height, and the
    // cut amount (gui px) over which an edge mask fades in so it never pops on.
    private static final float EDGE_FADE_WIDTH = 0.9f;
    private static final float EDGE_FADE_RAMP_PX = 6f;
    private final List<LinePair> pairs = new ArrayList<>();
    private final ConcurrentLinkedQueue<PendingLines> pendingLines = new ConcurrentLinkedQueue<>();
    ModernStringSplitter modernStringSplitter;
    @Setter
    private float line1Height;
    @Setter
    private float line2Height;
    @Setter
    private SceneStyle hudStyle;
    private int cachedContainerWidth;
    @Setter
    private int lineSpacing = 0;

    public ScrollingLyricLineRenderer() {
        FontResourceManager fontResourceManager = FontResourceManager.getInstance();
        Logger logger = MusicHud.getLogger(ScrollingLyricLineRenderer.class);
        if (fontResourceManager instanceof TextLayoutEngine layoutEngine) {
            try {
                modernStringSplitter = layoutEngine.getStringSplitter();
            } catch (Throwable t) {
                logger.debug("ModernTextEngine is disabled", t);
            }
        } else {
            logger.debug("ModernTextEngine is disabled");
        }
    }

    public void clear() {
        pendingLines.clear();
        setLines(
                new Line(null, "", 0, 0, 0),
                new Line(null, "", 0, 0, 0),
                true
        );
    }

    /**
     * 设置双行文本及其样式
     * <p>
     * May be called from a worker thread: the request is queued and applied on the render
     * thread (see {@link #applyPendingLines(long)}). Every request is kept in order, so rapid
     * switches never overwrite (and thus never drop) a line; the renderer decides how to animate.
     *
     * @param line1   第一行的文本和颜色
     * @param line2   第二行的文本和颜色
     * @param animate whether to start the vertical switch spring
     */
    public void setLines(Line line1, Line line2, boolean animate) {
        int width = cachedContainerWidth;
        float maxScroll1 = computeMaxScrollOffset(line1.text(), line1Height, width);
        float maxScroll2 = computeMaxScrollOffset(line2.text(), line2Height, width);
        pendingLines.add(new PendingLines(line1, line2, animate, maxScroll1, maxScroll2));
    }

    private void applyPendingLines(long nowNanos) {
        // One per frame: if the producer ever enqueues faster than we render, each line still gets a
        // frame instead of being overwritten.
        PendingLines pending = pendingLines.poll();
        if (pending == null) {
            return;
        }

        Line line1 = pending.line1();
        Line line2 = pending.line2();
        float height = hudStyle.getHeight();

        // Non-animated update (e.g. clear): drop every running pair and snap to the new lines.
        if (!pending.animate()) {
            pairs.clear();
            LinePair pair = new LinePair();
            applyPair(pair, line1, line2, pending.maxScroll1(), pending.maxScroll2());
            pair.offset.jumpTo(0f);
            startScrollingIfPossible(pair);
            return;
        }

        LinePair newest = pairs.isEmpty() ? null : pairs.getLast();
        if (newest != null && line1.equals(newest.line1.line) && line2.equals(newest.line2.line)) {
            return; // already showing the active pair
        }

        // Strict single line: keep only the pair currently on screen and discard any older outgoing
        // pair, so at most one leaving + one entering pair exist. The kept pair keeps its current
        // value and velocity (setTarget), so the hand-off has no jump.
        if (pairs.size() > 1) {
            pairs.subList(0, pairs.size() - 1).clear();
        }
        for (LinePair pair : pairs) {
            pair.requestLeave(nowNanos, height);
        }

        LinePair pair = new LinePair();
        applyPair(pair, line1, line2, pending.maxScroll1(), pending.maxScroll2());
        pair.offset.set(height, 0f, 0f, nowNanos); // enter from below, target rest
        pairs.add(pair);
    }

    private void applyPair(LinePair pair, Line line1, Line line2, float maxScroll1, float maxScroll2) {
        pair.line1.reset(line1);
        pair.line2.reset(line2);
        applyScrollMetrics(pair.line1, maxScroll1);
        applyScrollMetrics(pair.line2, maxScroll2);
        // Per-line switch speed: short lines switch fast so the slide finishes before the next line.
        pair.offset.setResponse(computeSwitchResponse(line1));
    }

    private float computeSwitchResponse(Line line) {
        LyricLine lyricLine = line.lyricLine();
        if (lyricLine == null || lyricLine.getDuration() == null) {
            return BASE_RESPONSE_SECONDS;
        }
        float seconds = lyricLine.getDuration().toMillis() / 1000f;
        return Math.clamp(seconds * DURATION_RESPONSE_FACTOR, MIN_RESPONSE_SECONDS, BASE_RESPONSE_SECONDS);
    }

    private void startScrollingIfPossible(LinePair pair) {
        if (pair.scrollStarted || cachedContainerWidth <= 0) {
            return;
        }
        pair.scrollStarted = true;
        startScrollingIfNeeded(pair.line1, cachedContainerWidth);
        startScrollingIfNeeded(pair.line2, cachedContainerWidth);
    }

    private void applyScrollMetrics(LineState line, float maxScrollOffset) {
        line.maxScrollOffset = maxScrollOffset;
        line.needScroll = maxScrollOffset < 0f;
    }

    private float computeMaxScrollOffset(String text, float lineHeight, int containerWidth) {
        if (text == null || text.isEmpty() || containerWidth <= 0) {
            return 0f;
        }
        float textWidth = calcTextWidth(text, lineHeight);
        return textWidth > containerWidth ? -(textWidth - containerWidth) : 0f;
    }

    private void startScrollingIfNeeded(LineState line, int containerWidth) {
        if (line.line == null) return;
        if (line.needScroll && containerWidth > 0) {
            line.isScrolling = true;
            line.scrollStartTime = System.currentTimeMillis();
            line.scrollOffset = 0;
            line.scrollTarget = line.maxScrollOffset;
            if (line.line.scrollMs <= 0) {
                line.isScrolling = false;
                line.scrollOffset = line.scrollTarget;
            }
        } else {
            line.isScrolling = false;
            line.scrollOffset = 0;
        }
    }

    private void updateScrolling(LineState line, long now) {
        if (!line.isScrolling) return;
        if (line.line.scrollMs <= 0) {
            line.isScrolling = false;
            line.scrollOffset = line.scrollTarget;
            return;
        }
        long elapsed = now - line.scrollStartTime;
        if (elapsed >= line.line.scrollMs) {
            line.isScrolling = false;
            line.scrollOffset = line.scrollTarget;
        } else {
            float progress = Easing.EASE_IN_OUT_SINE.getInterpolation((float) elapsed / line.line.scrollMs);
            line.scrollOffset = line.scrollTarget * progress;
        }
    }

    private float calcTextWidth(String text, float lineHeight) {
        if (text == null || text.isEmpty()) return 0;
        float rawWidth;
        Font font = Minecraft.getInstance().font;
        if (modernStringSplitter != null) {
            try {
                rawWidth = modernStringSplitter.stringWidth(text);
            } catch (Throwable e) {
                modernStringSplitter = null;//fallback;
                rawWidth = submitVanillaCalcWidth(text, font);
            }
        } else {
            rawWidth = submitVanillaCalcWidth(text, font);
        }
        return rawWidth * lineHeight / font.lineHeight;
    }

    private float submitVanillaCalcWidth(String text, Font font) {
        try {
            return Minecraft.getInstance().submit(() -> font.width(text)).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateAnimations() {
        long now = System.currentTimeMillis();
        long nowNanos = System.nanoTime();

        applyPendingLines(nowNanos);

        // Every pair owns its own offset spring: retargeting preserves velocity, so the loop just
        // advances each spring and retires the ones that have finished sliding off the top.
        for (Iterator<LinePair> it = pairs.iterator(); it.hasNext(); ) {
            LinePair pair = it.next();
            pair.offset.update(nowNanos);

            if (pair.leaving) {
                if (pair.offset.isSettled()) {
                    it.remove();
                }
                continue;
            }

            // Reached rest: start the horizontal scroll animation for the active line.
            if (!pair.scrollStarted && pair.offset.isSettled()) {
                startScrollingIfPossible(pair);
            }
            updateScrolling(pair.line1, now);
            updateScrolling(pair.line2, now);
        }
    }

    public void render(SceneRenderContext context) {
        if (hudStyle == null) {
            return;
        }

        // 计算实际布局绝对坐标和尺寸
        SceneStyle.AbsolutePosition absPos = hudStyle.calcAbsolutePosition(context);
        // 布局缓存（每次渲染时更新）
        int cachedContainerX = (int) absPos.x();
        int cachedContainerY = (int) absPos.y();
        cachedContainerWidth = (int) hudStyle.getWidth();
        int cachedContainerHeight = (int) hudStyle.getHeight();

        if (cachedContainerWidth <= 0 || cachedContainerHeight <= 0) return;

        updateAnimations();

        int totalHeight = (int) (line1Height + line2Height);
        int startY = cachedContainerY + (cachedContainerHeight - totalHeight) / 2; // 垂直居中

        float x = absPos.x();
        float y = absPos.y();
        context.pushScissor((int) x, (int) y, (int) (x + hudStyle.getWidth()), (int) (y + hudStyle.getHeight()));
        for (LinePair pair : pairs) {
            float yOffset = pair.offset.getValue();
            Line line1 = pair.line1.line;
            if (line1 == null || line1.lyricLine == null) {
                continue;
            }
            if (line1.lyricLine.isWordByWord()) {
                renderLineHighlight(context, pair.line1, line1.emphasizeColor, line1.fadeColor, cachedContainerX, startY, line1Height, yOffset);
            } else {
                renderLine(context, pair.line1, line1.emphasizeColor, cachedContainerX, startY, line1Height, yOffset);
            }
            if (clientConfig.getShowTranslatedCnLyrics()) {
                Line line2 = pair.line2.line;
                if (line2 != null && line2.lyricLine != null) {
                    renderLine(context, pair.line2, line2.fadeColor, cachedContainerX, (int) (startY + lineSpacing + line1Height), line2Height, yOffset);
                }
            }
        }
        context.popScissor();
    }

    private float calcHighlightWidth(LineState lineState, float lineHeight) {
        Line line = lineState.line;
        if (line == null) return 0;
        String text = line.text;
        LyricLine currentLyricLine = line.lyricLine;
        if (currentLyricLine == null) {
            return 0;
        }
        float textWidth = calcTextWidth(text, lineHeight);
        if (!currentLyricLine.isWordByWord()) {
            return textWidth;
        }
        LyricHighlightCalculator calculator = lineState.highlightCalculator;
        if (calculator == null) {
            return textWidth;
        }
        LyricHighlightCalculator.SweepState sweep =
                calculator.compute(nowPlayingInfo.getPlayedDuration());
        if (sweep == null) {
            return textWidth;
        }
        return calcTextWidthAt(text, Math.clamp(sweep.offset(), 0f, text.length()), lineHeight);
    }

    private float calcTextWidthAt(String text, float offset, float lineHeight) {
        int textLength = text.length();
        int floor = Math.clamp((int) Math.floor(offset), 0, textLength);
        float from = floor <= 0 ? 0 : calcTextWidth(text.substring(0, floor), lineHeight);
        if (floor >= textLength) {
            return from;
        }
        float to = calcTextWidth(text.substring(0, floor + 1), lineHeight);
        return from + (offset - floor) * (to - from);
    }

    private void renderLine(SceneRenderContext context, LineState line, int color, int baseX, int baseY, float lineHeight, float yOffset) {
        if (line.line == null) return;
        String text = line.line.text;
        if (text.isEmpty()) return;

        float scale = lineHeight / Minecraft.getInstance().font.lineHeight;
        if (scale <= 0) return;

        float scrollOffset = line.scrollOffset;

        float drawX = baseX + scrollOffset;
        float drawY = baseY + yOffset;
        int drawColor = applyStyleAlpha(color);
        TextEdgeFade fade = edgeFade(line, lineHeight, scale, drawX, baseX);

        context.transform()
                .translate(drawX, drawY)
                .scale(scale)
                .end(transforming ->
                        context.drawGradientString(Minecraft.getInstance().font, text, 0, 0,
                                GradientSpec.flat(drawColor), 1f, fade)
                );
    }

    /**
     * Horizontal edge mask for one line: an edge is masked only while the run actually crosses that
     * box edge, and the ramp width follows the cut amount, so a line that just starts to overflow
     * dissolves in smoothly instead of flipping a hard mask on.
     */
    private TextEdgeFade edgeFade(LineState line, float lineHeight, float scale, float drawGuiX, float boxLeft) {
        float overhang = -line.maxScrollOffset;
        if (overhang <= 0.5f || scale <= 0f) {
            return TextEdgeFade.NONE;
        }
        float boxWidth = hudStyle.getWidth();
        float cutLeft = Math.max(0f, boxLeft - drawGuiX);
        float cutRight = Math.max(0f, overhang - cutLeft);
        float baseBand = Math.min(lineHeight * EDGE_FADE_WIDTH, boxWidth * 0.4f);
        return TextEdgeFade.between(boxLeft, boxLeft + boxWidth,
                Math.min(baseBand, cutLeft), Math.min(baseBand, cutRight),
                TextEdgeFade.smoothstep(cutLeft / EDGE_FADE_RAMP_PX),
                TextEdgeFade.smoothstep(cutRight / EDGE_FADE_RAMP_PX),
                drawGuiX, scale);
    }

    /**
     * Highlight sweep as a per-vertex gradient: the already sung part keeps
     * {@code sungColor} and the rest fades to {@code pendingColor} across a small smoothing
     * band around the current character offset, so the transition runs inside a single glyph.
     * <p>
     * The window center travels {@code 2 * band} beyond the text span, letting the window slide
     * completely off both ends: at sweep 0 the whole line is pending and at sweep 1 the whole
     * line is sung, both reached continuously instead of snapping at the ends.
     */
    private void renderLineHighlight(SceneRenderContext context, LineState line, int sungColor, int pendingColor,
                                     int baseX, int baseY, float lineHeight, float yOffset) {
        if (line.line == null) return;
        String text = line.line.text;
        if (text.isEmpty()) return;

        float scale = lineHeight / Minecraft.getInstance().font.lineHeight;
        if (scale <= 0) return;

        float scrollOffset = line.scrollOffset;
        float drawX = baseX + scrollOffset;
        float drawY = baseY + yOffset;

        // both widths are in the same (scaled) space, so the ratio is scale independent
        float totalWidth = calcTextWidth(text, lineHeight);
        float sweep = totalWidth > 0
                ? Math.clamp(calcHighlightWidth(line, lineHeight) / totalWidth, 0f, 1f)
                : 1f;
        // band is normalized against the unscaled width so it scales with the text
        float rawWidth = calcTextWidth(text, Minecraft.getInstance().font.lineHeight);
        float band = rawWidth > 0 ? Math.clamp(GRADIENT_SMOOTH_PX / rawWidth, 0.002f, 0.5f) : 0.002f;

        int sung = applyStyleAlpha(sungColor);
        int pending = applyStyleAlpha(pendingColor);
        // overshoot: the window center travels 2*band beyond the text span, so the window can
        // slide fully off either end (sweep 0 -> all pending, sweep 1 -> all sung) without a jump
        float center = sweep * (1f + 2f * band) - band;
        GradientSpec spec = GradientSpec.window(sung, pending, center, band);
        TextEdgeFade fade = edgeFade(line, lineHeight, scale, drawX, baseX);

        context.transform()
                .translate(drawX, drawY)
                .scale(scale)
                .end(transforming ->
                        context.drawGradientString(Minecraft.getInstance().font, text, 0, 0, spec, 1f, fade)
                );
    }

    private int applyStyleAlpha(int color) {
        int alpha = (int) (((color >>> 24) & 0xFF) * hudStyle.calcAbsoluteAlpha());
        return (Math.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private record PendingLines(Line line1, Line line2, boolean animate, float maxScroll1, float maxScroll2) {
    }

    /**
     * One on-screen lyric line-pair (original + translated) and its own vertical offset spring.
     * Mirrors StaggeredLyricScrollView's RowWave: the offset is a pixel value in its own
     * {@link SpringValue}, so it can be retargeted at any time while preserving value/velocity.
     */
    private static class LinePair {
        final LineState line1 = new LineState();
        final LineState line2 = new LineState();
        final SpringValue offset = new SpringValue(BASE_RESPONSE_SECONDS, SWITCH_DAMPING);
        boolean leaving;
        boolean scrollStarted;

        /**
         * Retarget upward toward {@code -height}; keeps current value and velocity.
         */
        void requestLeave(long nowNanos, float height) {
            if (leaving) {
                return;
            }
            leaving = true;
            offset.setTarget(-height, nowNanos);
        }
    }

    private static class LineState {
        Line line;
        boolean needScroll;
        float maxScrollOffset;
        boolean isScrolling;
        long scrollStartTime;
        float scrollTarget;
        float scrollOffset;
        LyricHighlightCalculator highlightCalculator;

        void reset(@Nullable Line line) {
            this.line = line;
            this.needScroll = false;
            this.maxScrollOffset = 0;
            this.isScrolling = false;
            this.scrollStartTime = 0;
            this.scrollTarget = 0;
            this.scrollOffset = 0;
            this.highlightCalculator = line == null || line.lyricLine() == null
                    ? null
                    : new LyricHighlightCalculator(line.lyricLine());
        }
    }

    public record Line(LyricLine lyricLine, String text, int fadeColor, int emphasizeColor, long scrollMs) {
    }
}
