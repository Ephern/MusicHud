package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MinecraftSurfaceView;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewParent;
import indi.etern.musichud.client.ui.hud.renderer.PlayerHeadRenderer;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class PlayerHeadView extends MinecraftSurfaceView {
    @Getter
    private Supplier<Identifier> playerSkinSupplier;

    @Setter
    @Getter
    private Identifier skin;

    private final int[] location = new int[2];

    // local-space clip rect, null means fully visible; computed on the UI thread
    // in updateSurface() so it stays consistent with the surface position snapshot
    private volatile ClipBounds clipBounds;

    private record ClipBounds(int left, int top, int right, int bottom) {
        boolean isEmpty() {
            return left >= right || top >= bottom;
        }
    }

    public PlayerHeadView(Context context) {
        super(context);
        setRenderer(new MinecraftSurfaceView.Renderer() {
            @Override
            public void onSurfaceChanged(int width, int height) {}

            @Override
            public void onDraw(@NotNull GuiGraphicsExtractor gr, int mouseX, int mouseY, float deltaTick,
                               double guiScale, float alpha) {
                if (playerSkinSupplier != null) {
                    skin = playerSkinSupplier.get();
                }
                if (skin == null) return;
                ClipBounds clip = clipBounds;
                if (clip != null && clip.isEmpty()) return;
                int w = (int) Math.ceil(getWidth() / guiScale);
                int h = (int) Math.ceil(getHeight() / guiScale);
                if (clip != null) {
                    // scissor coordinates are local GUI scaled, transformed by the current pose
                    gr.enableScissor(
                            (int) Math.floor(clip.left / guiScale),
                            (int) Math.floor(clip.top / guiScale),
                            (int) Math.ceil(clip.right / guiScale),
                            (int) Math.ceil(clip.bottom / guiScale));
                }
                try {
                    PlayerHeadRenderer.renderHead(gr, skin, 0, 0, w, h, alpha);
                } finally {
                    if (clip != null) {
                        gr.disableScissor();
                    }
                }
            }
        });
    }

    @Override
    protected void updateSurface() {
        // clip against the visible bounds of the parent chain,
        // since MinecraftSurfaceView bypasses the canvas clip
        getLocationInWindow(location);
        int myLeft = location[0];
        int myTop = location[1];
        int clipLeft = 0, clipTop = 0, clipRight = getWidth(), clipBottom = getHeight();
        ViewParent parent = getParent();
        while (parent instanceof View viewParent) {
            viewParent.getLocationInWindow(location);
            clipLeft = Math.max(clipLeft, location[0] - myLeft);
            clipTop = Math.max(clipTop, location[1] - myTop);
            clipRight = Math.min(clipRight, location[0] + viewParent.getWidth() - myLeft);
            clipBottom = Math.min(clipBottom, location[1] + viewParent.getHeight() - myTop);
            parent = viewParent.getParent();
        }
        if (clipLeft <= 0 && clipTop <= 0 && clipRight >= getWidth() && clipBottom >= getHeight()) {
            clipBounds = null;
        } else {
            clipBounds = new ClipBounds(clipLeft, clipTop, clipRight, clipBottom);
        }
        super.updateSurface();
    }

    public void setPlayerSkinSupplier(@Nullable Supplier<Identifier> playerSkinSupplier) {
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
