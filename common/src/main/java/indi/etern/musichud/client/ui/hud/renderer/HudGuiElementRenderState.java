package indi.etern.musichud.client.ui.hud.renderer;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import indi.etern.musichud.client.ui.hud.pipelines.HudUniform;
import lombok.NonNull;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/**
 * 1.21.6-1.21.8 adapter: turns a neutral {@link indi.etern.musichud.client.ui.hud.pipelines.HudRenderState}
 * into a {@link GuiElementRenderState} consumable by {@code GuiRenderer}.
 */
public class HudGuiElementRenderState implements GuiElementRenderState {
    private final RenderPipeline pipeline;
    private final TextureSetup textureSetup;
    private final Matrix3x2f pose;
    private final float width;
    private final float height;
    @Nullable
    private final ScreenRectangle bounds;
    @Nullable
    private final String elementKey;
    private final HudUniform[] uniforms;

    public HudGuiElementRenderState(@NonNull RenderPipeline pipeline,
                                    @NonNull TextureSetup textureSetup,
                                    @NonNull Matrix3x2f pose,
                                    float width,
                                    float height,
                                    @Nullable ScreenRectangle bounds,
                                    @Nullable String elementKey,
                                    HudUniform[] uniforms) {
        this.pipeline = pipeline;
        this.textureSetup = textureSetup;
        this.pose = pose;
        this.width = width;
        this.height = height;
        this.bounds = bounds;
        this.elementKey = elementKey;
        this.uniforms = uniforms;
    }

    @Nullable
    public String elementKey() {
        return elementKey;
    }

    public HudUniform[] uniforms() {
        return uniforms;
    }

    @Override
    public void buildVertices(VertexConsumer consumer, float z) {
        float left = -width / 2f;
        float right = width / 2f;
        float top = -height / 2f;
        float bottom = height / 2f;
        consumer.addVertexWith2DPose(pose, right, bottom, z).setColor(-1);
        consumer.addVertexWith2DPose(pose, right, top, z).setColor(-1);
        consumer.addVertexWith2DPose(pose, left, top, z).setColor(-1);
        consumer.addVertexWith2DPose(pose, left, bottom, z).setColor(-1);
    }

    @Override
    public RenderPipeline pipeline() {
        return pipeline;
    }

    @Override
    public TextureSetup textureSetup() {
        return textureSetup;
    }

    @Nullable
    @Override
    public ScreenRectangle scissorArea() {
        return null;
    }

    @Nullable
    @Override
    public ScreenRectangle bounds() {
        return bounds;
    }
}
