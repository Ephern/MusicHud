package indi.etern.musichud.client.ui.render.gpu;

import icyllis.arc3d.sketch.Canvas;
import icyllis.arc3d.sketch.NWayCanvas;
import icyllis.arc3d.sketch.Surface;
import indi.etern.musichud.MusicHud;
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
