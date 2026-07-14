package indi.etern.musichud.client.ui.hud.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class PlayerHeadRenderer implements HudRenderer {
    private static final int SKIN_TEXTURE_SIZE = 64;
    @Getter
    private Layout layout;
    private ResourceLocation previousSkinResource;
    private ResourceLocation skinResource;
    @Getter
    private Supplier<ResourceLocation> playerSkinSupplier;
    private long lastUpdateTime = -1;
    private static final int TRANSITION_DURATION = 400;

    public void setPlayerSkinSupplier(@Nullable Supplier<ResourceLocation> playerSkinSupplier) {
        this.playerSkinSupplier = playerSkinSupplier;
        ResourceLocation newSkin = playerSkinSupplier == null ? null : playerSkinSupplier.get();
        long now = System.currentTimeMillis();
        if (now - lastUpdateTime > TRANSITION_DURATION) {
            previousSkinResource = skinResource;
        } else {
            previousSkinResource = null;
        }
        lastUpdateTime = now;
        skinResource = newSkin;
    }

    public void configure(Layout layout) {
        this.layout = layout;
    }

    @Override
    public void render(HudRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        long currentTimeMillis = System.currentTimeMillis();
        try {
            if (playerSkinSupplier != null) {
                ResourceLocation skin = playerSkinSupplier.get();
                if (skinResource != skin) {
                    if (currentTimeMillis - lastUpdateTime > TRANSITION_DURATION) {
                        previousSkinResource = skinResource;
                    } else {
                        previousSkinResource = null;
                    }
                    lastUpdateTime = currentTimeMillis;
                    skinResource = skin;
                }
            }
            if (skinResource == null) return;
        } catch (Exception ignored) {}

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

    public static void renderHead(GuiGraphics gr, ResourceLocation skin,
                                   float x, float y, int w, int h, float alpha) {
        if (alpha <= 0.003) {
            return;
        }
        float scale = 0.87f;
        float inset = (1 - scale) / 2;

        float[] prevColor = RenderSystem.getShaderColor();
        // Inner face layer (8,8 to 16,16) - slightly smaller for depth
        gr.setColor(1, 1, 1, Math.min(alpha, 1));
        PoseStack pose = gr.pose();
        pose.pushPose();
        pose.translate(x + w * inset, y + h * inset, 0);
        pose.scale(scale, scale, 1);
        gr.blit(skin,
                0, 0, w, h,
                8, 8, 8, 8,
                SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE);
        pose.popPose();

        pose.pushPose();
        pose.translate(x, y, 0);
        // Outer hat layer (40,8 to 48,16) - full size on top
        gr.blit(skin,
                0, 0, w, h,
                40, 8, 8, 8,
                SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE);
        pose.popPose();
        gr.setColor(prevColor[0], prevColor[1], prevColor[2], prevColor[3]);
    }
}