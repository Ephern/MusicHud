package indi.etern.musichud.client.ui.hud.renderer;

import icyllis.modernui.mc.ModernUIMod;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.ui.hud.metadata.DynamicStatusUniform;
import indi.etern.musichud.client.ui.hud.metadata.HudRenderData;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import indi.etern.musichud.client.ui.hud.metadata.ThemedColors;
import indi.etern.musichud.client.ui.hud.pipelines.HudRenderPipelines;
import indi.etern.musichud.client.ui.hud.pipelines.HudRenderState;
import indi.etern.musichud.client.ui.utils.image.ImageTextureData;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.NotNull;

public class BackgroundRenderer implements HudRenderer {
    private static volatile BackgroundRenderer instance;
    private HudRenderData currentData;
    private ImageTextureData icon;
    private final DynamicStatusUniform dynamicStatusUniform = DynamicStatusUniform.getInstance();

    public static BackgroundRenderer getInstance() {
        if (instance == null) {
            synchronized (BackgroundRenderer.class) {
                if (instance == null)
                    instance = new BackgroundRenderer();
            }
        }
        return instance;
    }

    public void configure(HudRenderData data) {
        this.currentData = data;
    }

    private static final int SWATCH_SIZE = 20;
    private static final int PADDING = 4;
    private static final int START_X = 4;
    private static final int START_Y = 4;

    public void drawColorDebug(HudRenderContext renderContext, ThemedColors colors) {
        if (colors == null) return;
        int currentY = START_Y;
        drawColorDebugLine(renderContext, currentY, colors.primary, "Primary");
        currentY += SWATCH_SIZE + PADDING;
        drawColorDebugLine(renderContext, currentY, colors.secondary, "Secondary");
        currentY += SWATCH_SIZE + PADDING;
        drawColorDebugLine(renderContext, currentY, colors.bright, "Bright");
        currentY += SWATCH_SIZE + PADDING;
        drawColorDebugLine(renderContext, currentY, colors.dark, "Dark");
    }

    private static void drawColorDebugLine(HudRenderContext renderContext, int currentY, int color, String label) {
        renderContext.fill(START_X, currentY, START_X + SWATCH_SIZE, currentY + SWATCH_SIZE, 0xFF000000);
        renderContext.fill(START_X + 1, currentY + 1, START_X + SWATCH_SIZE - 1, currentY + SWATCH_SIZE - 1, color);
        String hex = String.format("#%06X", color & 0x00FFFFFF);
        renderContext.drawString(Minecraft.getInstance().font, label + ": " + hex,
                START_X + SWATCH_SIZE + PADDING, currentY + (SWATCH_SIZE - 8) / 2, 0xFFFFFFFF, true);
    }

    @Override
    public void render(HudRenderContext hudRenderContext) {
        if (currentData == null) return;

        Layout layout = currentData.getLayout();
        dynamicStatusUniform.setTransitionable(currentData.getTransitionableBackground());

        TextureSetup textureSetup = getMixedTextureSetup();

        HudRenderState hudRenderState = new HudRenderState(
                HudRenderPipelines.BACKGROUND,
                textureSetup,
                hudRenderContext.currentPose(),
                layout,
                layout,
                currentData.getTransitionableBackground().getMixed(),
                dynamicStatusUniform
        );

        if (ModernUIMod.isDeveloperMode()) {
            drawColorDebug(hudRenderContext, currentData.getTransitionableBackground().getMixed().color());
        }

        hudRenderContext.submitHudRenderState(hudRenderState);
        hudRenderContext.nextStratum();
    }

    private @NotNull TextureSetup getMixedTextureSetup() {
        var background = currentData.getTransitionableBackground();
        BackgroundData next = background.getNext();
        BackgroundData current = background.getCurrent();
        DynamicTexture currentTexture = current == null || current.image() == null || current.image().current == null ? getIconTexture() : current.image().current.getTexture();
        DynamicTexture nextTexture = next == null || next.image() == null || next.image().current == null ? getIconTexture() : next.image().current.getTexture();
        DynamicTexture transitionTexture = background.isTransitioning() ? nextTexture : currentTexture;
        TextureSetup textureSetup;
        if (currentTexture != null) {
            textureSetup = transitionTexture != null ?
                    TextureSetup.doubleTexture(currentTexture.getTextureView(), transitionTexture.getTextureView())
                    : TextureSetup.singleTexture(currentTexture.getTextureView());
        } else {
            textureSetup = TextureSetup.noTexture();
        }
        return textureSetup;
    }

    private DynamicTexture getIconTexture() {
        if (icon == null) {
            icon = ImageUtils.loadBase64(MusicHud.ICON_BASE64);
        }
        return icon.getTexture();
    }
}
