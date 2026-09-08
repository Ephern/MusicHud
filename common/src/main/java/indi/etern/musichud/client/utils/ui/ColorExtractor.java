package indi.etern.musichud.client.utils.ui;

import com.mojang.blaze3d.platform.NativeImage;
import indi.etern.musichud.client.ui.hud.metadata.ThemedColors;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.util.HashMap;
import java.util.Map;

import static indi.etern.musichud.client.utils.ui.UniformDataUtils.interpolateARGB;

public class ColorExtractor {
    // Duplicate threshold for theme colors (normalized redmean distance).
    // Priority on collision: primary -> secondary -> bright -> dark.
    private static final float DUPLICATE_DIST = 0.02f;
    // Floor for each color share so no channel vanishes in the shader.
    private static final float MIN_SHARE = 0.02f;

    /**
     * 从 DynamicTexture 提取四种颜色
     * <p>
     * RGB 为提取到的主题色，alpha 为该色在四色中的整体占比，
     * 四个 alpha 之和为 1.0（归一化到 0~255）。
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

        // 采样步长
        int step = Math.max(1, (int) Math.sqrt((width * height) / 6400.0));
        Map<Integer, Float> colorWeight = new HashMap<>();  // 量化颜色 -> 累计权重

        // 在 extractColors 方法内，完成 colorWeight 统计后，计算总权重
        float totalWeight = 0;

        int bits = 5;  // 4~6
        int shift = 8 - bits;

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int argb = image.getPixel(x, y);
                if ((argb >>> 24) == 0) continue;
                int rgb = argb & 0x00FFFFFF;

                // 颜色量化
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

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
        final float LUM_WEIGHT = 0.3f;
        final float FREQ_WEIGHT = 0.5f;
        final float DIST_EPSILON = 0.001f;
        final float BD_SAT_TARGET = 0.15f;
        final float BD_SAT_SPREAD = 0.7f;

        int bright = 0;
        float bestBrightScore = -1;
        for (Map.Entry<Integer, Float> entry : colorWeight.entrySet()) {
            if (entry.getValue() < minWeight) continue;
            int rgb = quantToRgb.get(entry.getKey());
            float lum = getLuminance(rgb);
            float sat = getSaturation(rgb);
            float satPenalty = (sat - BD_SAT_TARGET) * (sat - BD_SAT_TARGET) / (BD_SAT_SPREAD * BD_SAT_SPREAD);
            float satScore = Math.max(0.1f, 1.0f - satPenalty);
            float score = lum * satScore;
            if (score > bestBrightScore) {
                bestBrightScore = score;
                bright = rgb;
            }
        }

        int dark = -1;
        float bestDarkScore = -1;
        for (Map.Entry<Integer, Float> entry : colorWeight.entrySet()) {
            if (entry.getValue() < minWeight) continue;
            int rgb = quantToRgb.get(entry.getKey());
            float lum = getLuminance(rgb);
            float sat = getSaturation(rgb);
            float darkness = 1.0f - lum;
            float satPenalty = (sat - BD_SAT_TARGET) * (sat - BD_SAT_TARGET) / (BD_SAT_SPREAD * BD_SAT_SPREAD);
            float satScore = Math.max(0.1f, 1.0f - satPenalty);
            float score = darkness * satScore;
            if (score > bestDarkScore) {
                bestDarkScore = score;
                dark = rgb;
            }
        }

        int primary = 0;
        float bestPrimaryScore = -1;
        int primaryFallback = 0;
        float bestPrimaryFallbackScore = -1;
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
            if (dist2 > 0.08f) {
                if (score > bestPrimaryScore) {
                    bestPrimaryScore = score;
                    primary = rgb;
                }
            } else if (score > bestPrimaryFallbackScore) {
                bestPrimaryFallbackScore = score;
                primaryFallback = rgb;
            }
        }
        if (primary == 0) primary = primaryFallback;

        int secondary = primary;
        float bestSecondaryScore = -1;
        int secondaryFallback = primary;
        float bestSecondaryFallbackScore = -1;
        for (Map.Entry<Integer, Float> entry : colorWeight.entrySet()) {
            int quant = entry.getKey();
            float weight = entry.getValue();
            if (weight < minWeight) continue;
            int rgb = quantToRgb.get(quant);
            if (isDuplicate(rgb, primary)) continue;
            float sat = getSaturation(rgb);
            float lum = getLuminance(rgb);
            float vivid = (float) (Math.pow(sat, SECONDARY_SAT_WEIGHT) * Math.pow(lum, LUM_WEIGHT));
            float dist1 = colorDistance(primary, rgb);
            float dist2 = colorDistance(bright, rgb);
            float dist3 = colorDistance(dark, rgb);
            float freqFactor = (float) Math.sqrt(weight) / (float) Math.sqrt(totalWeight);
            float score = (vivid + freqFactor * FREQ_WEIGHT) * (dist1 + DIST_EPSILON) * (dist2 + DIST_EPSILON) * (dist3 + DIST_EPSILON);
            if (dist1 > 0.1f && dist2 > 0.08f) {
                if (score > bestSecondaryScore) {
                    bestSecondaryScore = score;
                    secondary = rgb;
                }
            } else if (dist1 > 0.1f && score > bestSecondaryFallbackScore) {
                bestSecondaryFallbackScore = score;
                secondaryFallback = rgb;
            }
        }
        if (isDuplicate(secondary, primary)) secondary = secondaryFallback;

        // Deduplicate lower-priority colors against higher-priority ones.
        // Priority: primary -> secondary -> bright -> dark.
        if (isDuplicate(bright, primary, secondary)) {
            int reselected = reselectBright(colorWeight, quantToRgb, minWeight, primary, secondary);
            if (reselected != Integer.MIN_VALUE) bright = reselected;
        }
        if (isDuplicate(dark, primary, secondary, bright)) {
            int reselected = reselectDark(colorWeight, quantToRgb, minWeight, primary, secondary, bright);
            if (reselected != Integer.MIN_VALUE) dark = reselected;
        }

        // Winner-takes-all cluster vote: each bucket counts toward its nearest
        // theme color, so shares reflect true area proportions in the image.
        float[] cluster = clusterWeights(primary, secondary, bright, dark, colorWeight, quantToRgb, minWeight);
        float sum = cluster[0] + cluster[1] + cluster[2] + cluster[3];
        if (sum <= 1e-6f) return getDefaultColors();

        // Normalize to shares, apply floor, then renormalize so the sum is 1.0.
        float aP = cluster[0] / sum;
        float aS = cluster[1] / sum;
        float aB = cluster[2] / sum;
        float aD = cluster[3] / sum;
        aP = Math.max(aP, MIN_SHARE);
        aS = Math.max(aS, MIN_SHARE);
        aB = Math.max(aB, MIN_SHARE);
        aD = Math.max(aD, MIN_SHARE);
        float sum2 = aP + aS + aB + aD;
        aP /= sum2;
        aS /= sum2;
        aB /= sum2;
        aD /= sum2;

        int[] alphas = sharesToAlphaBytes(aP, aS, aB, aD);
        return new ThemedColors(
                (alphas[0] << 24) | (primary & 0x00FFFFFF),
                (alphas[1] << 24) | (secondary & 0x00FFFFFF),
                (alphas[2] << 24) | (bright & 0x00FFFFFF),
                (alphas[3] << 24) | (dark & 0x00FFFFFF)
        );
    }

    // Check whether rgb is a perceptual duplicate of any taken color.
    private static boolean isDuplicate(int rgb, int... taken) {
        for (int t : taken) {
            if (colorDistance(rgb, t) < DUPLICATE_DIST) return true;
        }
        return false;
    }

    // Winner-takes-all cluster vote over quantized buckets.
    // Every bucket with weight >= minWeight counts once toward its nearest theme
    // color. Ties resolve by priority primary > secondary > bright > dark via
    // strict-less comparison. Order: {primary, secondary, bright, dark}.
    private static float[] clusterWeights(int primary, int secondary, int bright, int dark,
                                          Map<Integer, Float> colorWeight, Map<Integer, Integer> quantToRgb,
                                          float minWeight) {
        float[] cluster = new float[4];
        int[] themes = new int[]{primary, secondary, bright, dark};
        for (Map.Entry<Integer, Float> entry : colorWeight.entrySet()) {
            float weight = entry.getValue();
            if (weight < minWeight) continue;
            Integer mapped = quantToRgb.get(entry.getKey());
            if (mapped == null) continue;
            int rgb = mapped;
            int best = 0;
            float bestDist = colorDistance(rgb, themes[0]);
            for (int i = 1; i < 4; i++) {
                float d = colorDistance(rgb, themes[i]);
                if (d < bestDist - 1e-6f) {
                    bestDist = d;
                    best = i;
                }
            }
            cluster[best] += weight;
        }
        return cluster;
    }

    // Convert four normalized shares to alpha bytes with exact total 255.
    // Uses largest-remainder so rounding never breaks the sum.
    public static int[] sharesToAlphaBytes(float... shares) {
        int n = shares.length;
        int[] out = new int[n];
        float[] frac = new float[n];
        int sum = 0;
        for (int i = 0; i < n; i++) {
            int floored = (int) Math.floor(shares[i] * 255f);
            floored = Math.clamp(floored, 1, 255);
            out[i] = floored;
            frac[i] = shares[i] * 255f - floored;
            sum += out[i];
        }
        int diff = 255 - sum;
        while (diff != 0) {
            int idx = -1;
            if (diff > 0) {
                float best = Float.NEGATIVE_INFINITY;
                for (int i = 0; i < n; i++) {
                    if (out[i] >= 255) continue;
                    if (frac[i] > best) {
                        best = frac[i];
                        idx = i;
                    }
                }
                if (idx < 0) break;
                out[idx]++;
                frac[idx] = Float.NEGATIVE_INFINITY;
                diff--;
            } else {
                float worst = Float.POSITIVE_INFINITY;
                for (int i = 0; i < n; i++) {
                    if (out[i] <= 1) continue;
                    if (frac[i] < worst) {
                        worst = frac[i];
                        idx = i;
                    }
                }
                if (idx < 0) break;
                out[idx]--;
                frac[idx] = Float.POSITIVE_INFINITY;
                diff++;
            }
        }
        return out;
    }

    // Re-select bright excluding taken colors; MIN_VALUE if no candidate.
    private static int reselectBright(Map<Integer, Float> colorWeight, Map<Integer, Integer> quantToRgb,
                                      float minWeight, int... taken) {
        final float satTarget = 0.15f;
        final float satSpread = 0.7f;
        int best = Integer.MIN_VALUE;
        float bestScore = -1f;
        for (Map.Entry<Integer, Float> entry : colorWeight.entrySet()) {
            if (entry.getValue() < minWeight) continue;
            int rgb = quantToRgb.get(entry.getKey());
            if (isDuplicate(rgb, taken)) continue;
            float lum = getLuminance(rgb);
            float sat = getSaturation(rgb);
            float penalty = (sat - satTarget) * (sat - satTarget) / (satSpread * satSpread);
            float score = lum * Math.max(0.1f, 1.0f - penalty);
            if (score > bestScore) {
                bestScore = score;
                best = rgb;
            }
        }
        return best;
    }

    // Re-select dark excluding taken colors; MIN_VALUE if no candidate.
    private static int reselectDark(Map<Integer, Float> colorWeight, Map<Integer, Integer> quantToRgb,
                                    float minWeight, int... taken) {
        final float satTarget = 0.15f;
        final float satSpread = 0.7f;
        int best = Integer.MIN_VALUE;
        float bestScore = -1f;
        for (Map.Entry<Integer, Float> entry : colorWeight.entrySet()) {
            if (entry.getValue() < minWeight) continue;
            int rgb = quantToRgb.get(entry.getKey());
            if (isDuplicate(rgb, taken)) continue;
            float darkness = 1.0f - getLuminance(rgb);
            float sat = getSaturation(rgb);
            float penalty = (sat - satTarget) * (sat - satTarget) / (satSpread * satSpread);
            float score = darkness * Math.max(0.1f, 1.0f - penalty);
            if (score > bestScore) {
                bestScore = score;
                best = rgb;
            }
        }
        return best;
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

    /**
     * Weighted color distance using the "redmean" formula, widely used in
     * image quantization (e.g. ImageMagick). Applies perceptual weighting
     * in sRGB space: higher weight on green luminosity and adaptive red/blue
     * weighting by mean red level.
     *
     * @return perceptual distance normalized to [0, 1]
     */
    private static float colorDistance(int rgb1, int rgb2) {
        int r1 = (rgb1 >> 16) & 0xFF;
        int g1 = (rgb1 >> 8) & 0xFF;
        int b1 = rgb1 & 0xFF;
        int r2 = (rgb2 >> 16) & 0xFF;
        int g2 = (rgb2 >> 8) & 0xFF;
        int b2 = rgb2 & 0xFF;

        int rMean = (r1 + r2) >>> 1;
        int dR = r1 - r2;
        int dG = g1 - g2;
        int dB = b1 - b2;

        return (float) Math.sqrt(
                ((512 + rMean) * dR * dR) / 256.0 +
                        4 * dG * dG +
                        ((767 - rMean) * dB * dB) / 256.0
        ) / 765.0f;
    }

