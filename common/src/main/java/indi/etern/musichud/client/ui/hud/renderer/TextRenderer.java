package indi.etern.musichud.client.ui.hud.renderer;

import icyllis.modernui.mc.text.ModernStringSplitter;
import icyllis.modernui.mc.text.TextLayoutEngine;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import indi.etern.musichud.client.utils.ui.Easing;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;

@Getter
@Setter
public class TextRenderer implements HudRenderer {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private int vanillaLineHeight = -1;
    private ModernStringSplitter modernStringSplitter = null;
    private TextStyle currentTextData;
    private Layout layout;
    private int baseColor;
    private Position position;
    private int marqueeIntervalMillis = 5000;
    private Easing marqueeStartAndEndUpSpeedEasing = Easing.EASE_IN_OUT_SINE;
    private float marqueeSpaceWeight = 0.4f;
    private TextStyle nextTextData;
    private float transitionProgress = 1.0f;
    private boolean isTransitioning = false;
    private float transitionSpeed = 4.0f;
    private long lastUpdateTime = System.currentTimeMillis();
    private float marqueeDuration = 10000;

    public TextRenderer() {
        try {
            modernStringSplitter = TextLayoutEngine.getInstance().getStringSplitter();
        } catch (Throwable t) {
            MusicHud.getLogger(TextRenderer.class).debug("ModernTextEngine is disabled", t);
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
        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
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

        if (vanillaLineHeight < 0) {
            vanillaLineHeight = Minecraft.getInstance().font.lineHeight;
        }

        float scale = layout.getHeight() / vanillaLineHeight;

        Layout.AbsolutePosition absolutePosition = layout.calcAbsolutePosition(context);

        if (!isTransitioning || nextTextData == null) {
            renderText(context, currentTextData, absolutePosition, scale, 1.0f);
        } else {
            float oldAlpha = 1.0f - transitionProgress;
            if (oldAlpha > 0) {
                renderText(context, currentTextData, absolutePosition, scale, oldAlpha);
            }

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

        // 计算带透明度的颜色
        int color = getColorWithAlpha(textData.baseColor, alpha);

        // 计算位置
        float measuredWidth = measureWidth(text);
        float textRenderWidth = scale * measuredWidth;
        float layoutWidth = layout.getWidth();
        float x = position.computeX(absolutePosition.x(), text, Math.min(textRenderWidth, layoutWidth));
        float y = absolutePosition.y();
        boolean overflow = textRenderWidth > layoutWidth;

        float x1 = x;
        float marqueeWidth = 0;
        float marqueeOffset = 0;
        boolean enableMarqueeText = clientConfig.getEnableMarqueeText();
        if (enableMarqueeText && position == Position.LEFT) {
            marqueeWidth = textRenderWidth + layoutWidth * marqueeSpaceWeight;
            long elapsedTime = System.currentTimeMillis() - lastUpdateTime;
            float marqueeElapsedTime = Math.max(0, elapsedTime % (marqueeDuration + marqueeIntervalMillis) - marqueeIntervalMillis);
            float marqueeProgress = marqueeStartAndEndUpSpeedEasing.getInterpolation(marqueeElapsedTime / marqueeDuration);
            marqueeOffset = overflow ? marqueeProgress * marqueeWidth : 0;
            x1 -= marqueeOffset;
        } else {
            float maxWidth = layout.getWidth() / scale;
            text = trimToWidth(text, maxWidth);
            if (text.isEmpty()) return;
        }
        context.pushScissor((int) x, (int) y, (int) (x + layoutWidth), (int) (y + layout.getHeight() + 1));
        Transforming transform = context.transform();
        String finalText = text;
        transform.translate(x1, y)
                .subTransform(transforming -> {
                    transforming.scale(scale)
                            .then(transforming1 -> {
                                context.drawString(Minecraft.getInstance().font, finalText, 0, 0, color, false);
                            });
                });
        if (overflow && enableMarqueeText) {
            if (marqueeWidth - marqueeOffset < layoutWidth) {
                transform.translate(marqueeWidth, 0)
                        .subTransform(transforming -> {
                            transforming.scale(scale)
                                    .then(transforming1 -> {
                                        context.drawString(Minecraft.getInstance().font, finalText, 0, 0, color, false);
                                    });
                        });
            }
        }
        transform.end();
        context.popScissor();
    }

    private int getColorWithAlpha(int baseColor, float alpha) {
        float a = ((baseColor >> 24) & 0xff) / 255.0f;
        int alphaValue = (int) (a * alpha * 255);
        // 确保 alpha 值在 4-255 范围内（MC 把较低 alpha 视为不透明）
        alphaValue = Math.clamp(alphaValue, 4, 255);
        // 将 Alpha 通道合并到颜色中 (ARGB 格式)
        return (alphaValue << 24) | (baseColor & 0x00FFFFFF);
    }

    public float calcDisplayWidth() {
        if (currentTextData == null || currentTextData.text == null || currentTextData.text.isEmpty()) {
            return 0f;
        } else {
            return Math.min(layout.getWidth(), measureWidth(currentTextData.text) * (layout.getHeight() / vanillaLineHeight));
        }
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

    public enum Position {
        LEFT {
            @Override
            float computeX(float startX, String text, float scaledMeasuredWidth) {
                return startX;
            }
        }, CENTER {
            @Override
            float computeX(float startX, String text, float scaledMeasuredWidth) {
                return startX - 0.5f * scaledMeasuredWidth;
            }
        }, RIGHT {
            @Override
            float computeX(float startX, String text, float scaledMeasuredWidth) {
                return startX - scaledMeasuredWidth;
            }
        };

        abstract float computeX(float startX, String text, float scaledMeasuredWidth);
    }

    public static class TextStyle {
        public final int baseColor;
        public String text;

        public TextStyle(String text, int baseColor) {
            this.text = text;
            this.baseColor = baseColor;
        }
    }
}