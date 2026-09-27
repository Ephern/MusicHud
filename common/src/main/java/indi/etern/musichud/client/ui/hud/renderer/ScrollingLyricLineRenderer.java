package indi.etern.musichud.client.ui.hud.renderer;

import icyllis.modernui.mc.FontResourceManager;
import icyllis.modernui.mc.text.ModernStringSplitter;
import icyllis.modernui.mc.text.TextLayoutEngine;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.dto.LyricLine;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
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

public class ScrollingLyricLineRenderer implements HudRenderer {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private static final int LYRICS_ANIMATION_DURATION = 300;
    private static final float BASE_RESPONSE_SECONDS = (float) LYRICS_ANIMATION_DURATION / 1000f;
    // Very short lines collapse the switch to a near-instant slide instead of dropping the line.
    private static final float MIN_RESPONSE_SECONDS = 0.05f;
    private static final float DURATION_RESPONSE_FACTOR = 0.75f;
    private static final float SWITCH_DAMPING = 1f;
    private final List<LinePair> pairs = new ArrayList<>();
    private final ConcurrentLinkedQueue<PendingLines> pendingLines = new ConcurrentLinkedQueue<>();
    ModernStringSplitter modernStringSplitter;
    @Setter
    private float line1Height;
    @Setter
    private float line2Height;
    @Setter
    private Layout layout;
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
                false
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
        float height = layout.getHeight();

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

    public void render(HudRenderContext context) {
        if (layout == null) {
            return;
        }

        // 计算实际布局绝对坐标和尺寸
        Layout.AbsolutePosition absPos = layout.calcAbsolutePosition(context);
        // 布局缓存（每次渲染时更新）
        int cachedContainerX = (int) absPos.x();
        int cachedContainerY = (int) absPos.y();
        cachedContainerWidth = (int) layout.getWidth();
        int cachedContainerHeight = (int) layout.getHeight();

        if (cachedContainerWidth <= 0 || cachedContainerHeight <= 0) return;

        updateAnimations();

        int totalHeight = (int) (line1Height + line2Height);
        int startY = cachedContainerY + (cachedContainerHeight - totalHeight) / 2; // 垂直居中
        Layout.AbsolutePosition absolutePosition = layout.calcAbsolutePosition(context);

        float x = absolutePosition.x();
        float y = absolutePosition.y();
        context.pushScissor((int) x, (int) y, (int) (x + layout.getWidth()), (int) (y + layout.getHeight()));
        for (LinePair pair : pairs) {
            float yOffset = pair.offset.getValue();
            Line line1 = pair.line1.line;
            if (line1 == null || line1.lyricLine == null) {
                continue;
            }
            if (line1.lyricLine.isWordByWord()) {
                renderLine(context, pair.line1, line1.fadeColor, cachedContainerX, startY, line1Height, yOffset);
                renderLineHighlight(context, pair.line1, cachedContainerX, startY, line1Height, y, x, calcHighlightWidth(pair.line1, line1Height), yOffset);
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

    private void renderLine(HudRenderContext context, LineState line, int color, int baseX, int baseY, float lineHeight, float yOffset) {
        if (line.line == null) return;
        String text = line.line.text;
        if (text.isEmpty()) return;

        float scale = lineHeight / Minecraft.getInstance().font.lineHeight;
        if (scale <= 0) return;

        float scrollOffset = line.scrollOffset;

        // 始终左对齐：起始X = baseX + scrollOffset
        float drawX = baseX + scrollOffset;
        float drawY = baseY + yOffset;

        context.transform()
                .translate(drawX, drawY)
                .scale(scale)
                .end(transforming -> {
                    context.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
                });
    }

    private void renderLineHighlight(HudRenderContext context, LineState line, int baseX, int baseY, float lineHeight, float positionY, float highlightFromX, float highlightToX, float yOffset) {
        if (line.line == null) return;
        String text = line.line.text;
        if (text.isEmpty()) return;

        float scale = lineHeight / Minecraft.getInstance().font.lineHeight;
        if (!(scale <= 0)) {
            float scrollOffset = line.scrollOffset;// 始终左对齐：起始X = baseX + scrollOffset
            float drawX = baseX + scrollOffset;
            float drawY = baseY + yOffset;
            int toX = (int) (drawX + highlightToX);
            context.pushScissor((int) highlightFromX, (int) positionY, toX, (int) (positionY + layout.getHeight()));
            context.transform()
                    .translate(drawX, drawY)
                    .scale(scale)
                    .end(transforming -> {
                        context.drawString(Minecraft.getInstance().font, text, 0, 0, line.line.emphasizeColor, false);
                    });
            context.popScissor();
        }
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
