package indi.etern.musichud.client.ui.hud.renderer;

import icyllis.modernui.mc.text.ModernStringSplitter;
import icyllis.modernui.mc.text.TextLayoutEngine;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;

@Getter
@Setter
public class TextRenderer implements HudRenderer{
    private ModernStringSplitter modernStringSplitter = null;
    private TextStyle currentTextData;
    private Layout layout;
    private int baseColor;
    private Position position;

    // 淡入淡出相关字段
    private TextStyle nextTextData; // 下一个要显示的文本
    private float transitionProgress = 1.0f; // 过渡进度 (0.0f - 1.0f)
    private boolean isTransitioning = false;
    private float transitionSpeed = 4.0f; // 过渡速度，可以根据需要调整
    private long lastUpdateTime = System.currentTimeMillis();

    public TextRenderer() {
        try {
            modernStringSplitter = TextLayoutEngine.getInstance().getStringSplitter();
        } catch (Throwable t) {
            MusicHud.getLogger(ScrollingLyricLineRenderer.class).debug("ModernTextEngine is disabled", t);
        }
    }

    public void configure(Layout layout, int baseColor, Position position) {
        this.layout = layout;
        this.baseColor = baseColor;
        this.position = position;
    }

    public void setText(String text) {
        if (text == null) {
            text = "";
        }

        if (currentTextData == null) {
            // 第一次设置文本，直接显示
            currentTextData = new TextStyle(text, baseColor);
            transitionProgress = 1.0f;
            isTransitioning = false;
            nextTextData = null;
        } else if (text.equals(currentTextData.text)) {
            // 文本相同，无需过渡
            if (isTransitioning) {
                // 如果正在过渡，直接完成当前过渡
                currentTextData.text = text;
                transitionProgress = 1.0f;
                isTransitioning = false;
                nextTextData = null;
            }
        } else {
            // 文本不同，开始过渡
            if (isTransitioning) {
                // 如果已经在过渡中，有两种处理方式：
                // 1. 如果新文本与nextTextData相同，保持当前过渡
                // 2. 如果不同，重置过渡，重新开始
                if (nextTextData == null || !text.equals(nextTextData.text)) {
                    // 新文本不同，快速完成当前过渡，然后开始新的过渡
                    if (nextTextData != null) {
                        // 立即完成当前过渡
                        currentTextData = nextTextData;
                    }
                    // 开始新的过渡
                    nextTextData = new TextStyle(text, baseColor);
                    transitionProgress = 0.0f;
                    lastUpdateTime = System.currentTimeMillis();
                }
            } else {
                // 不在过渡中，开始新过渡
                nextTextData = new TextStyle(text, baseColor);
                transitionProgress = 0.0f;
                isTransitioning = true;
                lastUpdateTime = System.currentTimeMillis();
            }
        }
    }

    private void updateTransition() {
        if (!isTransitioning) return;

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f; // 转换为秒
        lastUpdateTime = currentTime;

        transitionProgress += deltaTime * transitionSpeed;

        if (transitionProgress >= 1.0f) {
            transitionProgress = 1.0f;
            if (nextTextData != null) {
                currentTextData = nextTextData;
            }
            nextTextData = null;
            isTransitioning = false;
        }
    }

    public void render(HudRenderContext context) {
        // 更新过渡进度
        updateTransition();

        if (currentTextData == null || layout.getHeight() <= 0 || layout.getWidth() <= 0) {
            return;
        }

        float scale = layout.getHeight() / 9;

        // 应用位置和缩放
        Layout.AbsolutePosition absolutePosition = layout.calcAbsolutePosition(context);

        // 如果没有在过渡，只渲染当前文本
        if (!isTransitioning || nextTextData == null) {
            renderText(context, currentTextData, absolutePosition, scale, 1.0f);
        } else {
            // 渲染淡出的旧文本
            float oldAlpha = 1.0f - transitionProgress;
            if (oldAlpha > 0) {
                renderText(context, currentTextData, absolutePosition, scale, oldAlpha);
            }

            // 渲染淡入的新文本
            float newAlpha = transitionProgress;
            if (newAlpha > 0) {
                renderText(context, nextTextData, absolutePosition, scale, newAlpha);
            }
        }
    }

