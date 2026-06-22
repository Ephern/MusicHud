package indi.etern.musichud.client.ui.hud.renderer;

import indi.etern.musichud.client.ui.hud.metadata.Layout;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

public class PlayerHeadRenderer implements HudRenderer {
    private static final int SKIN_TEXTURE_SIZE = 64;
    static volatile PlayerHeadRenderer instance;
    @Getter
    private Layout layout;
    @Getter
    @Setter
    private ResourceLocation skinResource;

    public static PlayerHeadRenderer getInstance() {
        if (instance == null) {
            synchronized (PlayerHeadRenderer.class) {
                if (instance == null)
                    instance = new PlayerHeadRenderer();
            }
        }
        return instance;
    }

    public void configure(Layout layout) {
        this.layout = layout;
    }

    @Override
    public void render(HudRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        if (skinResource == null) return;

        Layout.AbsolutePosition absolutePosition = layout.calcAbsolutePosition(context);
        int w = (int) layout.getWidth();
        int h = (int) layout.getHeight();

        // Inner layer (face) - slightly smaller for 3D depth, rendered first as background
        float scale = 0.87f;
        float inset = (1 - scale) / 2;
        context.nextStratum();

        context.transform()
                .translate(absolutePosition.x() + w * inset, absolutePosition.y() + h * inset)
                .scale(scale)
                .end((t) -> {
                    context.blit(
                            RenderPipelines.GUI_TEXTURED,
                            skinResource,
                            0, 0,
                            8, 8,
                            w, h,
                            8, 8,
                            SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE
                    );
                });

        // Outer layer (hat) - aligned to layout bounds, rendered second on top
        context.transform()
                .translate(absolutePosition.x(), absolutePosition.y())
                .end((t) -> {
                    context.blit(
                            RenderPipelines.GUI_TEXTURED,
                            skinResource,
                            0, 0, 40, 8,
                            w, h, 8, 8,
                            SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE
                    );
                });
    }
}