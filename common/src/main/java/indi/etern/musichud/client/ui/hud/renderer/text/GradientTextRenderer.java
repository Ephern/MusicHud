package indi.etern.musichud.client.ui.hud.renderer.text;

import icyllis.modernui.mc.FontResourceManager;
import icyllis.modernui.mc.text.TextLayoutEngine;
import indi.etern.musichud.MusicHud;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

import java.util.List;

/**
 * Dispatches gradient text geometry extraction to the same engine that would render the text:
 * the Modern UI text engine when it has taken over, the vanilla text pipeline otherwise. This
 * keeps glyph shapes, atlas, pipeline selection and metrics identical to normal text drawing.
 */
public final class GradientTextRenderer {

    private GradientTextRenderer() {
    }

    public static List<GradientTextRenderState> build(Font font, String text, int x, int y,
                                                      GradientSpec gradient, float alpha,
                                                      @Nullable ScreenRectangle scissor, Matrix3x2f pose) {
        try {
            if (FontResourceManager.getInstance() instanceof TextLayoutEngine) {
                return ModernTextGeometry.build(font, text, x, y, gradient, alpha, scissor, pose);
            }
        } catch (Throwable t) {
            // Modern UI absent or its API changed: fall through to the vanilla pipeline
            MusicHud.getLogger(GradientTextRenderer.class).debug("Modern gradient text geometry failed", t);
        }
        return VanillaTextGeometry.build(font, text, x, y, gradient, alpha, scissor, pose);
    }
}
