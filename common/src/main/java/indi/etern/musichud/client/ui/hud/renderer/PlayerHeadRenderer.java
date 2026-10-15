package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.hud.metadata.HudStyle;
import indi.etern.musichud.client.utils.ui.Easing;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class PlayerHeadRenderer implements HudRenderer {
    private static final int SKIN_TEXTURE_SIZE = 64;
    private static final int TRANSITION_DURATION = 400;
    private static final float ALPHA_EPSILON = 0.003f;
    @Getter
    private HudStyle hudStyle;
    @Getter
    private volatile Supplier<String> playerSkinSupplier;

    /**
     * Active cross-fade layers. Owned by the render thread only: {@link #setPlayerSkinSupplier}
     * just publishes the supplier so no transition state is ever mutated off the render thread.
     */
    private final List<SkinLayer> layers = new ArrayList<>();
    /**
     * The skin currently being faded in; {@code null} means the target is "no skin" (fade out).
     */
    @Nullable
    private String targetSkin;
    private long transitionStartTime = -1;

    private static final class SkinLayer {
        private final String skin;
        private float startAlpha;

        private SkinLayer(String skin, float startAlpha) {
            this.skin = skin;
            this.startAlpha = startAlpha;
        }
    }

    public static void renderHead(HudGraphics graphics, String skinPath,
                                  float x, float y, int w, int h, float alpha) {
        if (alpha <= ALPHA_EPSILON) {
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

    public void setPlayerSkinSupplier(@Nullable Supplier<String> playerSkinSupplier) {
        this.playerSkinSupplier = playerSkinSupplier;
    }

    public boolean isVisible() {
        return !layers.isEmpty();
    }

    public void configure(HudStyle hudStyle) {
        this.hudStyle = hudStyle;
    }

    @Override
    public void render(HudRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        Supplier<String> supplier = playerSkinSupplier;
        if (supplier == null) {
            // Supplier removed (player quit / HUD idle): fade the head out.
            setTarget(null);
        } else {
            String skin = null;
            try {
                skin = supplier.get();
            } catch (Exception ignored) {
                // Render-thread supplier: a lookup failure must never break the HUD frame.
            }
            // A null sample is treated as "no skin" so a temporarily unavailable skin fades out.
            setTarget(skin);
        }

        long currentTimeMillis = System.currentTimeMillis();
        float progress = progressAt(currentTimeMillis);
        if (progress >= 1f) {
            // Transition settled: keep only the target layer, fully opaque, ready for the next one.
            layers.removeIf(layer -> !Objects.equals(layer.skin, targetSkin));
            for (SkinLayer layer : layers) {
                layer.startAlpha = 1f;
            }
        }
        if (layers.isEmpty()) return;

        HudStyle.AbsolutePosition absolutePosition = hudStyle.calcAbsolutePosition(context);
        int w = (int) hudStyle.getWidth();
        int h = (int) hudStyle.getHeight();
        float styleAlpha = hudStyle.calcAbsoluteAlpha();
        for (SkinLayer layer : layers) {
            renderHead(context.graphics(), layer.skin,
                    absolutePosition.x(), absolutePosition.y(), w, h, alphaAt(layer, progress) * styleAlpha);
        }
    }

    /**
     * Retargets the cross-fade while preserving the currently displayed composite. Every active
     * layer is frozen at its current alpha and then converges: the new target toward 1, the rest
     * toward 0. Because the frozen alphas always sum to 1 while a target exists, the layers keep
     * summing to 1 through any number of interruptions, so interrupting a transition never flashes.
     */
    private void setTarget(@Nullable String next) {
        if (Objects.equals(targetSkin, next)) return;
        long now = System.currentTimeMillis();
        float progress = progressAt(now);
        for (SkinLayer layer : layers) {
            layer.startAlpha = alphaAt(layer, progress);
        }
        layers.removeIf(layer -> layer.startAlpha < ALPHA_EPSILON);
        targetSkin = next;
        if (next != null && layers.stream().noneMatch(layer -> layer.skin.equals(next))) {
            // A layer that was already fading (e.g. A -> B -> A) keeps its frozen alpha.
            layers.add(new SkinLayer(next, 0f));
        }
        transitionStartTime = now;
    }

    private float progressAt(long now) {
        if (transitionStartTime < 0) return 1f;
        float linear = Math.clamp((float) (now - transitionStartTime) / TRANSITION_DURATION, 0f, 1f);
        return Easing.EASE_IN_OUT_SINE.getInterpolation(linear);
    }

    private float alphaAt(SkinLayer layer, float progress) {
        float goal = Objects.equals(layer.skin, targetSkin) ? 1f : 0f;
        return layer.startAlpha + (goal - layer.startAlpha) * progress;
    }
}
