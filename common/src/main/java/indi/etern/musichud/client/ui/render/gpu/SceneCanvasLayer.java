package indi.etern.musichud.client.ui.render.gpu;

import icyllis.arc3d.core.Rect2i;
import icyllis.arc3d.engine.Engine;
import icyllis.arc3d.granite.GraniteSurface;
import icyllis.arc3d.sketch.Canvas;
import icyllis.arc3d.sketch.Image;
import icyllis.arc3d.sketch.NWayCanvas;
import icyllis.arc3d.sketch.Surface;
import indi.etern.musichud.MusicHud;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * Resolves the surface that owns the recording behind a canvas. An alpha-animated view wraps the
 * canvas in an {@code AlphaFilterCanvas}, which owns no surface; the recording target is the single
 * proxied canvas inside it.
 */
public final class SceneCanvasLayer {
    private static final Field NWAY_CHILDREN = lookupNwayChildren();

    private SceneCanvasLayer() {
    }

    public static @Nullable Surface resolve(@Nullable Canvas canvas) {
        if (canvas == null) {
            return null;
        }
        Surface surface = canvas.getSurface();
        if (surface != null) {
            return surface;
        }
        if (NWAY_CHILDREN == null || !(canvas instanceof NWayCanvas way)) {
            return null;
        }
        try {
            Canvas[] children = (Canvas[]) NWAY_CHILDREN.get(way);
            if (children != null && children.length > 0 && children[0] != null) {
                return resolve(children[0]);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Snapshots a device-space rect. The copy works in texel space, which is bottom-up on a
     * lower-left surface, so the rect is mirrored there.
     */
    public static @Nullable Image snapshot(@NotNull Surface surface,
                                           int left, int top, int right, int bottom) {
        int origin = surface instanceof GraniteSurface granite
                ? granite.getReadSurfaceView().getOrigin()
                : Engine.SurfaceOrigin.kUpperLeft;
        if (origin == Engine.SurfaceOrigin.kLowerLeft) {
            int height = surface.getHeight();
            return surface.makeImageSnapshot(new Rect2i(left, height - bottom, right, height - top));
        }
        return surface.makeImageSnapshot(new Rect2i(left, top, right, bottom));
    }

    private static @Nullable Field lookupNwayChildren() {
        try {
            Field field = NWayCanvas.class.getDeclaredField("mList");
            field.setAccessible(true);
            return field;
        } catch (Throwable failure) {
            MusicHud.getLogger(SceneCanvasLayer.class)
                    .warn("Layer backdrop blur disabled while canvases are alpha-filtered", failure);
            return null;
        }
    }
}
