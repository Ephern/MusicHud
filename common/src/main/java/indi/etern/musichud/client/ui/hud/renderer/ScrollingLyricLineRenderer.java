package indi.etern.musichud.client.ui.hud.renderer;

import icyllis.modernui.mc.FontResourceManager;
import icyllis.modernui.mc.text.ModernStringSplitter;
import icyllis.modernui.mc.text.TextLayoutEngine;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.dto.LyricLine;
import indi.etern.musichud.client.audio.NowPlayingInfo;
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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

public class ScrollingLyricLineRenderer implements HudRenderer {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private static final int LYRICS_ANIMATION_DURATION = 300;
    private final SpringValue switchSpring = new SpringValue((float) LYRICS_ANIMATION_DURATION / 1000, 1f);
    private final LineState currentLine1;
    private final LineState currentLine2;
    private final LineState nextLine1;
    private final LineState nextLine2;
    ModernStringSplitter modernStringSplitter;
    @Setter
    private float line1Height;
    @Setter
    private float line2Height;
    @Setter
    private Layout layout;
    private boolean isTransitioning = false;
    private final AtomicReference<PendingLines> pendingLines = new AtomicReference<>();
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

        currentLine1 = new LineState();
        currentLine2 = new LineState();
        nextLine1 = new LineState();
        nextLine2 = new LineState();
    }

    public void clear() {
        setLines(
                new Line(null, "", 0, 0, 0),
                new Line(null, "", 0, 0, 0),
                false
        );
    }

    /**
     * 设置双行文本及其样式
     * <p>
     * May be called from a worker thread: the request is stashed and applied on the render
     * thread (see {@link #applyPendingLines(long)}). Rapid calls naturally conflate to the
     * latest request.
     *
     * @param line1   第一行的文本和颜色
     * @param line2   第二行的文本和颜色
     * @param animate whether to start the vertical switch spring
     */
    public void setLines(Line line1, Line line2, boolean animate) {
        int width = cachedContainerWidth;
        float maxScroll1 = computeMaxScrollOffset(line1.text(), line1Height, width);
        float maxScroll2 = computeMaxScrollOffset(line2.text(), line2Height, width);
        pendingLines.set(new PendingLines(line1, line2, animate, maxScroll1, maxScroll2));
    }

    private void applyPendingLines(long nowNanos) {
        PendingLines pending = pendingLines.getAndSet(null);
        if (pending == null) {
            return;
        }

        Line line1 = pending.line1();
        Line line2 = pending.line2();

        // Rapid switching: the outgoing line keeps sliding out; just replace the incoming
        // line and retarget the same spring, which keeps its value and velocity.
        if (isTransitioning) {
            if (!line1.equals(nextLine1.line) || !line2.equals(nextLine2.line)) {
                nextLine1.reset(line1);
                nextLine2.reset(line2);
                applyScrollMetrics(nextLine1, pending.maxScroll1());
                applyScrollMetrics(nextLine2, pending.maxScroll2());
                switchSpring.setTarget(1f, nowNanos);
            }
            return;
        }

        boolean textChanged = !line1.equals(currentLine1.line) || !line2.equals(currentLine2.line);
        if (!textChanged) {
            return;
        }

        nextLine1.reset(line1);
        nextLine2.reset(line2);
        applyScrollMetrics(nextLine1, pending.maxScroll1());
        applyScrollMetrics(nextLine2, pending.maxScroll2());

        isTransitioning = pending.animate();
        if (isTransitioning) {
            switchSpring.set(0f, 0f, 1f, nowNanos);
        } else {
            currentLine1.copyFrom(nextLine1);
            currentLine2.copyFrom(nextLine2);
            if (cachedContainerWidth > 0) {
                startScrollingIfNeeded(currentLine1, cachedContainerWidth);
                startScrollingIfNeeded(currentLine2, cachedContainerWidth);
            }
            nextLine1.reset(null);
            nextLine2.reset(null);
        }
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

        // 更新切换动画
        if (isTransitioning) {
            switchSpring.update(nowNanos);
            if (switchSpring.isSettled()) {
                isTransitioning = false;
                switchSpring.jumpTo(1f);
                currentLine1.copyFrom(nextLine1);
                currentLine2.copyFrom(nextLine2);
                if (cachedContainerWidth > 0) {
                    startScrollingIfNeeded(currentLine1, cachedContainerWidth);
                    startScrollingIfNeeded(currentLine2, cachedContainerWidth);
                }
                nextLine1.reset(null);
                nextLine2.reset(null);
            }
        }

        // 更新滚动动画
        if (!isTransitioning) {
            updateScrolling(currentLine1, now);
            updateScrolling(currentLine2, now);
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
        if (isTransitioning && nextLine1.line != null && nextLine2.line != null) {
            float easedProgress = Math.clamp(switchSpring.getValue(), 0f, 1f);
            float oldYOffset = -easedProgress * layout.getHeight();
            if (currentLine1.line != null && currentLine1.line.lyricLine != null) {
                if (currentLine1.line.lyricLine.isWordByWord()) {
                    renderLine(context, currentLine1, currentLine1.line.fadeColor, cachedContainerX, startY, line1Height, oldYOffset);
                    renderLineHighlight(context, currentLine1, cachedContainerX, startY, line1Height, y, x, calcHighlightWidth(currentLine1, line1Height), oldYOffset);
                } else {
                    renderLine(context, currentLine1, currentLine1.line.emphasizeColor, cachedContainerX, startY, line1Height, oldYOffset);
                }
                if (clientConfig.getShowTranslatedCnLyrics()) {
                    if (currentLine2.line != null && currentLine2.line.lyricLine != null) {
                        renderLine(context, currentLine2, currentLine2.line.fadeColor, cachedContainerX, (int) (startY + lineSpacing + line1Height), line2Height, oldYOffset);
                    }
                }
            }

            if (nextLine1.line != null && nextLine1.line.lyricLine != null) {
                float newYOffset = (1 - easedProgress) * layout.getHeight();
                int color = nextLine1.line.lyricLine.isWordByWord() ? nextLine1.line.fadeColor : nextLine1.line.emphasizeColor;
                renderLine(context, nextLine1, color, cachedContainerX, startY, line1Height, newYOffset);
                if (clientConfig.getShowTranslatedCnLyrics()) {
                    if (nextLine2.line != null && nextLine2.line.lyricLine != null) {
                        renderLine(context, nextLine2, nextLine2.line.fadeColor, cachedContainerX, (int) (startY + lineSpacing + line1Height), line2Height, newYOffset);
                    }
                }
            }
        } else {
            if (currentLine1.line != null && currentLine1.line.lyricLine != null) {
                if (currentLine1.line.lyricLine.isWordByWord()) {
                    renderLine(context, currentLine1, currentLine1.line.fadeColor, cachedContainerX, startY, line1Height, 0);
                    renderLineHighlight(context, currentLine1, cachedContainerX, startY, line1Height, y, x, calcHighlightWidth(currentLine1, line1Height), 0);
                } else {
                    renderLine(context, currentLine1, currentLine1.line.emphasizeColor, cachedContainerX, startY, line1Height, 0);
                }
                if (clientConfig.getShowTranslatedCnLyrics()) {
                    if (currentLine2.line != null && currentLine2.line.lyricLine != null) {
                        renderLine(context, currentLine2, currentLine2.line.fadeColor, cachedContainerX, (int) (startY + lineSpacing + line1Height), line2Height, 0);
                    }
                }
            }
        }
        context.popScissor();
    }

    private float calcHighlightWidth(LineState lineState,float lineHeight) {
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

        void copyFrom(LineState other) {
            if (other.line != null) {
                this.line = new Line(other.line.lyricLine, other.line.text, other.line.fadeColor, other.line.emphasizeColor, other.line.scrollMs);
            } else {
                this.line = null;
            }
            this.needScroll = other.needScroll;
            this.maxScrollOffset = other.maxScrollOffset;
            this.isScrolling = other.isScrolling;
            this.scrollStartTime = other.scrollStartTime;
            this.scrollTarget = other.scrollTarget;
            this.scrollOffset = other.scrollOffset;
            this.highlightCalculator = other.highlightCalculator;
        }
    }

    public record Line(LyricLine lyricLine, String text, int fadeColor, int emphasizeColor, long scrollMs) {}
}