    private void renderText(HudRenderContext context, TextStyle textData, Layout.AbsolutePosition absolutePosition,
                            float scale, float alpha) {
        String text = textData.text;
        if (text == null || text.isEmpty()) return;

        String trimmedText = trimToWidth(text, layout.getWidth() / scale);
        if (trimmedText.isEmpty()) return;

        // 计算带透明度的颜色
        int color = getColorWithAlpha(textData.baseColor, alpha);

        // 计算位置
        float x = position.computeX(absolutePosition.x(), scale, trimmedText, this::measureWidth);
        context.transform()
                .translate(x, absolutePosition.y())
                .scale(scale)
                .then(transforming -> {
                    context.drawString(Minecraft.getInstance().font, trimmedText, 0, 0, color, false);
                });
    }

    private int getColorWithAlpha(int baseColor, float alpha) {
        float a = ((baseColor >> 24) & 0xff) / 255.0f;
        int alphaValue = (int) (a * alpha * 255);
        // 确保 alpha 值在 0-255 范围内
        alphaValue = Math.clamp(alphaValue, 0, 255);
        // 将 Alpha 通道合并到颜色中 (ARGB 格式)
        return (alphaValue << 24) | (baseColor & 0x00FFFFFF);
    }

    public float calcDisplayWidth() {
        if (currentTextData == null || currentTextData.text == null || currentTextData.text.isEmpty()) {
            return 0f;
        } else {
            return measureWidth(currentTextData.text) * (layout.getHeight() / Minecraft.getInstance().font.lineHeight);
        }
    }

    public enum Position {
        LEFT {
            @Override
            float computeX(float startX, float scale, String text, WidthFunction widthFn) {
                return startX;
            }
        }, CENTER {
            @Override
            float computeX(float startX, float scale, String text, WidthFunction widthFn) {
                return startX - 0.5f * widthFn.measure(text) * scale;
            }
        }, RIGHT {
            @Override
            float computeX(float startX, float scale, String text, WidthFunction widthFn) {
                return startX - widthFn.measure(text) * scale;
            }
        };

        abstract float computeX(float startX, float scale, String text, WidthFunction widthFn);
    }

    public static class TextStyle {
        public String text;
        public final int baseColor;

        public TextStyle(String text, int baseColor) {
            this.text = text;
            this.baseColor = baseColor;
        }
    }

    @FunctionalInterface
    private interface WidthFunction {
        float measure(String text);
    }

    private ModernStringSplitter tryGetSplitter() {
        try {
            return TextLayoutEngine.getInstance().getStringSplitter();
        } catch (Throwable t) {
            return null;
        }
    }

    private String trimToWidth(String text, float maxWidth) {
        if (modernStringSplitter != null) {
            int maxIndex = modernStringSplitter.indexByWidth(text, maxWidth, Style.EMPTY);
            String trimmed = text.substring(0, maxIndex);
            if (maxIndex < text.length()) {
                trimmed = addEllipsis(trimmed);
            }
            return trimmed;
        }

        return trimWithVanilla(text, maxWidth);
    }

    private String trimWithVanilla(String text, float maxWidth) {
        float width = 0;
        int index = 0;
        final int len = text.length();
        while (index < len) {
            int codePoint = text.codePointAt(index);
            int cpLen = Character.charCount(codePoint);
            String cpStr = new String(new int[]{codePoint}, 0, 1);
            int w = Minecraft.getInstance().font.width(cpStr);
            if (width + w > maxWidth) {
                break;
            }
            width += w;
            index += cpLen;
        }

        String trimmed = text.substring(0, index);
        if (index < len) {
            trimmed = addEllipsis(trimmed);
        }
        return trimmed;
    }

    private String addEllipsis(String base) {
        if (base.length() <= 3) {
            return "";
        }
        int cut = Math.max(0, base.length() - 3);
        return base.substring(0, cut) + "...";
    }

    private float measureWidth(String text) {
        ModernStringSplitter splitter = tryGetSplitter();
        if (splitter != null) {
            return splitter.measureText(text);
        }
        return Minecraft.getInstance().font.width(text);
    }
}