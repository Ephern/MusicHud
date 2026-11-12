package indi.etern.musichud.client.ui.render.gpu;

import icyllis.arc3d.sketch.Image;
import icyllis.modernui.core.Core;
import indi.etern.musichud.client.ui.render.interop.InteropTarget;
import indi.etern.musichud.client.ui.render.interop.InteropUsage;
import indi.etern.musichud.client.ui.render.interop.SceneInterop;
import org.jetbrains.annotations.Nullable;

/**
 * A scene texture written on the render thread and sampled on the UI thread. The wrapper is
 * non-owning, so the producer must keep the texture alive while an image is referenced; consumers
 * drop the wrapper when the producer publishes a different texture.
 */
public final class PublishedSceneTexture {
    private volatile SceneGpuTexture texture;
    private SceneGpuTexture wrappedSource;
    private InteropTarget target;
    private int consumers;

    public void publish(@Nullable SceneGpuTexture texture) {
        this.texture = texture;
    }

    public @Nullable SceneGpuTexture texture() {
        return texture;
    }

    public synchronized void registerConsumer() {
        consumers++;
    }

    public synchronized void unregisterConsumer() {
        if (consumers > 0) {
            consumers--;
        }
        if (consumers == 0) {
            release();
        }
    }

    public synchronized @Nullable Image image() {
        SceneGpuTexture current = texture;
        if (current == null || current.isClosed()) {
            return null;
        }
        if (target != null && wrappedSource == current) {
            return target.arcImage();
        }
        if (!Core.isOnUiThread()) {
            // Exporting needs the UI recording context; the UI thread will wrap it on its next draw.
            return null;
        }
        release();
        InteropTarget created = SceneInterop.exportTexture(current, InteropUsage.SAMPLE);
        if (created == null) {
            return null;
        }
        target = created;
        wrappedSource = current;
        return target.arcImage();
    }

    /**
     * Drops the published image. The wrapper goes through the retirement queue because a recording
     * made before the drop may still sample it.
     */
    public synchronized void discard() {
        texture = null;
        InteropTarget previous = target;
        target = null;
        wrappedSource = null;
        if (previous != null) {
            SceneRetirement.retire(previous::close);
        }
    }

    public synchronized void release() {
        if (target != null) {
            target.close();
            target = null;
        }
        wrappedSource = null;
    }
}
