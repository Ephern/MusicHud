package indi.etern.musichud.client.ui.render.gpu;

import icyllis.arc3d.core.Rect2i;
import icyllis.arc3d.sketch.Image;
import org.jetbrains.annotations.Nullable;

/**
 * Shared state between the HUD config side panel (UI thread) and its backdrop blur (render thread).
 * The panel publishes the region it wants blurred, the renderer publishes the result and the region
 * the result covers.
 */
public final class HudPanelBackdrop {
    public record Region(int left, int top, int width, int height, float radius, float sigma) {
    }

    private static final PublishedSceneTexture TEXTURE = new PublishedSceneTexture();
    private static volatile Region request;
    private static volatile Rect2i captured;

    private HudPanelBackdrop() {
    }

    /** UI thread: the next capture should cover this device rect. */
    public static void request(int left, int top, int width, int height,
                               float radius, float sigma) {
        if (width < 1 || height < 1 || !(sigma > 0f)) {
            return;
        }
        Region current = request;
        if (current != null && current.left() == left && current.top() == top
                && current.width() == width && current.height() == height
                && current.radius() == radius && current.sigma() == sigma) {
            return;
        }
        request = new Region(left, top, width, height, radius, sigma);
    }

    public static void clear() {
        request = null;
        captured = null;
        TEXTURE.publish(null);
        TEXTURE.release();
    }

    /** Render thread: the region to capture, {@code null} when nothing is visible. */
    public static @Nullable Region request() {
        return request;
    }

    /** Render thread: publishes the blurred result and the device rect it covers. */
    public static void publish(@Nullable SceneGpuTexture texture, @Nullable Rect2i covered) {
        captured = covered;
        TEXTURE.publish(texture);
    }

    /** UI thread: the blurred texture, mapped by {@link #covered()}. */
    public static @Nullable Image image() {
        return TEXTURE.image();
    }

    /** UI thread: where the image's top-left pixel lands in device space. */
    public static @Nullable Rect2i covered() {
        return captured;
    }
}
