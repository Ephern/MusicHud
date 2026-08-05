package indi.etern.musichud.client.ui.hud.pipelines;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import indi.etern.musichud.client.utils.ui.UniformDataUtils;
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
        @Nullable ScreenRectangle bounds,
        HudUniform[] uniforms
) implements GuiElementRenderState {

    public HudRenderState(@NonNull RenderPipeline pipeline,
                          @NonNull TextureSetup textureSetup,
                          @NonNull Matrix3x2f pose,
                          @NonNull Layout layout,
                          HudUniform... uniforms) {
        this(pipeline, textureSetup, pose, layout.getWidth(), layout.getHeight(),
                UniformDataUtils.getBounds(-layout.getWidth() / 2f, -layout.getHeight() / 2f, layout.getWidth() / 2f, layout.getHeight() / 2f, pose), uniforms);
    }

    @Override
    public void buildVertices(@NonNull VertexConsumer consumer, float z) {
        float left = -width / 2f;
        float right = width / 2f;
        float top = -height / 2f;
        float bottom = height / 2f;
        consumer.addVertexWith2DPose(pose, right, bottom, z).setColor(-1);
        consumer.addVertexWith2DPose(pose, right, top, z).setColor(-1);
        consumer.addVertexWith2DPose(pose, left, top, z).setColor(-1);
        consumer.addVertexWith2DPose(pose, left, bottom, z).setColor(-1);
    }

    @Nullable
    @Override
    public ScreenRectangle scissorArea() {
        return null;
    }
}
