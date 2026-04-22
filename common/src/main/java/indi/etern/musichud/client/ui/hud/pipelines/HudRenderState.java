package indi.etern.musichud.client.ui.hud.pipelines;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import indi.etern.musichud.client.ui.utils.UniformDataUtils;
import lombok.NonNull;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public record HudRenderState(
        @NonNull RenderPipeline pipeline,
        @NonNull TextureSetup textureSetup,
        @NonNull Matrix3x2f pose,
        float width,
        float height,
        @Nullable ScreenRectangle bounds   // bounds 只用于裁剪，不作为顶点坐标
) implements GuiElementRenderState {

    public HudRenderState(@NonNull RenderPipeline pipeline,
                          @NonNull TextureSetup textureSetup,
                          @NonNull Matrix3x2f pose,
                          float width, float height) {
        this(pipeline, textureSetup, pose, width, height,
                UniformDataUtils.getBounds(-width / 2f, -height / 2f, width / 2f, height / 2f, pose));
    }

    @Override
    public void buildVertices(@NonNull VertexConsumer consumer, float z) {
        float left = -width / 2f;
        float right = width / 2f;
        float top = -height / 2f;
        float bottom = height / 2f;

        consumer.addVertexWith2DPose(pose, right, bottom, z);
        consumer.addVertexWith2DPose(pose, right, top, z);
        consumer.addVertexWith2DPose(pose, left, top, z);
        consumer.addVertexWith2DPose(pose, left, bottom, z);
    }

    @Nullable
    @Override
    public ScreenRectangle scissorArea() {
        return null;
    }
}