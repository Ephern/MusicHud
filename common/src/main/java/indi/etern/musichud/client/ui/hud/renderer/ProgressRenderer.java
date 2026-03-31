package indi.etern.musichud.client.ui.hud.renderer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import icyllis.modernui.mc.GradientRectangleRenderState;
import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.client.ui.hud.metadata.HudRenderData;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import indi.etern.musichud.client.ui.hud.metadata.ProgressBar;
import indi.etern.musichud.client.ui.hud.piplines.HudRenderPipelines;
import lombok.Getter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static indi.etern.musichud.client.ui.utils.UniformDataUtils.colorToVector;

public class ProgressRenderer {
    private static volatile ProgressRenderer instance;
    private GpuBufferSlice gpuBufferSlice;
    @Getter
    private HudRenderData currentData;

    public static ProgressRenderer getInstance() {
        if (instance == null) {
            synchronized (ProgressRenderer.class) {
                if (instance == null)
                    instance = new ProgressRenderer();
            }
        }
        return instance;
    }

    public void configure(HudRenderData data) {
        this.currentData = data;
    }

    public void render(GuiGraphics gr) {
        if (currentData == null || currentData.getProgressBar() == null || currentData.getLayout().height <= 0) {
            return;
        }

        gpuBufferSlice = write(currentData, gr);

        Layout layout = currentData.getLayout();
        float halfWidth = layout.width / 2f;
        float halfHeight = layout.height / 2f;

        ScreenRectangle scissor = MuiModApi.get().peekScissorStack(gr);

        // 获取进度条颜色
        ProgressBar progressBar = currentData.getProgressBar();

        MuiModApi.get().submitGuiElementRenderState(gr,
                new GradientRectangleRenderState(
                        HudRenderPipelines.PROGRESS_BAR,
                        TextureSetup.noTexture(),
                        new Matrix3x2f(gr.pose()),
                        -halfWidth, -halfHeight, halfWidth, halfHeight,
                        progressBar.fillColorLeft, progressBar.fillColorRight, progressBar.backgroundColor, 0,
                        scissor
                ));
    }

    public void updateRenderPass(RenderPass renderPass) {
        if (gpuBufferSlice != null) {
            renderPass.setUniform("HudProgressParams", gpuBufferSlice);
        }
    }

    public GpuBufferSlice write(HudRenderData data, GuiGraphics graphics) {
        Layout layout = data.getLayout();
        ProgressBar progressBar = data.getProgressBar();

        if (progressBar == null) {
            return null;
        }

        // 计算局部变换矩阵
        Matrix3x2f localMatrix = new Matrix3x2f();
        Layout.AbsolutePosition absolutePosition = layout.calcAbsoluteCenterPosition(graphics);
        localMatrix.translate(absolutePosition.x(), absolutePosition.y());

        // 准备 uniform 参数
        Vector4f progressData = new Vector4f(
                layout.width / 2f,
                layout.height / 2f,
                layout.radius,
                progressBar.getProgress()
        );

        // 颜色数据(这里简化处理,实际可能需要更复杂的颜色编码)
        //noinspection SuspiciousNameCombination
        Vector3f gradientOffsets = new Vector3f(
                progressBar.gradientLength,
                progressBar.gradientRightOffset,
                data.getProgressBar().transitionBorderRate
        );

        Matrix4f gradientColorsMatrix = new Matrix4f();
        gradientColorsMatrix.setColumn(0, colorToVector(progressBar.fillColorLeft));
        gradientColorsMatrix.setColumn(1, colorToVector(progressBar.fillColorRight));
        gradientColorsMatrix.setColumn(2, colorToVector(progressBar.backgroundColor));
        gradientColorsMatrix.setColumn(3, new Vector4f());

        // 使用 writeTransform 写入数据
        return RenderSystem.getDynamicUniforms().writeTransform(
                new Matrix4f().mul(localMatrix),
                progressData,
                gradientOffsets,
                gradientColorsMatrix
        );
    }
}
