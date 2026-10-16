package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.hud.pipelines.HudRenderState;
import indi.etern.musichud.client.ui.hud.renderer.text.GradientSpec;
import lombok.NonNull;
import net.minecraft.client.gui.Font;
import org.joml.Matrix3x2f;

/**
 * Version-neutral render context used by the HUD renderers and metadata beans.
 * <p>
 * Each supported Minecraft version supplies one implementation
 * ({@link HudRenderContextImpl}); everything above this interface is
 * shared verbatim across branches.
 */
public interface HudRenderContext {
    static HudRenderContext getCurrent() {
        return HudRenderContextImpl.getCurrent();
    }

    void beginFrame(HudGraphics graphics);

    void endFrame();

    HudGraphics graphics();

    @NonNull
    Matrix3x2f currentPose();

    Transforming transform();

    void nextStratum();

    void submitHudRenderState(HudRenderState state);

    int guiWidth();

    int guiHeight();

    void pushScissor(int fromX, int fromY, int toX, int toY);

    void popScissor();

    void drawString(Font font, String text, int x, int y, int color, boolean dropShadow);

    /**
     * Draws text with a horizontal multi-stop gradient, interpolated per glyph vertex so a
     * gradient can run through a single character. Uses whichever text engine currently
     * renders text (Modern UI or vanilla).
     *
     * @param gradient normalized {@code [0, 1]} gradient across the whole drawn string
     * @param alpha    global opacity multiplier applied to the sampled colors
     */
    void drawGradientString(Font font, String text, int x, int y, GradientSpec gradient, float alpha);

    void fill(int fromX, int fromY, int toX, int toY, int color);
}
