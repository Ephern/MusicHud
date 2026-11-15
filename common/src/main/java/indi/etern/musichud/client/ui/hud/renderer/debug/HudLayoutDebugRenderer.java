package indi.etern.musichud.client.ui.hud.renderer.debug;

import indi.etern.musichud.client.ui.hud.layout.HudLayouts;
import indi.etern.musichud.client.ui.layout.FlexBox;
import indi.etern.musichud.client.ui.layout.LayoutNode;
import indi.etern.musichud.client.ui.render.RenderLayer;
import indi.etern.musichud.client.ui.render.SceneRenderContext;
import indi.etern.musichud.client.ui.render.SceneStyle;

public final class HudLayoutDebugRenderer implements RenderLayer {
    private static final int CONTAINER_COLOR = 0x80FFAA00;
    private static final int ELEMENT_COLOR = 0x8000AAFF;

    private volatile HudLayouts layout;

    public void configure(HudLayouts layout) {
        this.layout = layout;
    }

    @Override
    public void render(SceneRenderContext context) {
        HudLayouts current = layout;
        if (current == null) {
            return;
        }
        SceneStyle.AbsolutePosition position = current.getBaseStyle().calcAbsolutePosition(context);
        context.nextStratum();
        renderNode(context, position.x(), position.y(), current.getTree().root());
    }

    private static void renderNode(SceneRenderContext context, float baseX, float baseY, LayoutNode node) {
        if (node == null || !node.isLayoutVisible() || node.getWidth() <= 0 || node.getHeight() <= 0) {
            return;
        }
        int color = node instanceof FlexBox ? CONTAINER_COLOR : ELEMENT_COLOR;
        drawBox(context, baseX + node.getX(), baseY + node.getY(),
                node.getWidth(), node.getHeight(), color);
        if (node instanceof FlexBox box) {
            for (LayoutNode child : box.children()) {
                renderNode(context, baseX, baseY, child);
            }
        }
    }

    private static void drawBox(SceneRenderContext context, float x, float y, float width, float height, int color) {
        int left = Math.round(x);
        int top = Math.round(y);
        int right = Math.max(left + 1, Math.round(x + width));
        int bottom = Math.max(top + 1, Math.round(y + height));
        context.fill(left, top, right, top + 1, color);
        context.fill(left, bottom - 1, right, bottom, color);
        context.fill(left, top, left + 1, bottom, color);
        context.fill(right - 1, top, right, bottom, color);
    }
}
