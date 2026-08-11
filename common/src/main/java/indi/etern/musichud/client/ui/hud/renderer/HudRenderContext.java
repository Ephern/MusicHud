package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.hud.pipelines.HudRenderState;
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

    void fill(int fromX, int fromY, int toX, int toY, int color);
}
