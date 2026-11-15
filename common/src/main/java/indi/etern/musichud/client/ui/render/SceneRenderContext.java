package indi.etern.musichud.client.ui.render;

import indi.etern.musichud.client.ui.render.text.GradientSpec;
import indi.etern.musichud.client.ui.render.text.TextEdgeFade;
import lombok.NonNull;
import net.minecraft.client.gui.Font;
import org.joml.Matrix3x2f;

public interface SceneRenderContext {
    static SceneRenderContext getCurrent() {
        return SceneRenderContextImpl.getCurrent();
    }

    default void renderFrame(SceneGraphics graphics, RenderScene scene) {
        beginFrame(graphics);
        try {
            scene.render(this);
        } finally {
            endFrame();
        }
    }

    void beginFrame(SceneGraphics graphics);

    void endFrame();

    void endGuiFrame();

    long frameTimeMillis();

    long frameTimeNanos();

    SceneGraphics graphics();

    @NonNull
    Matrix3x2f currentPose();

    Transforming transform();

    void nextStratum();

    void submit(RenderLayer owner, SceneElementRenderState state);

    int guiWidth();

    int guiHeight();

    void pushScissor(int fromX, int fromY, int toX, int toY);

    void popScissor();

    void drawString(Font font, String text, int x, int y, int color, boolean dropShadow);

    default void drawGradientString(Font font, String text, int x, int y, GradientSpec gradient, float alpha) {
        drawGradientString(font, text, x, y, gradient, alpha, TextEdgeFade.NONE);
    }

    void drawGradientString(Font font, String text, int x, int y, GradientSpec gradient, float alpha,
                            TextEdgeFade fade);

    void fill(int fromX, int fromY, int toX, int toY, int color);
}
