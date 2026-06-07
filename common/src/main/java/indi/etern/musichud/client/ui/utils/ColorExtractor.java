package indi.etern.musichud.client.ui.utils;

import com.mojang.blaze3d.platform.NativeImage;
import indi.etern.musichud.client.ui.hud.metadata.ThemedColors;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static indi.etern.musichud.client.ui.utils.UniformDataUtils.interpolateARGB;
import static indi.etern.musichud.client.ui.utils.UniformDataUtils.rgbToHsl;

public class ColorExtractor {
    /**
     * 从 DynamicTexture 提取四种颜色
     *
     * @return int[4] {主色, 次主色, 亮色, 暗色} 均为 ARGB
     */
    public static ThemedColors extractColors(DynamicTexture texture) {
        if (texture == null) return getDefaultColors();

        NativeImage image = texture.getPixels();
        if (image == null) return getDefaultColors();

        int width = image.getWidth();
        int height = image.getHeight();
        if (width == 0 || height == 0) return getDefaultColors();

        // 采样步长（可保持原样或略提高）
        int step = Math.max(1, (int) Math.sqrt((width * height) / 2500.0));
        Map<Integer, Float> colorWeight = new HashMap<>();  // 量化颜色 -> 累计权重

        // 在 extractColors 方法内，完成 colorWeight 统计后，计算总权重
        float totalWeight = 0;

        int bits = 5;  // 可改为4~6
        int shift = 8 - bits;

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                // getPixelRGBA() returns 0xAABBGGRR in 1.21.1 (ABGR memory layout)
                int rgba = image.getPixelRGBA(x, y);
                int a = (rgba >> 24) & 0xFF;
                if (a == 0) continue;
                // Extract actual RGB from ABGR layout
                int r = rgba & 0xFF;
                int g = (rgba >> 8) & 0xFF;
                int b = (rgba >> 16) & 0xFF;
                int rgb = (r << 16) | (g << 8) | b;

                int qr = r >> shift;
                int qg = g >> shift;
                int qb = b >> shift;
                int quantized = (qr << 10) | (qg << 5) | qb;   // 15位整数

                colorWeight.merge(quantized, 1f, Float::sum);
            }
        }

        for (float w : colorWeight.values()) totalWeight += w;
        float minWeightRatio = 0.002f; // 0.5%，可根据需要调整
        float minWeight = totalWeight * minWeightRatio;

        Map<Integer, Integer> quantToRgb = new HashMap<>();
        for (int quant : colorWeight.keySet()) {
            int r = ((quant >> 10) & 0x1F) << shift;
            int g = ((quant >> 5) & 0x1F) << shift;
            int b = (quant & 0x1F) << shift;
            quantToRgb.put(quant, (r << 16) | (g << 8) | b);
        }

        if (colorWeight.isEmpty()) return getDefaultColors();

        final float PRIMARY_SAT_WEIGHT = 0.3f;
        final float SECONDARY_SAT_WEIGHT = 0.2f;
        final float LUM_WEIGHT = 0.1f;
        final float FREQ_WEIGHT = 0.5f;
        final float DIST_EPSILON = 0.001f;

        int bright = 0;
        float bestBrightScore = -1;
        for (Map.Entry<Integer, Float> entry : colorWeight.entrySet()) {
            if (entry.getValue() < minWeight) continue;
            int rgb = quantToRgb.get(entry.getKey());
            float lum = getLuminance(rgb);
            // 亮度范围 [0,1]，直接使用
            if (lum > bestBrightScore) {
                bestBrightScore = lum;
                bright = rgb;
            }
        }

        int dark = -1;
        float bestDarkScore = -1;
        for (Map.Entry<Integer, Float> entry : colorWeight.entrySet()) {
            if (entry.getValue() < minWeight) continue;
            int rgb = quantToRgb.get(entry.getKey());
            float lum = getLuminance(rgb);
            float darkness = 1.0f - lum;  // 暗度，越高越暗
            if (darkness > bestDarkScore) {
                bestDarkScore = darkness;
                dark = rgb;
            }
        }

        int primary = 0;
        float bestPrimaryScore = -1;
        for (Map.Entry<Integer, Float> entry : colorWeight.entrySet()) {
            int quant = entry.getKey();
            float weight = entry.getValue();
            if (weight < minWeight) continue;  // 忽略低频杂色
            int rgb = quantToRgb.get(quant);
            float sat = getSaturation(rgb);
            float lum = getLuminance(rgb);
            float dist1 = colorDistance(bright, rgb);
            float dist2 = colorDistance(dark, rgb);
            float vivid = (float) (Math.pow(sat, PRIMARY_SAT_WEIGHT) * Math.pow(lum, LUM_WEIGHT));
            // sqrt 压制部分高频优势
            float freqFactor = (float) Math.sqrt(weight) / (float) Math.sqrt(totalWeight);
            float score = (vivid + freqFactor * FREQ_WEIGHT) * (dist1 + DIST_EPSILON) * (dist2 + DIST_EPSILON);
            if (score > bestPrimaryScore) {
                bestPrimaryScore = score;
                primary = rgb;
            }
        }

        int secondary = primary;
        float bestSecondaryScore = -1;
        for (Map.Entry<Integer, Float> entry : colorWeight.entrySet()) {
            int quant = entry.getKey();
            float weight = entry.getValue();
            if (weight < minWeight) continue;
            int rgb = quantToRgb.get(quant);
            if (rgb == primary) continue;
            float sat = getSaturation(rgb);
            float lum = getLuminance(rgb);
            float vivid = (float) (Math.pow(sat, SECONDARY_SAT_WEIGHT) * Math.pow(lum, LUM_WEIGHT));
            float dist1 = colorDistance(primary, rgb);
            float dist2 = colorDistance(bright, rgb);
            float dist3 = colorDistance(dark, rgb);
            float freqFactor = (float) Math.sqrt(weight) / (float) Math.sqrt(totalWeight);
            float score = (vivid + freqFactor * FREQ_WEIGHT) * (dist1 + DIST_EPSILON) * (dist2 + DIST_EPSILON) * (dist3 + DIST_EPSILON);
            if (score > bestSecondaryScore && dist1 > 0.1f) {
                bestSecondaryScore = score;
                secondary = rgb;
            }
        }

        return new ThemedColors(
                0xFF000000 | primary,
                0xFF000000 | secondary,
                0xFF000000 | bright,
                0xFF000000 | dark
        );
    }

    private static float getSaturation(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if (max == 0) return 0;
        return (max - min) / (float) max;
    }

    private static float colorDistance(int rgb1, int rgb2) {
        float[] hsl1 = rgbToHsl(rgb1);
        float[] hsl2 = rgbToHsl(rgb2);

        float hueDist = 0;
        if (hsl1[1] > 0.1f && hsl2[1] > 0.1f) {
            hueDist = Math.abs(hsl1[0] - hsl2[0]);
            if (hueDist > 0.5f) hueDist = 1.0f - hueDist;
        }
        hueDist *= 2;

        float satDist = Math.abs(hsl1[1] - hsl2[1]);
        float lumDist = Math.abs(hsl1[2] - hsl2[2]);

        float HUE_WEIGHT = 0.5f;
        float SAT_WEIGHT = 0.2f;
        float LUM_WEIGHT = 0.3f;
        return hueDist * HUE_WEIGHT + satDist * SAT_WEIGHT + lumDist * LUM_WEIGHT;
    }

    private static float getLuminance(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    public static ThemedColors getDefaultColors() {
        return new ThemedColors(
                0xFF1A1A1A, 0xFF202020,
                0XFF202020, 0xFF2A2A2A
        );
    }


    /**
     * 对颜色数组进行饱和度、亮度、Gamma调整
     *
     * @param colors     长度为4的ARGB颜色数组（不透明，alpha将被忽略并重置为0xFF）
     * @param saturation 饱和度乘数（0~2，0=灰度，1=不变，>1增强）
     * @param brightness 亮度乘数（0~2，0=全黑，1=不变，>1提亮）
     * @param contrast   对比度
     * @return 调整后的新颜色数组（ARGB，alpha=0xFF）
     */
    public static ThemedColors adjustColors(ThemedColors colors, float saturation, float brightness, float contrast) {
        if (colors == null) return null;
        return new ThemedColors(
                adjustColorFull(colors.primary, saturation, brightness, contrast),
                adjustColorFull(colors.secondary, saturation, brightness, contrast),
                adjustColorFull(colors.bright, saturation, brightness, contrast),
                adjustColorFull(colors.dark, saturation, brightness, contrast)
        );
    }

    /**
     * 调整单个颜色（完整版）
     */
    private static int adjustColorFull(int argb, float vibrance, float brightness, float contrast) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        // 线性化
        float rLin = (float) Math.pow(r / 255.0, 2.2);
        float gLin = (float) Math.pow(g / 255.0, 2.2);
        float bLin = (float) Math.pow(b / 255.0, 2.2);

        // 自然饱和度
        if (vibrance != 1.0f) {
            float maxC = Math.max(rLin, Math.max(gLin, bLin));
            float minC = Math.min(rLin, Math.min(gLin, bLin));
            float satOrig = maxC - minC;
            if (satOrig > 1e-6) {
                float adjust = (vibrance - 1.0f) * (1.0f - satOrig);
                float newSat = satOrig + adjust;
                newSat = Math.clamp(newSat, 0.0f, 1.0f);
                float scale = newSat / satOrig;
                float gray = 0.2126f * rLin + 0.7152f * gLin + 0.0722f * bLin;
                rLin = gray + (rLin - gray) * scale;
                gLin = gray + (gLin - gray) * scale;
                bLin = gray + (bLin - gray) * scale;
            } // 若 satOrig == 0，保持灰度不变（vibrance 对纯灰度无影响）
        }

        // 亮度
        rLin *= brightness;
        gLin *= brightness;
        bLin *= brightness;

        // 对比度（最后执行，并确保 clamp）
        if (contrast != 1.0f) {
            float midpoint = 0.5f;
            rLin = (rLin - midpoint) * contrast + midpoint;
            gLin = (gLin - midpoint) * contrast + midpoint;
            bLin = (bLin - midpoint) * contrast + midpoint;
        }

        int rOut = (int) (Math.clamp(rLin, 0.0f, 1.0f) * 255);
        int gOut = (int) (Math.clamp(gLin, 0.0f, 1.0f) * 255);
        int bOut = (int) (Math.clamp(bLin, 0.0f, 1.0f) * 255);
        return 0xFF000000 | (rOut << 16) | (gOut << 8) | bOut;
    }

    public static ThemedColors mixBaseColorsWithAlpha(ThemedColors colors, int baseColor, float alpha) {
        if (colors == null) return null;
        return new ThemedColors(
                interpolateARGB(baseColor, colors.primary, alpha),
                interpolateARGB(baseColor, colors.secondary, alpha),
                interpolateARGB(baseColor, colors.bright, alpha),
                interpolateARGB(baseColor, colors.dark, alpha)
        );
    }
}