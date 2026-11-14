package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.render.*;
import indi.etern.musichud.client.ui.render.text.*;

import icyllis.modernui.mc.text.ModernStringSplitter;
import icyllis.modernui.mc.text.TextLayoutEngine;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.SceneStyle;
import indi.etern.musichud.client.ui.render.text.GradientSpec;
import indi.etern.musichud.client.utils.ui.Easing;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Getter
@Setter
public class TextRenderer implements RenderLayer {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    /** Left edge mask fade-in window before the marquee starts moving. */
    private static final float MARQUEE_FADE_IN_MS = 200f;
    private int vanillaLineHeight = -1;
    private ModernStringSplitter modernStringSplitter = null;
    private TextStyle currentTextData;
    private SceneStyle hudStyle;
    private int baseColor;
    private Position position;
    private int marqueeIntervalMillis = 5000;
    private Easing marqueeStartAndEndUpSpeedEasing = Easing.EASE_IN_OUT_SINE;
    private float marqueeSpaceWeight = 0.4f;
    /** Edge mask width, in units of the text height. */
    private float marqueeEdgeFadeWidth = 0.9f;
    private TextStyle nextTextData;
    private float transitionProgress = 1.0f;
    private boolean isTransitioning = false;
    private float transitionSpeed = 4.0f;
    private long lastUpdateTime = System.currentTimeMillis();
    private float marqueeDuration = 10000;
    private String measuredCurrentText;
    private String measuredNextText;
    private float measuredHeight = -1;
    private float measuredDisplayWidth;
    /**
     * Optional horizontal gradient; when set the text is drawn with per-vertex gradient colors
     * instead of a single color. {@code null} keeps the legacy flat path.
     */
    private GradientSpec gradient;
    /** Ellipsis-trimmed text is stable per (text, available width); it is rebuilt every frame. */
    private final Map<String, String> trimmedCache = new HashMap<>();
    private float trimmedCacheMaxWidth = Float.NaN;

    public TextRenderer() {
        try {
            modernStringSplitter = TextLayoutEngine.getInstance().getStringSplitter();
        } catch (Throwable t) {
            MusicHud.getLogger(TextRenderer.class).debug("ModernTextEngine is disabled", t);
        }
    }

