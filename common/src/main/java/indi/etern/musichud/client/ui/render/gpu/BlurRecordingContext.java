package indi.etern.musichud.client.ui.render.gpu;

import icyllis.arc3d.engine.ImmediateContext;
import icyllis.arc3d.granite.RecordingContext;
import icyllis.modernui.core.Core;
import indi.etern.musichud.MusicHud;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * Recording context dedicated to offscreen blur sources. Snapping it mid-frame hands over only the
 * source draws, leaving the main UI recording (and the layer it fills) in one piece.
 */
public final class BlurRecordingContext {
    private static final Logger LOGGER = MusicHud.getLogger(BlurRecordingContext.class);

    private static RecordingContext context;
    private static boolean unavailable;
    private static boolean fallback;
    private static long lastCleanupNanos;

    private BlurRecordingContext() {
    }

    public static @Nullable RecordingContext get() {
        if (fallback) {
            return null;
        }
        if (context != null) {
            return context;
        }
        if (unavailable) {
            return null;
        }
        ImmediateContext immediate = Core.peekImmediateContext();
        if (immediate == null) {
            return null;
        }
        try {
            context = RecordingContext.makeRecordingContext(immediate, new RecordingContext.Options());
        } catch (Throwable failure) {
            LOGGER.warn("Falling back to the UI recording context for blur sources", failure);
        }
        if (context == null) {
            unavailable = true;
            return null;
        }
        return context;
    }

    /** Pins sources created so far to the UI context, so later snaps target the same context. */
    static void markFallback() {
        fallback = true;
    }

    /** UI thread: runs the deferred cleanup this context would otherwise never get. */
    public static void cleanupIfDue() {
        RecordingContext current = get();
        if (current == null) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastCleanupNanos < 20_000_000_000L) {
            return;
        }
        lastCleanupNanos = now;
        current.performDeferredCleanup(120_000);
    }
}
