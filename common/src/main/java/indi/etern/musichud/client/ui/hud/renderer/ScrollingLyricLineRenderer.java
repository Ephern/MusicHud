package indi.etern.musichud.client.ui.hud.renderer;

import icyllis.modernui.mc.text.ModernStringSplitter;
import icyllis.modernui.mc.text.TextLayoutEngine;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import indi.etern.musichud.client.ui.utils.Easings;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

public class ScrollingLyricLineRenderer {
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
    private float transitionProgress = 1.0f;
    private long transitionStartTime = 0;
    private long transitionDuration = 800;
    private int cachedContainerWidth;
    @Setter
    private int lineSpacing = 0;

    public ScrollingLyricLineRenderer() {
        try {
            modernStringSplitter = TextLayoutEngine.getInstance().getStringSplitter();
        } catch (Throwable t) {
            MusicHud.getLogger(ScrollingLyricLineRenderer.class).debug("ModernTextEngine is disabled", t);
        }

        currentLine1 = new LineState();
        currentLine2 = new LineState();
        nextLine1 = new LineState();
        nextLine2 = new LineState();
    }

    public void clear() {
        setLines(
                new TextStyle("", 0), 0,
                new TextStyle("", 0), 0,
                0
        );
    }

    /**
     * 设置双行文本及其样式
     *
     * @param style1       第一行的文本和颜色
     * @param scrollMs1    第一行滚动时长（毫秒），若文本不超宽则不滚动
     * @param style2       第二行的文本和颜色
     * @param scrollMs2    第二行滚动时长
     * @param transitionMs 切换动画时长（毫秒）
     */
    public void setLines(TextStyle style1, long scrollMs1,
                         TextStyle style2, long scrollMs2,
                         long transitionMs) {
        // 准备新配置
        LineConfig newLine1 = new LineConfig(style1, scrollMs1);
        LineConfig newLine2 = new LineConfig(style2, scrollMs2);

        // 如果已经处于切换中，先强制结束当前切换，把next变成current
        if (isTransitioning) {
            currentLine1.copyFrom(nextLine1);
            currentLine2.copyFrom(nextLine2);
            isTransitioning = false;
            transitionProgress = 1.0f;
        }

        // 检查是否有实际变化
        boolean textChanged = !newLine1.equals(currentLine1.config) || !newLine2.equals(currentLine2.config);
        if (!textChanged) {
            return;
        }

        // 准备新行状态（滚动尚未开始）
        nextLine1.reset(newLine1);
        nextLine2.reset(newLine2);
        // 预计算文本宽度（基于当前容器宽度）
        if (cachedContainerWidth > 0) {
            recalcScrollIfNeeded(nextLine1, cachedContainerWidth, line1Height);
            recalcScrollIfNeeded(nextLine2, cachedContainerWidth, line2Height);
        }

        // 开始切换动画
        isTransitioning = true;
        transitionProgress = 0.0f;
        transitionDuration = transitionMs;
        transitionStartTime = System.currentTimeMillis();
    }

    private void recalcScrollIfNeeded(LineState line, int containerWidth, float lineHeight) {
        if (line.config == null) return;
        float textWidth = getTextWidth(line.config.text, lineHeight);
        if (textWidth > containerWidth) {
            line.maxScrollOffset = -(textWidth - containerWidth);
            line.needScroll = true;
        } else {
            line.needScroll = false;
            line.maxScrollOffset = 0;
        }
    }

