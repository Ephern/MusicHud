package indi.etern.musichud.client.ui.hud.metadata;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static indi.etern.musichud.client.ui.utils.UniformDataUtils.colorToVector;

public class HudUniformWriter {
    long initTimestamp;

    public HudUniformWriter() {
        initTimestamp = System.currentTimeMillis();
    }

    /**
     * 将渲染数据写入 uniform buffer
     * @return GpuBufferSlice 用于后续绑定
     */
    public GpuBufferSlice write(HudRenderData data, GuiGraphics graphics) {
        Layout layout = data.getLayout();
        BackgroundColor bgColor = data.getBackgroundColor();
        BackgroundImage bgImage = data.getBackgroundImage();

        // 计算派生参数
        Layout.AbsolutePosition absolutePosition = layout.calcAbsoluteCenterPosition(graphics);

        // 创建局部变换矩阵
        org.joml.Matrix3x2f localMatrix = new org.joml.Matrix3x2f();
        localMatrix.translate(absolutePosition.x(), absolutePosition.y());
        Matrix4f transformMat4 = new Matrix4f().mul(localMatrix);

        // 构建参数
        Vector4f appearanceData = new Vector4f(
                layout.width/2,
                layout.height/2,
                Math.min(Math.min(layout.width, layout.height)/2, layout.radius),
                (System.currentTimeMillis() - initTimestamp) / 1000.0f
        );

        var transitionStatus = HudRenderData.getTransitionStatus();
        var nextData = transitionStatus.getNextData();
        Vector3f backgroundImageTransitionData = new Vector3f(
                bgImage != null ? transitionStatus.getProgress() : 0.0f,
                bgImage != null ? bgImage.currentAspect : 1.0f,
                bgImage != null && nextData != null ? nextData.nextAspect() : 1.0f
        );

        Matrix4f colorMatrix = bgColor == null ? new Matrix4f() : buildColorMatrix(bgColor);

        return RenderSystem.getDynamicUniforms().writeTransform(
                transformMat4,
                appearanceData,
                backgroundImageTransitionData,
                colorMatrix,
                0
        );
    }

    private Matrix4f buildColorMatrix(BackgroundColor bgColor) {
        Matrix4f matrix = new Matrix4f();
        matrix.setColumn(0, colorToVector(bgColor.color1));
        matrix.setColumn(1, colorToVector(bgColor.color2));
        matrix.setColumn(2, colorToVector(bgColor.color3));
        matrix.setColumn(3, colorToVector(bgColor.color4));
        return matrix;
    }

    private static final Matrix4f emptyColorMatrix = new Matrix4f();
    static {
        emptyColorMatrix.setColumn(0, colorToVector(0));
        emptyColorMatrix.setColumn(1, colorToVector(0));
        emptyColorMatrix.setColumn(2, colorToVector(0));
        emptyColorMatrix.setColumn(3, colorToVector(0));
    }
}
