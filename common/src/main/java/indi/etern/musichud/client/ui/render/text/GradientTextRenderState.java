package indi.etern.musichud.client.ui.render.text;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/** Resolves the font atlas view lazily when the GUI renderer sorts elements. */
public final class GradientTextRenderState implements GuiElementRenderState {
    private final Matrix3x2f pose;
    private final GradientTextRun run;
    private final GradientSpec gradient;
    private final float alpha;
    @Nullable
    private final ScreenRectangle scissorArea;
    @Nullable
    private TextureSetup textureSetup;

    public GradientTextRenderState(Matrix3x2f pose, GradientTextRun run, GradientSpec gradient,
                                   float alpha, @Nullable ScreenRectangle scissorArea) {
        this.pose = pose;
        this.run = run;
        this.gradient = gradient;
        this.alpha = alpha;
        this.scissorArea = scissorArea;
    }

    @Override
    public void buildVertices(@NotNull VertexConsumer consumer, float z) {
        run.writeVertices(consumer, z, pose, gradient, alpha);
    }

    @Override
    public @NotNull RenderPipeline pipeline() {
        return run.pipeline();
    }

    @Override
    public @NotNull TextureSetup textureSetup() {
        if (textureSetup == null) {
            textureSetup = TextureSetup.singleTextureWithLightmap(run.texture());
        }
        return textureSetup;
    }

    @Nullable
    @Override
    public ScreenRectangle scissorArea() {
        return scissorArea;
    }

    @Nullable
    @Override
    public ScreenRectangle bounds() {
        return null;
    }
}
