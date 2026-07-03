package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MinecraftSurfaceView;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewParent;
import indi.etern.musichud.client.ui.hud.renderer.PlayerHeadRenderer;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class PlayerHeadView extends MinecraftSurfaceView {
    @Getter
    private Supplier<ResourceLocation> playerSkinSupplier;

    @Setter
    @Getter
    private ResourceLocation skin;

    public PlayerHeadView(Context context) {
        super(context);
        setRenderer(new MinecraftSurfaceView.Renderer() {
            @Override
            public void onSurfaceChanged(int width, int height) {}

            @Override
            public void onDraw(@NotNull GuiGraphics gr, int mouseX, int mouseY, float deltaTick,
                               double guiScale, float alpha) {
                if (playerSkinSupplier != null) {
                    skin = playerSkinSupplier.get();
                }
                if (skin == null) return;
                int w = (int) Math.ceil(getWidth() / guiScale);
                int h = (int) Math.ceil(getHeight() / guiScale);
                PlayerHeadRenderer.renderHead(gr, skin, 0, 0, w, h, alpha);
            }
        });
    }

    public void setPlayerSkinSupplier(@Nullable Supplier<ResourceLocation> playerSkinSupplier) {
        this.playerSkinSupplier = playerSkinSupplier;
        skin = playerSkinSupplier == null ? null : playerSkinSupplier.get();
    }

    public float getConfiguredAlpha() {
        return super.getAlpha();
    }

    @Override
    public float getAlpha() {
        float alpha = super.getAlpha();
        ViewParent parent = getParent();
        while (parent instanceof View viewParent) {
            alpha *= viewParent.getAlpha();
            parent = viewParent.getParent();
        }
        return alpha;
    }
}
