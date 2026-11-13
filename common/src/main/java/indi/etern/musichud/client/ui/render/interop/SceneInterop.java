package indi.etern.musichud.client.ui.render.interop;

import indi.etern.musichud.client.ui.render.gpu.SceneGpuTexture;
import org.jetbrains.annotations.Nullable;

/**
 * The single place the effect layer talks to for cross-API texture sharing. Installed by the GPU
 * context implementation of the active platform backend.
 */
public final class SceneInterop {
    private static volatile SceneInteropBackend backend = SceneInteropBackend.NOOP;

    private SceneInterop() {
    }

    public static void install(SceneInteropBackend implementation) {
        backend = implementation == null ? SceneInteropBackend.NOOP : implementation;
    }

    public static SceneInteropBackend backend() {
        return backend;
    }

    public static InteropCapabilities capabilities() {
        return backend.capabilities();
    }

    public static boolean delaysExportedTextures() {
        return backend.delaysExportedTextures();
    }

    public static @Nullable InteropSource importImage(@Nullable icyllis.arc3d.engine.Image image,
                                                      InteropUsage usage) {
        return backend.importImage(image, usage);
    }

    public static @Nullable InteropTarget exportTexture(@Nullable SceneGpuTexture texture,
                                                        InteropUsage usage) {
        return backend.exportTexture(texture, usage);
    }

    public static void beginRead(InteropSource source) {
        backend.beginRead(source);
    }

    public static void endRead(InteropSource source) {
        backend.endRead(source);
    }

    public static void syncBack(SceneGpuTexture texture) {
        backend.syncBack(texture);
    }

    public static void prepareGameGraphics() {
        backend.prepareGameGraphics();
    }

    public static void restoreArc3d() {
        backend.restoreArc3d();
    }

    public static void flushArc3d(icyllis.arc3d.engine.ImmediateContext context) {
        backend.flushArc3d(context);
    }

    public static boolean requiresMidFrameSubmit() {
        return backend.requiresMidFrameSubmit();
    }
}
