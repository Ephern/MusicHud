package indi.etern.musichud.client.ui.hud.presentation;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns the lifetime of the displayed {@link TrackArtwork}.
 * <p>
 * The renderers keep sampling the previous cover while a crossfade is running, and a renderer that
 * stops rendering (closed screen, hidden HUD) must not keep an artwork alive forever. Instead of
 * inferring that from cache eviction, the store keeps the retired artwork until no {@link Holder}
 * reports a reference and then closes it — on the render thread, at the end of a frame.
 */
public final class TrackArtworkStore {
    /** Something that may still sample an artwork's textures. */
    public interface Holder {
        boolean holdsCover(TrackArtwork artwork);
    }

    private static final TrackArtworkStore INSTANCE = new TrackArtworkStore();

    private final Object lock = new Object();
    private final List<Holder> holders = new CopyOnWriteArrayList<>();
    private final List<TrackArtwork> retiring = new ArrayList<>();
    private TrackArtwork current;

    private TrackArtworkStore() {
    }

    public static TrackArtworkStore getInstance() {
        return INSTANCE;
    }

    public void addHolder(Holder holder) {
        if (holder != null && !holders.contains(holder)) {
            holders.add(holder);
        }
    }

    public void removeHolder(Holder holder) {
        holders.remove(holder);
    }

    /** Makes {@code artwork} the displayed one; the previous one is released once it is unused. */
    public void publish(@Nullable TrackArtwork artwork) {
        synchronized (lock) {
            if (current != null && current != artwork) {
                retiring.add(current);
            }
            current = artwork;
        }
    }

    public @Nullable TrackArtwork current() {
        synchronized (lock) {
            return current;
        }
    }

    /**
     * Closes every retired artwork that nothing references anymore. Render thread, once per frame;
     * cheaps out immediately while nothing is retiring.
     */
    public void sweep() {
        List<TrackArtwork> ready = null;
        synchronized (lock) {
            if (retiring.isEmpty()) {
                return;
            }
            for (Iterator<TrackArtwork> iterator = retiring.iterator(); iterator.hasNext(); ) {
                TrackArtwork artwork = iterator.next();
                if (isHeld(artwork)) {
                    continue;
                }
                if (ready == null) {
                    ready = new ArrayList<>();
                }
                ready.add(artwork);
                iterator.remove();
            }
        }
        if (ready == null) {
            return;
        }
        for (TrackArtwork artwork : ready) {
            artwork.close();
        }
    }

    private boolean isHeld(TrackArtwork artwork) {
        if (artwork.isClosed()) {
            return false;
        }
        if (BackgroundState.getInstance().holdsArtwork(artwork)) {
            return true;
        }
        for (Holder holder : holders) {
            if (holder.holdsCover(artwork)) {
                return true;
            }
        }
        return false;
    }
}
