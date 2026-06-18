package indi.etern.musichud.client.ui.hud.renderer;

import icyllis.modernui.mc.FontResourceManager;
import icyllis.modernui.mc.text.ModernStringSplitter;
import icyllis.modernui.mc.text.TextLayoutEngine;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.LyricLine;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import indi.etern.musichud.client.ui.utils.Easing;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;

public class ScrollingLyricLineRenderer implements HudRenderer {
    private final LineState currentLine1;
    private final LineState currentLine2;
    private final LineState nextLine1;
    private final LineState nextLine2;
    private final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    ModernStringSplitter modernStringSplitter;
    @Setter
    private float line1Height;
    @Setter
    private float line2Height;
    @Setter
    private Layout layout;
    private boolean isTransitioning = false;
    private float transitionProgress = 1.0f;
    private long transitionStartTime = 0;
    private long transitionDuration = 800;
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
                0
        );
    }

    /**
     * 设置双行文本及其样式
     *
     * @param line1             第一行的文本和颜色
     * @param line2             第二行的文本和颜色
     * @param transitionDuration 切换动画时长（毫秒）
     */
    public void setLines(Line line1, Line line2,
                         long transitionDuration) {
        // 如果已经处于切换中，先强制结束当前切换，把next变成current
        if (isTransitioning) {
            currentLine1.copyFrom(nextLine1);
            currentLine2.copyFrom(nextLine2);
            isTransitioning = false;
            transitionProgress = 1.0f;
        }

        // 检查是否有实际变化
        boolean textChanged = !line1.equals(currentLine1.line) || !line2.equals(currentLine2.line);
        if (!textChanged) {
            return;
        }

        // 准备新行状态（滚动尚未开始）
        nextLine1.reset(line1);
        nextLine2.reset(line2);
        // 预计算文本宽度（基于当前容器宽度）
        if (cachedContainerWidth > 0) {
            recalcScrollIfNeeded(nextLine1, cachedContainerWidth, line1Height);
            recalcScrollIfNeeded(nextLine2, cachedContainerWidth, line2Height);
        }

        // 开始切换动画
        isTransitioning = true;
        transitionProgress = 0.0f;
        this.transitionDuration = transitionDuration;
        transitionStartTime = System.currentTimeMillis();
    }

    private void recalcScrollIfNeeded(LineState line, int containerWidth, float lineHeight) {
        if (line.line == null) return;
        float textWidth = calcTextWidth(line.line.text, lineHeight);
        if (textWidth > containerWidth) {
            line.maxScrollOffset = -(textWidth - containerWidth);
            line.needScroll = true;
        } else {
            line.needScroll = false;
            line.maxScrollOffset = 0;
        }
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
            } catch (Exception e) {
                modernStringSplitter = null;//fallback
                rawWidth = font.width(text);
            }
        } else {
            rawWidth = font.width(text);
        }
        return rawWidth * lineHeight / font.lineHeight;
    }

    private void updateAnimations() {
        long now = System.currentTimeMillis();

        // 更新切换动画
        if (isTransitioning) {
            long elapsed = now - transitionStartTime;
            if (elapsed >= transitionDuration) {
                transitionProgress = 1.0f;
                isTransitioning = false;
                currentLine1.copyFrom(nextLine1);
                currentLine2.copyFrom(nextLine2);
                if (cachedContainerWidth > 0) {
                    startScrollingIfNeeded(currentLine1, cachedContainerWidth);
                    startScrollingIfNeeded(currentLine2, cachedContainerWidth);
                }
                nextLine1.reset(null);
                nextLine2.reset(null);
            } else {
                transitionProgress = (float) elapsed / transitionDuration;
                transitionProgress = Math.min(1.0f, transitionProgress);
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
            float easedProgress = Easing.EASE_IN_OUT_QUINT.getInterpolation(transitionProgress);
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
        String text = line.text;
        float textWidth = calcTextWidth(text, lineHeight);
        LyricLine currentLyricLine = line.lyricLine;
        if (currentLyricLine == null) {
            return 0;
        }
        currentLyricLine.parsePhrases();
        Duration lineStart = currentLyricLine.getStartTime();
        Duration playedDuration = nowPlayingInfo.getPlayedDuration();
        List<LyricLine.Phrase> phrases = currentLyricLine.getPhrases();
        if (currentLyricLine.isWordByWord()){
            int currentPhraseIndex = currentLyricLine.binarySearchPhraseIndex(playedDuration);
            float phraseStartOffest = 0;
            int currentPhraseStartOffset = 0;
            Duration currentPhraseStartTime = lineStart;
            if (currentPhraseIndex >= 1) {
                LyricLine.Phrase previousPhrase = phrases.get(currentPhraseIndex - 1);
                currentPhraseStartOffset = previousPhrase.endOffset();
                currentPhraseStartTime = previousPhrase.endTime();
                if (currentPhraseStartOffset <= text.length()) {
                    phraseStartOffest = calcTextWidth(text.substring(0, currentPhraseStartOffset), lineHeight);
                }
            }
            LyricLine.Phrase currentPhrase = currentPhraseIndex < phrases.size() ? phrases.get(currentPhraseIndex) : null;
            float phraseWidth = 0;
            if (currentPhrase != null) {
                float rate = (float) playedDuration.minus(currentPhraseStartTime).toMillis() / currentPhrase.durationMillis();
                if (currentPhrase.endOffset() <= text.length()) {
                    phraseWidth = calcTextWidth(text.substring(currentPhraseStartOffset, currentPhrase.endOffset()), lineHeight) * Math.clamp(rate, 0, 1);
                }
            }
            return phraseStartOffest + phraseWidth;
        } else {
            return textWidth;
        }
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

    private static class LineState {
        Line line;
        boolean needScroll;
        float maxScrollOffset;
        boolean isScrolling;
        long scrollStartTime;
        float scrollTarget;
        float scrollOffset;

        void reset(@Nullable Line line) {
            this.line = line;
            this.needScroll = false;
            this.maxScrollOffset = 0;
            this.isScrolling = false;
            this.scrollStartTime = 0;
            this.scrollTarget = 0;
            this.scrollOffset = 0;
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
        }
    }

    public record Line(LyricLine lyricLine, String text, int fadeColor, int emphasizeColor, long scrollMs) {}
}