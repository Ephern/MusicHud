package indi.etern.musichud.client.ui.hud.renderer.debug;

import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.ui.render.RenderLayer;
import indi.etern.musichud.client.ui.render.SceneRenderContext;
import indi.etern.musichud.client.utils.ui.ThemedColors;
import indi.etern.musichud.client.utils.ui.Transitionable;
import net.minecraft.client.Minecraft;

public final class ThemeColorDebugRenderer implements RenderLayer {
    private static final int SWATCH_SIZE = 20;
    private static final int PADDING = 4;
    private static final int START_X = 4;
    private static final int START_Y = 4;

    private final Transitionable<BackgroundData> background;

    public ThemeColorDebugRenderer(Transitionable<BackgroundData> background) {
        this.background = background;
    }

    @Override
    public void render(SceneRenderContext context) {
        BackgroundData data = background == null ? null : background.getMixed();
        if (data == null || data.color() == null) {
            return;
        }
        context.nextStratum();
        drawColorDebug(context, data.color());
    }

    private static void drawColorDebug(SceneRenderContext context, ThemedColors colors) {
        int currentY = START_Y;
        drawColorDebugLine(context, currentY, colors.primary, "Primary");
        currentY += SWATCH_SIZE + PADDING;
        drawColorDebugLine(context, currentY, colors.secondary, "Secondary");
        currentY += SWATCH_SIZE + PADDING;
        drawColorDebugLine(context, currentY, colors.bright, "Bright");
        currentY += SWATCH_SIZE + PADDING;
        drawColorDebugLine(context, currentY, colors.dark, "Dark");
    }

    private static void drawColorDebugLine(SceneRenderContext context, int currentY, int color, String label) {
        // Force opaque display; alpha holds the share weight, not opacity.
        int opaque = 0xFF000000 | (color & 0x00FFFFFF);
        int share = (color >>> 24) & 0xFF;
        context.fill(START_X, currentY, START_X + SWATCH_SIZE, currentY + SWATCH_SIZE, 0xFF000000);
        context.fill(START_X + 1, currentY + 1, START_X + SWATCH_SIZE - 1, currentY + SWATCH_SIZE - 1, opaque);
        String hex = String.format("#%06X A=%d", color & 0x00FFFFFF, share);
        context.drawString(Minecraft.getInstance().font, label + ": " + hex,
                START_X + SWATCH_SIZE + PADDING, currentY + (SWATCH_SIZE - 8) / 2, 0xFFFFFFFF, true);
    }
}
