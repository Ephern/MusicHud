package indi.etern.musichud.client.ui.hud.renderer.text;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/**
 * A {@link GuiElementRenderState} that renders one {@link GradientTextRun} with a horizontal
 * gradient. Submitted through {@code GuiRenderState.submitGlyphToCurrentLayer}, exactly like
 * the text engines submit their own glyph runs, so it participates in the same batching and
 * scissor handling of {@code GuiRenderer}.
 * <p>
 * The texture setup is resolved lazily when the GUI renderer starts sorting elements; that is
 * after the engines have baked their glyphs for this frame, so the font atlas view cannot go
 * stale before the draw.
 */
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
    public void buildVertices(@NotNull VertexConsumer consumer) {
        run.writeVertices(consumer, pose, gradient, alpha);
    }

    @Override
    public @NotNull RenderPipeline pipeline() {
        return run.pipeline();
    }

    @Override
    public @NotNull TextureSetup textureSetup() {
        if (textureSetup == null) {
            textureSetup = TextureSetup.singleTextureWithLightmap(run.texture(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
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
