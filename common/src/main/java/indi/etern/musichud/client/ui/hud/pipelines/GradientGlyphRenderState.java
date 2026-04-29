package indi.etern.musichud.client.ui.hud.pipelines;

import com.mojang.blaze3d.vertex.VertexConsumer;
import icyllis.modernui.graphics.MathUtil;
import lombok.Getter;
import lombok.NonNull;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;

import java.util.Objects;

public class GradientGlyphRenderState /*implements GuiElementRenderState */{
    @Getter
    private final Matrix3x2f pose;
    private final BakedGlyph.GlyphInstance instance;
    @Nullable
    private final ScreenRectangle scissorArea;

    public GradientGlyphRenderState(Matrix3x2f pose, BakedGlyph.GlyphInstance instance,
                                     @Nullable ScreenRectangle scissorArea) {
        this.pose = pose;
        this.instance = instance;
        this.scissorArea = scissorArea;
    }

//    @Override
    public void buildVertices(@NonNull VertexConsumer consumer, float z) {
        Matrix4f matrix4f = new Matrix4f().mul(pose).translate(0.0F, 0.0F, z);
        instance.glyph().renderChar(instance, matrix4f, consumer, 15728880, true);
    }

//    @Override
    /*public @NonNull RenderPipeline pipeline() {
        return HudRenderPipelines.TEXT_GRADIENT;
    }*/

//    @Override
    public @NonNull TextureSetup textureSetup() {
        return TextureSetup.singleTextureWithLightmap(Objects.requireNonNull(instance.glyph().textureView()));
    }

    @Nullable
//    @Override
    public ScreenRectangle bounds() {
        float left = instance.left();
        float right = instance.right();
        float top = instance.top();
        float bottom = instance.bottom();

        float m00 = pose.m00();
        float m01 = pose.m01();
        float m10 = pose.m10();
        float m11 = pose.m11();
        float m20 = pose.m20();
        float m21 = pose.m21();

        float x1 = m00 * left + m10 * top + m20;
        float y1 = m01 * left + m11 * top + m21;
        float x2 = m00 * right + m10 * top + m20;
        float y2 = m01 * right + m11 * top + m21;
        float x3 = m00 * left + m10 * bottom + m20;
        float y3 = m01 * left + m11 * bottom + m21;
        float x4 = m00 * right + m10 * bottom + m20;
        float y4 = m01 * right + m11 * bottom + m21;

        int L = (int) Math.floor(MathUtil.min(x1, x2, x3, x4));
        int T = (int) Math.floor(MathUtil.min(y1, y2, y3, y4));
        int R = (int) Math.ceil(MathUtil.max(x1, x2, x3, x4));
        int B = (int) Math.ceil(MathUtil.max(y1, y2, y3, y4));

        if (L >= R || T >= B) return null;
        return new ScreenRectangle(L, T, R - L, B - T);
    }

    @Nullable
//    @Override
    public ScreenRectangle scissorArea() {
        return scissorArea;
    }
}
