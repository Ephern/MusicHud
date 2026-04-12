package indi.etern.musichud.client.ui.hud.renderer;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import icyllis.modernui.mc.GradientRectangleRenderState;
import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.client.ui.hud.metadata.*;
import indi.etern.musichud.client.ui.hud.piplines.HudRenderPipelines;
import indi.etern.musichud.client.ui.utils.ColorExtractor;
import lombok.SneakyThrows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3x2f;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class BackgroundRenderer {
    private static volatile BackgroundRenderer instance;
    private final HudUniformWriter uniformWriter = new HudUniformWriter();
    // 颜色缓存，避免每帧重复提取
    private final Cache<DynamicTexture, int[]> colorCache = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.of(3, ChronoUnit.MINUTES))
            .maximumSize(2).build();
    private GpuBufferSlice gpuBufferSlice;
    private HudRenderData currentData;

    public static BackgroundRenderer getInstance() {
        if (instance == null) {
            synchronized (BackgroundRenderer.class) {
                if (instance == null)
                    instance = new BackgroundRenderer();
            }
        }
        return instance;
    }

    public void configure(HudRenderData data) {
        this.currentData = data;
    }

    public void render(GuiGraphics gr) {
        if (currentData == null) {
            return;
        }

        Layout layout = currentData.getLayout();
        BackgroundImage bgImage = currentData.getBackgroundImage();
        DynamicTexture currentTexture = getDynamicTexture(bgImage.currentUnblurredLocation);
        DynamicTexture currentBlurredTexture = getDynamicTexture(bgImage.currentBlurredLocation);

        // 获取过渡状态
        var transitionStatus = HudRenderData.getTransitionStatus();
        var nextData = transitionStatus.getNextData();
        float progress = transitionStatus.getProgress();

        // 获取当前图片和下一张图片的 DynamicTexture
        DynamicTexture nextBlurredTexture = null;
        if (progress > 0 && nextData != null) {
            nextBlurredTexture = getDynamicTexture(nextData.nextBlurred());
        }

        // 提取颜色（带缓存）
        int[] currentColors = getColorsForTexture(currentBlurredTexture);
        int[] nextColors = nextBlurredTexture != null ? getColorsForTexture(nextBlurredTexture) : currentColors;

        // 构建过渡中的 BackgroundColor 对象（用于传给 UniformWriter）
        BackgroundColor interpolatedColor = buildInterpolatedBackgroundColor(currentColors, nextColors, progress);

        currentData.setBackgroundColor(interpolatedColor);
        gpuBufferSlice = uniformWriter.write(currentData, gr);

        // 提交渲染（纯色背景，不需要纹理）
        float halfWidth = layout.width / 2f;
        float halfHeight = layout.height / 2f;

        ScreenRectangle scissor = MuiModApi.get().peekScissorStack(gr);
        MuiModApi.get().submitGuiElementRenderState(gr,
                new GradientRectangleRenderState(
                        HudRenderPipelines.BACKGROUND,
                        TextureSetup.noTexture(),
                        new Matrix3x2f(gr.pose()),
                        -halfWidth, -halfHeight, halfWidth, halfHeight,
                        0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF,
                        scissor
                ));
    }

    /**
     * 获取纹理的颜色数组，带缓存
     */
    @SneakyThrows
    private int[] getColorsForTexture(DynamicTexture texture) {
        if (texture == null) {
            return ColorExtractor.getDefaultColors();
        }
        return colorCache.get(texture, () -> ColorExtractor.adjustColors(ColorExtractor.extractColors(texture), 1.1f, 0.55f, 0.7f));
    }

    /**
     * 构建插值后的 BackgroundColor 对象
     */
    private BackgroundColor buildInterpolatedBackgroundColor(int[] from, int[] to, float t) {
        if (t <= 0.01f) return new BackgroundColor(from[0], from[1], from[2], from[3]);
        if (t >= 0.99f) return new BackgroundColor(to[0], to[1], to[2], to[3]);

        int tl = interpolateARGB(from[0], to[0], t);
        int tr = interpolateARGB(from[1], to[1], t);
        int br = interpolateARGB(from[2], to[2], t);
        int bl = interpolateARGB(from[3], to[3], t);
        return new BackgroundColor(tl, tr, br, bl);
    }

    private int interpolateARGB(int a, int b, float t) {
        int aA = (a >> 24) & 0xFF;
        int aR = (a >> 16) & 0xFF;
        int aG = (a >> 8) & 0xFF;
        int aB = a & 0xFF;

        int bA = (b >> 24) & 0xFF;
        int bR = (b >> 16) & 0xFF;
        int bG = (b >> 8) & 0xFF;
        int bB = b & 0xFF;

        int rA = (int) (aA + (bA - aA) * t);
        int rR = (int) (aR + (bR - aR) * t);
        int rG = (int) (aG + (bG - aG) * t);
        int rB = (int) (aB + (bB - aB) * t);

        return (rA << 24) | (rR << 16) | (rG << 8) | rB;
    }

    private DynamicTexture getDynamicTexture(ResourceLocation imageLocation) {
        if (imageLocation == null) return null;
        AbstractTexture texture = Minecraft.getInstance()
                .getTextureManager()
                .getTexture(imageLocation);
        if (texture instanceof DynamicTexture dynamicTexture) {
            return dynamicTexture;
        }
        return null;
    }

    public void updateRenderPass(RenderPass renderPass) {
        if (gpuBufferSlice != null) {
            renderPass.setUniform("HudBackgroundParams", gpuBufferSlice);
        }
    }
}