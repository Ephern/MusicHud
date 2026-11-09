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
 */
public final class SceneRetirement {
    private static final int FRAMES = 3;
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static long frame;

    private SceneRetirement() {
    }

    static void endFrame() {
        List<Runnable> due = null;
        synchronized (ENTRIES) {
            frame++;
            for (Iterator<Entry> iterator = ENTRIES.iterator(); iterator.hasNext(); ) {
                Entry entry = iterator.next();
                if (frame - entry.frame >= FRAMES) {
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
        synchronized (ENTRIES) {
            ENTRIES.add(new Entry(frame, release));
        }
    }

    private record Entry(long frame, Runnable release) {
    }
}
