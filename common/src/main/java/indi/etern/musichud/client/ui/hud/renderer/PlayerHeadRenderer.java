package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.hud.metadata.Layout;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class PlayerHeadRenderer implements HudRenderer {
    private static final int SKIN_TEXTURE_SIZE = 64;
    @Getter
    private Layout layout;
    private Identifier previousSkinResource;
    private Identifier skinResource;
    @Getter
    private Supplier<Identifier> playerSkinSupplier;
    private long lastUpdateTime = -1;
    private static final int TRANSITION_DURATION = 400;

    public void setPlayerSkinSupplier(@Nullable Supplier<Identifier> playerSkinSupplier) {
        this.playerSkinSupplier = playerSkinSupplier;
        lastUpdateTime = System.currentTimeMillis();
        previousSkinResource = skinResource;
        skinResource = playerSkinSupplier == null ? null : playerSkinSupplier.get();
    }

    public void configure(Layout layout) {
        this.layout = layout;
    }

    @Override
    public void render(HudRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        long currentTimeMillis = System.currentTimeMillis();
        if (playerSkinSupplier != null) {
            ResourceLocation skin = playerSkinSupplier.get();
            if (skinResource != skin) {
                lastUpdateTime = currentTimeMillis;
                previousSkinResource = skinResource;
                skinResource = skin;
            }
        }
        if (skinResource == null) return;

        Layout.AbsolutePosition absolutePosition = layout.calcAbsolutePosition(context);
        int w = (int) layout.getWidth();
        int h = (int) layout.getHeight();

        if (currentTimeMillis - lastUpdateTime > TRANSITION_DURATION) {
            renderHead(context.getGraphics(), skinResource,
                    absolutePosition.x(), absolutePosition.y(), w, h, 1);
        } else {
            float transitionProgress = Math.clamp((float) (currentTimeMillis - lastUpdateTime) / TRANSITION_DURATION, 0, 1);
            if (previousSkinResource != null) {
                renderHead(context.getGraphics(), previousSkinResource,
                        absolutePosition.x(), absolutePosition.y(), w, h, 1 - transitionProgress);
            }
            renderHead(context.getGraphics(), skinResource,
                    absolutePosition.x(), absolutePosition.y(), w, h, transitionProgress);
        }
    }

    public static void renderHead(GuiGraphicsExtractor gr, Identifier skin,
                                   float x, float y, int w, int h, float alpha) {
        if (alpha <= 0.003) {
            return;
        }
        float scale = 0.87f;
        float inset = (1 - scale) / 2;
        gr.nextStratum();

        // Inner face layer (8,8 to 16,16) - slightly smaller for depth
        gr.pose().pushMatrix();
        gr.pose().translate(x + w * inset, y + h * inset);
        gr.pose().scale(scale);
        int alphaColor = ARGB.color(Math.min(alpha, 1), 0xFFFFFF);
        gr.blit(RenderPipelines.GUI_TEXTURED, skin,
                0, 0, 8, 8, w, h, 8, 8, SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE, alphaColor);
        gr.pose().popMatrix();

        gr.pose().pushMatrix();
        gr.pose().translate(x, y);
        // Outer hat layer (40,8 to 48,16) - full size on top
        gr.blit(RenderPipelines.GUI_TEXTURED, skin,
                0, 0, 40, 8, w, h, 8, 8, SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE, alphaColor);
        gr.pose().popMatrix();
    }
}