    private static float getLuminance(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    public static ThemedColors getDefaultColors() {
        // Alpha values are shares summing to 255 (64 + 64 + 64 + 63), i.e. total 1.0.
        return new ThemedColors(
                0x401A1A1A, 0x40202020,
                0x40202020, 0x3F2A2A2A
        );
    }


    /**
     * 对颜色数组进行饱和度、亮度、Gamma调整
     * <p>
     * 只调整 RGB，alpha（占比权重）原样保留。
     *
     * @param colors     长度为4的ARGB颜色数组
     * @param saturation 饱和度乘数（0~2，0=灰度，1=不变，>1增强）
     * @param brightness 亮度乘数（0~2，0=全黑，1=不变，>1提亮）
     * @param contrast   对比度
     * @return 调整后的新颜色数组（ARGB，alpha 保留输入值）
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
        // Preserve the share stored in alpha.
        return (argb & 0xFF000000) | (rOut << 16) | (gOut << 8) | bOut;
    }

    /**
     * Mix each theme color towards baseColor in RGB only.
     * Alpha channels hold share weights and keep their original distribution.
     */
    public static ThemedColors mixBaseColorsWithAlpha(ThemedColors colors, int baseColor, float alpha) {
        if (colors == null) return null;
        return new ThemedColors(
                mixRgbKeepAlpha(baseColor, colors.primary, alpha),
                mixRgbKeepAlpha(baseColor, colors.secondary, alpha),
                mixRgbKeepAlpha(baseColor, colors.bright, alpha),
                mixRgbKeepAlpha(baseColor, colors.dark, alpha)
        );
    }

    // Interpolate RGB channels only, keep alpha from dst (the theme color).
    private static int mixRgbKeepAlpha(int baseColor, int themeColor, float alpha) {
        int opaqueBase = 0xFF000000 | (baseColor & 0x00FFFFFF);
        int opaqueTheme = 0xFF000000 | (themeColor & 0x00FFFFFF);
        int mixed = interpolateARGB(opaqueBase, opaqueTheme, alpha);
        return (themeColor & 0xFF000000) | (mixed & 0x00FFFFFF);
    }
}