    private void startScrollingIfNeeded(LineState line, int containerWidth) {
        if (line.config == null) return;
        if (line.needScroll && containerWidth > 0) {
            line.isScrolling = true;
            line.scrollStartTime = System.currentTimeMillis();
            line.scrollOffset = 0;
            line.scrollTarget = line.maxScrollOffset;
            if (line.config.scrollMs <= 0) {
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
        if (line.config.scrollMs <= 0) {
            line.isScrolling = false;
            line.scrollOffset = line.scrollTarget;
            return;
        }
        long elapsed = now - line.scrollStartTime;
        if (elapsed >= line.config.scrollMs) {
            line.isScrolling = false;
            line.scrollOffset = line.scrollTarget;
        } else {
            float progress = Easings.EASE_IN_OUT_SINE.getInterpolation((float) elapsed / line.config.scrollMs);
            line.scrollOffset = line.scrollTarget * progress;
        }
    }

    private float getTextWidth(String text, float lineHeight) {
        if (text == null || text.isEmpty()) return 0;
        float rawWidth;
        if (modernStringSplitter != null) {
            rawWidth = modernStringSplitter.stringWidth(text);
        } else {
            Font font = Minecraft.getInstance().font;
            rawWidth = font.width(text);
        }
        float scale = lineHeight / Minecraft.getInstance().font.lineHeight;
        return rawWidth * scale;
    }

    private void renderLine(GuiGraphics gr, LineState line, int baseX, int baseY, float lineHeight, float yOffset) {
        if (line.config == null) return;
        String text = line.config.text;
        if (text.isEmpty()) return;

        float scale = lineHeight / Minecraft.getInstance().font.lineHeight;
        if (scale <= 0) return;

        float scrollOffset = line.scrollOffset;

        // 始终左对齐：起始X = baseX + scrollOffset
        float drawX = baseX + scrollOffset;

        float drawY = baseY + yOffset;

        gr.pose().pushMatrix();
        gr.pose().translate(drawX, drawY);
        gr.pose().scale(scale, scale);
        gr.drawString(Minecraft.getInstance().font, text, 0, 0, line.config.color);
        gr.pose().popMatrix();
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

    /**
     * 主渲染入口，应在每一帧调用
     *
     * @param gr GuiGraphics
     */
    public void render(GuiGraphics gr) {
        if (layout == null) {
            return;
        }

        // 计算实际布局绝对坐标和尺寸
        Layout.AbsolutePosition absPos = layout.calcAbsolutePosition(gr);
        // 布局缓存（每次渲染时更新）
        int cachedContainerX = (int) absPos.x();
        int cachedContainerY = (int) absPos.y();
        cachedContainerWidth = (int) layout.width;
        int cachedContainerHeight = (int) layout.height;

        if (cachedContainerWidth <= 0 || cachedContainerHeight <= 0) return;

        updateAnimations();

        int totalHeight = (int) (line1Height + line2Height);
        int previousStartY = cachedContainerY + (cachedContainerHeight - totalHeight) / 2; // 垂直居中
        int nextStartY = cachedContainerY + (cachedContainerHeight - totalHeight) / 2; // 垂直居中
        Layout.AbsolutePosition absolutePosition = layout.calcAbsolutePosition(gr);

        float x = absolutePosition.x();
        float y = absolutePosition.y();
        gr.enableScissor((int) x, (int) y, (int) (x + layout.width), (int) (y + layout.height));
        if (isTransitioning && nextLine1.config != null && nextLine2.config != null) {
            // 旧文本向上移出
            float easedProgress = Easings.EASE_IN_OUT_QUINT.getInterpolation(transitionProgress);
            float oldYOffset = -easedProgress * layout.height;
            renderLine(gr, currentLine1, cachedContainerX, previousStartY, line1Height, oldYOffset);
            renderLine(gr, currentLine2, cachedContainerX, (int) (previousStartY + lineSpacing + line1Height), line2Height, oldYOffset);

            // 新文本从下方向上移入
            float newYOffset = (1 - easedProgress) * layout.height;
            renderLine(gr, nextLine1, cachedContainerX, nextStartY, line1Height, newYOffset);
            renderLine(gr, nextLine2, cachedContainerX, (int) (nextStartY + lineSpacing + line1Height), line2Height, newYOffset);
        } else {
            // 正常显示
            renderLine(gr, currentLine1, cachedContainerX, nextStartY, line1Height, 0);
            renderLine(gr, currentLine2, cachedContainerX, (int) (nextStartY + lineSpacing + line1Height), line2Height, 0);
        }
        gr.disableScissor();
    }

    private static class LineConfig {
        String text;
        int color;
        long scrollMs;

        public LineConfig(TextStyle style, long scrollMs) {
            this.text = style.text;
            this.color = style.baseColor;
            this.scrollMs = scrollMs;
        }

        public LineConfig(String text, int color, long scrollMs) {
            this.text = text;
            this.color = color;
            this.scrollMs = scrollMs;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof LineConfig other)) return false;
            return text.equals(other.text) && color == other.color && scrollMs == other.scrollMs;
        }
    }

    private static class LineState {
        LineConfig config;
        boolean needScroll;
        float maxScrollOffset;
        boolean isScrolling;
        long scrollStartTime;
        float scrollTarget;
        float scrollOffset;

        void reset(@Nullable LineConfig cfg) {
            this.config = cfg;
            this.needScroll = false;
            this.maxScrollOffset = 0;
            this.isScrolling = false;
            this.scrollStartTime = 0;
            this.scrollTarget = 0;
            this.scrollOffset = 0;
        }

        void copyFrom(LineState other) {
            if (other.config != null) {
                this.config = new LineConfig(other.config.text, other.config.color, other.config.scrollMs);
            } else {
                this.config = null;
            }
            this.needScroll = other.needScroll;
            this.maxScrollOffset = other.maxScrollOffset;
            this.isScrolling = other.isScrolling;
            this.scrollStartTime = other.scrollStartTime;
            this.scrollTarget = other.scrollTarget;
            this.scrollOffset = other.scrollOffset;
        }
    }

    public static class TextStyle {
        public String text;
        public int baseColor;

        public TextStyle(String text, int baseColor) {
            this.text = text;
            this.baseColor = baseColor;
        }
    }
}