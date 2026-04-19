package indi.etern.musichud.client.ui.hud.piplines;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import indi.etern.musichud.client.ui.utils.UniformDataUtils;
import lombok.NonNull;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;


public record ProgressBarRenderState(
        @NonNull RenderPipeline pipeline,
        @NonNull TextureSetup textureSetup,
        @NonNull Matrix3x2f pose,
        float width,
        float height,
        int leftFillColor,      // 左下/右下？根据你的逻辑调整
        int rightFillColor,     // 右上
        int backgroundColor,    // 左上/左下
        @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {

    public ProgressBarRenderState(@NonNull RenderPipeline pipeline,
                                  @NonNull TextureSetup textureSetup,
                                  @NonNull Matrix3x2f pose,
                                  float width, float height,
                                  int leftFillColor,
                                  int rightFillColor,
                                  int backgroundColor) {
        this(pipeline, textureSetup, pose, width, height,
                leftFillColor, rightFillColor, backgroundColor,
                UniformDataUtils.getBounds(-width / 2f, -height / 2f, width / 2f, height / 2f, pose));
    }

    @Override
    public void buildVertices(@NonNull VertexConsumer consumer, float z) {
        float left = -width / 2f;
        float right = width / 2f;
        float top = -height / 2f;
        float bottom = height / 2f;

        consumer.addVertexWith2DPose(pose, right, bottom, z).setColor(leftFillColor);
        consumer.addVertexWith2DPose(pose, right, top, z).setColor(rightFillColor);
        consumer.addVertexWith2DPose(pose, left, top, z).setColor(backgroundColor);
        consumer.addVertexWith2DPose(pose, left, bottom, z).setColor(0xFFFFFFFF);
    }

    @Nullable
    @Override
    public ScreenRectangle scissorArea() {
        return null;
    }
}