    public void configure(SceneStyle hudStyle, int baseColor, Position position) {
        this.hudStyle = hudStyle;
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

    @Override
    public void update() {
        updateTransition();
    }

    public void render(SceneRenderContext context) {
        if (currentTextData == null || hudStyle.getHeight() <= 0 || hudStyle.getWidth() <= 0) {
            return;
        }

        if (vanillaLineHeight < 0) {
            vanillaLineHeight = Minecraft.getInstance().font.lineHeight;
        }

        float scale = hudStyle.getHeight() / vanillaLineHeight;

        SceneStyle.AbsolutePosition absolutePosition = hudStyle.calcAbsolutePosition(context);

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

    private void renderText(SceneRenderContext context, TextStyle textData, SceneStyle.AbsolutePosition absolutePosition,
                            float scale, float alpha) {
        String text = textData.text;
        if (text == null || text.isEmpty()) return;

        // 计算带透明度的颜色
        float absoluteAlpha = alpha * hudStyle.calcAbsoluteAlpha();
        int color = getColorWithAlpha(textData.baseColor, absoluteAlpha);

        float boxLeft = absolutePosition.x();
        float boxWidth = hudStyle.getWidth();
        float boxRight = boxLeft + boxWidth;
        float y = absolutePosition.y();
        float measuredWidth = measureWidth(text);
        float textRenderWidth = scale * measuredWidth;
        boolean overflow = textRenderWidth > boxWidth;

        float x = boxLeft;
        float marqueeWidth = 0;
        float marqueeOffset = 0;
        float fadeBandLeft = 0;
        float fadeBandRight = 0;
        float fadeStrengthLeft = 0;
        float fadeStrengthRight = 0;
        boolean enableMarqueeText = clientConfig.getEnableMarqueeText();
        if (enableMarqueeText && position == Position.LEFT) {
            marqueeWidth = textRenderWidth + boxWidth * marqueeSpaceWeight;
            // keep the phase in long arithmetic: as a float, the elapsed milliseconds lose
            // precision once the HUD has been running for a while (16 ms steps past 2^24)
            long cycleMs = (long) (marqueeDuration + marqueeIntervalMillis);
            float marqueePhase = cycleMs > 0
                    ? (float) ((System.currentTimeMillis() - lastUpdateTime) % cycleMs) : 0f;
            float marqueeElapsedTime = Math.max(0f, marqueePhase - marqueeIntervalMillis);
            float marqueeProgress = marqueeDuration > 0
                    ? marqueeStartAndEndUpSpeedEasing.getInterpolation(marqueeElapsedTime / marqueeDuration)
                    : 0f;
            marqueeOffset = overflow ? marqueeProgress * marqueeWidth : 0;
            x -= marqueeOffset;
            if (overflow) {
                float baseBand = Math.min(hudStyle.getHeight() * marqueeEdgeFadeWidth, boxWidth * 0.4f);
                // the right edge always cuts the run, so it always dissolves; clamping the band to
                // the overhang keeps text that is still fully visible from being dimmed
                fadeBandRight = Math.min(baseBand, textRenderWidth - boxWidth);
                fadeStrengthRight = 1f;
                // the left edge only cuts the leading run, i.e. exactly while it still crosses the
                // box: fade in during the last MARQUEE_FADE_IN_MS of the pause, then drop the mask
                // the instant the run has fully left. At that moment the band holds no text, so
                // the mask vanishing is invisible, and it stays gone while the pause brings the
                // wrapped copy back to its initial position.
                fadeBandLeft = baseBand;
                if (marqueeOffset <= textRenderWidth + 0.5f) {
                    float fadeInStart = Math.max(0f, marqueeIntervalMillis - MARQUEE_FADE_IN_MS);
                    fadeStrengthLeft = TextEdgeFade.smoothstep(
                            (marqueePhase - fadeInStart) / MARQUEE_FADE_IN_MS);
                }
            }
        } else {
            String trimmed = trimToWidth(text, boxWidth / scale);
            if (trimmed.isEmpty()) return;
            text = trimmed;
            float trimmedWidth = measureWidth(text) * scale;
            x = switch (position) {
                case LEFT -> boxLeft;
                case CENTER -> boxLeft + (boxWidth - trimmedWidth) / 2f;
                case RIGHT -> boxRight - trimmedWidth;
            };
            x = Math.max(boxLeft, x);
            overflow = false;
        }
        context.pushScissor((int) boxLeft, (int) y, (int) boxRight, (int) (y + hudStyle.getHeight() + 1));
        Transforming transform = context.transform();
        String finalText = text;
        TextEdgeFade fade = TextEdgeFade.between(boxLeft, boxRight, fadeBandLeft, fadeBandRight,
                fadeStrengthLeft, fadeStrengthRight, x, scale);
        transform.translate(x, y)
                .subTransform(transforming -> {
                    transforming.scale(scale)
                            .then(transforming1 -> {
                                drawTextRun(context, finalText, color, absoluteAlpha, fade);
                            });
                });
        if (overflow) {
            if (marqueeWidth - marqueeOffset < boxWidth) {
                TextEdgeFade wrapFade = TextEdgeFade.between(boxLeft, boxRight, fadeBandLeft, fadeBandRight,
                        fadeStrengthLeft, fadeStrengthRight, x + marqueeWidth, scale);
                transform.translate(marqueeWidth, 0)
                        .subTransform(transforming -> {
                            transforming.scale(scale)
                                    .then(transforming1 -> {
                                        drawTextRun(context, finalText, color, absoluteAlpha, wrapFade);
                                    });
                        });
            }
        }
        transform.end();
        context.popScissor();
    }

    private void drawTextRun(SceneRenderContext context, String text, int color, float alpha, TextEdgeFade fade) {
        Font font = Minecraft.getInstance().font;
        if (gradient != null) {
            context.drawGradientString(font, text, 0, 0, gradient, alpha, fade);
        } else {
            // color already carries the transition alpha, so the per-glyph alpha stays neutral
            context.drawGradientString(font, text, 0, 0, GradientSpec.flat(color), 1f, fade);
        }
    }

    private int getColorWithAlpha(int baseColor, float alpha) {
        float a = ((baseColor >> 24) & 0xff) / 255.0f;
        int alphaValue = (int) (a * alpha * 255);
        // 确保 alpha 值在 0-255 范围内
        alphaValue = Math.clamp(alphaValue, 0, 255);
        // 将 Alpha 通道合并到颜色中 (ARGB 格式)
        return (alphaValue << 24) | (baseColor & 0x00FFFFFF);
    }

    public float measureDisplayWidth(float height) {
        String current = currentTextData == null ? null : currentTextData.text;
        String next = isTransitioning && nextTextData != null ? nextTextData.text : null;
        if ((current == null || current.isEmpty()) && (next == null || next.isEmpty())) {
            return 0f;
        }
        if (Objects.equals(current, measuredCurrentText)
                && Objects.equals(next, measuredNextText)
                && height == measuredHeight) {
            return measuredDisplayWidth;
        }
        if (vanillaLineHeight < 0) {
            vanillaLineHeight = Minecraft.getInstance().font.lineHeight;
        }
        float width = 0;
        if (current != null && !current.isEmpty()) {
            width = measureWidth(current);
        }
        if (next != null && !next.isEmpty()) {
            width = Math.max(width, measureWidth(next));
        }
        measuredCurrentText = current;
        measuredNextText = next;
        measuredHeight = height;
        measuredDisplayWidth = width * (height / vanillaLineHeight);
        return measuredDisplayWidth;
    }

    private ModernStringSplitter tryGetSplitter() {
        try {
            return TextLayoutEngine.getInstance().getStringSplitter();
        } catch (Throwable t) {
            return null;
        }
    }

    private String trimToWidth(String text, float maxWidth) {
        if (maxWidth != trimmedCacheMaxWidth) {
            trimmedCache.clear();
            trimmedCacheMaxWidth = maxWidth;
        }
        String cached = trimmedCache.get(text);
        if (cached != null) {
            return cached;
        }
        String trimmed = computeTrimmedText(text, maxWidth);
        if (trimmedCache.size() >= 8) {
            trimmedCache.clear();
        }
        trimmedCache.put(text, trimmed);
        return trimmed;
    }

    private String computeTrimmedText(String text, float maxWidth) {
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
        LEFT,
        CENTER,
        RIGHT
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