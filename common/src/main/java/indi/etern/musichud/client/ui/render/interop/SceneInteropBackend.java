package indi.etern.musichud.client.ui.render.interop;

import indi.etern.musichud.client.ui.render.gpu.SceneGpuTexture;
import org.jetbrains.annotations.Nullable;

/**
 * Backend SPI for sharing textures between arc3d and the game's render device.
 * <p>
 * Implementations own every API-specific detail (handles, views, image layouts, command buffers).
 * The effect layer only sees {@link InteropSource} / {@link InteropTarget} leases.
 */
public interface SceneInteropBackend {
    SceneInteropBackend NOOP = new SceneInteropBackend() {
    };

    default InteropCapabilities capabilities() {
        return InteropCapabilities.NONE;
    }

    /**
     * Whether the UI recording can be split into segments to refresh a backdrop blur within the
     * same frame. Backends whose layer submission cannot be interleaved with foreign draws must
     * return false; the blur is then resolved once per frame.
     */
    default boolean supportsMidFrameBlur() {
        return true;
    }

    /**
     * Borrows an arc3d engine image for our pipelines. Takes ownership of the passed reference.
     */
    default @Nullable InteropSource importImage(@Nullable icyllis.arc3d.engine.Image image,
                                                InteropUsage usage) {
        if (image != null) {
            image.unref();
        }
        return null;
    }

    /** Wraps one of our textures so arc3d can sample it. */
    default @Nullable InteropTarget exportTexture(@Nullable SceneGpuTexture texture, InteropUsage usage) {
        return null;
    }

    /** Makes the imported image visible to our pipelines for this frame. */
    default void beginRead(InteropSource source) {
    }

    default void endRead(InteropSource source) {
    }

    /** Makes a texture mutated by arc3d visible again before the next write. */
    default void syncBack(SceneGpuTexture texture) {
    }

    /** Re-establishes the game's GL/device state after arc3d ran. */
    default void prepareGameGraphics() {
    }

    /** Marks arc3d's cached state dirty after the game rendered. */
    default void restoreArc3d() {
    }
}
