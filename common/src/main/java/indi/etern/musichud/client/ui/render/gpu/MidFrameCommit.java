package indi.etern.musichud.client.ui.render.gpu;

import icyllis.arc3d.granite.Recording;
import icyllis.arc3d.granite.RecordingContext;
import icyllis.modernui.core.Core;
import indi.etern.musichud.MusicHud;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-off point for partial UI recordings. The UI thread snaps what it has recorded once all blur
 * sources are in, and the render thread executes each recording, runs the blur requests that belong
 * to it, and only then executes the next recording. Blur results are therefore ready before any
 * compositing that happens in a later segment.
 * <p>
 * A frame's partial recordings are consumed together with that frame's main recording; executing
 * them earlier would display a half-drawn frame while the UI thread is still recording it.
 */
public final class MidFrameCommit {
    public record Commit(Recording recording, int batch) {
    }

    private static final Logger LOGGER = MusicHud.getLogger(MidFrameCommit.class);
    private static final List<Commit> PENDING = new ArrayList<>();
    private static int batch;

    private MidFrameCommit() {
    }

    /** Current batch number; requests made while recording belong to it. */
    public static int currentBatch() {
        return batch;
    }

    /** UI thread: snaps the recording made so far when at least one blur is queued. */
    public static void commitIfPending() {
        commitIfPending(null);
    }

    /**
     * UI thread: snaps {@code context} (or the UI recording context when null). Blur sources live in
     * their own context, so snapping them leaves the main UI recording in one piece.
     */
    public static void commitIfPending(@Nullable RecordingContext context) {
        if (context != null) {
            BlurRecordingContext.cleanupIfDue();
        }
        if (!SceneBlurSlot.hasPending()) {
            return;
        }
        Recording recording = context != null
                ? context.snap()
                : Core.requireUiRecordingContext().snap();
        if (recording == null) {
            LOGGER.warn("Failed to record a partial UI frame at batch {}", batch);
        } else {
            synchronized (PENDING) {
                PENDING.add(new Commit(recording, batch));
            }
        }
        batch++;
    }

    /** Render thread: takes ownership of every queued partial recording. */
    public static List<Commit> takeAll() {
        synchronized (PENDING) {
            if (PENDING.isEmpty()) {
                return List.of();
            }
            List<Commit> copy = new ArrayList<>(PENDING);
            PENDING.clear();
            return copy;
        }
    }
}
