package indi.etern.musichud.client.ui.render.gpu;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Defers GPU resource release until no recording that could still sample the resource can execute.
 * <p>
 * The clock advances at the end of a rendered frame, after every partial recording of that frame and
 * the main one have been submitted: the UI thread records about one frame ahead, so a texture retired
 * during frame N can still be sampled by a recording that executes in frame N + 1.
 * <p>
 * Frame counting alone is not enough: VulkanMod keeps several frames in flight and reuses image
 * memory immediately, so a few frames at a high frame rate can span less wall time than the GPU
 * needs. A wall-clock floor keeps the window long enough there.
 */
public final class SceneRetirement {
    private static final int FRAMES = 3;
    private static final long MIN_MILLIS = 50L;
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static long frame;

    private SceneRetirement() {
    }

    /** Advances the retirement clock; called once per rendered frame. */
    public static void endFrame() {
        List<Runnable> due = null;
        long now = System.currentTimeMillis();
        synchronized (ENTRIES) {
            frame++;
            for (Iterator<Entry> iterator = ENTRIES.iterator(); iterator.hasNext(); ) {
                Entry entry = iterator.next();
                if (frame - entry.frame >= FRAMES && now - entry.millis >= MIN_MILLIS) {
                    iterator.remove();
                    if (due == null) {
                        due = new ArrayList<>();
                    }
                    due.add(entry.release);
                }
            }
        }
        if (due != null) {
            for (Runnable release : due) {
                release.run();
            }
        }
    }

    public static void retire(Runnable release) {
        long now = System.currentTimeMillis();
        synchronized (ENTRIES) {
            ENTRIES.add(new Entry(frame, now, release));
        }
    }

    /** Defers releasing a texture that a frame in flight may still sample. */
    public static void retire(SceneGpuContext owner, SceneGpuTexture texture) {
        retire(() -> owner.release(texture));
    }

    /** Defers releasing a mesh that a frame in flight may still draw. */
    public static void retire(SceneGpuContext owner, SceneMesh mesh) {
        retire(() -> owner.release(mesh));
    }

    private record Entry(long frame, long millis, Runnable release) {
    }
}
