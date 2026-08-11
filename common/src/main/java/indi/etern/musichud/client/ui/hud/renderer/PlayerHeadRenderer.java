package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.hud.metadata.Layout;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class PlayerHeadRenderer implements HudRenderer {
    private static final int SKIN_TEXTURE_SIZE = 64;
    @Getter
    private Layout layout;
    private String previousSkinResource;
    private String skinResource;
    @Getter
    private Supplier<String> playerSkinSupplier;
    private long lastUpdateTime = -1;
    private static final int TRANSITION_DURATION = 400;

    public void setPlayerSkinSupplier(@Nullable Supplier<String> playerSkinSupplier) {
        this.playerSkinSupplier = playerSkinSupplier;
        String newSkin = playerSkinSupplier == null ? null : playerSkinSupplier.get();
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
                String skin = playerSkinSupplier.get();
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
            renderHead(context.graphics(), skinResource,
                    absolutePosition.x(), absolutePosition.y(), w, h, 1);
        } else {
            float transitionProgress = Math.clamp((float) (currentTimeMillis - lastUpdateTime) / TRANSITION_DURATION, 0, 1);
            if (previousSkinResource != null) {
                renderHead(context.graphics(), previousSkinResource,
                        absolutePosition.x(), absolutePosition.y(), w, h, 1 - transitionProgress);
            }
            renderHead(context.graphics(), skinResource,
                    absolutePosition.x(), absolutePosition.y(), w, h, transitionProgress);
        }
    }

    public static void renderHead(HudGraphics graphics, String skinPath,
                                   float x, float y, int w, int h, float alpha) {
        if (alpha <= 0.003) {
            return;
        }
        float scale = 0.87f;
        float inset = (1 - scale) / 2;
        graphics.nextStratum();

        // Inner face layer (8,8 to 16,16) - slightly smaller for depth
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + w * inset, y + h * inset);
        graphics.pose().scale(scale);
        int alphaColor = ARGB.color(Math.min(alpha, 1), 0xFFFFFF);
        graphics.blitTextured(skinPath, 0, 0, 8, 8, w, h, 8, 8, SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE, alphaColor);
        graphics.pose().popMatrix();

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        // Outer hat layer (40,8 to 48,16) - full size on top
        graphics.blitTextured(skinPath, 0, 0, 40, 8, w, h, 8, 8, SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE, alphaColor);
        graphics.pose().popMatrix();
    }
}