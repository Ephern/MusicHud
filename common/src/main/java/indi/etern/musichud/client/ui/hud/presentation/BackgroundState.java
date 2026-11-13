package indi.etern.musichud.client.ui.hud.presentation;

import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundImages;
import indi.etern.musichud.client.utils.ui.Transitionable;
import lombok.Getter;

/**
 * Holds the presented background and its override stack. The transition advances lazily when read,
 * so a background change completes without a tick or render loop.
 */
@Getter
public final class BackgroundState {
    private static final int TRANSITION_MS = 500;
    private static volatile BackgroundState instance;

    private final Transitionable<BackgroundData> transition =
            new Transitionable<>(BackgroundData.NONE, TRANSITION_MS);
    private final BackgroundOverrideStack overrides = new BackgroundOverrideStack();

    private BackgroundState() {
    }

    public static BackgroundState getInstance() {
        if (instance == null) {
            synchronized (BackgroundState.class) {
                if (instance == null) {
                    instance = new BackgroundState();
                }
            }
        }
        return instance;
    }

    public void startTransition(BackgroundData data) {
        transition.startTransition(data);
    }

    public void reset() {
        transition.startTransition(BackgroundData.NONE);
    }

    /** @return true while either crossfade slot still samples one of {@code artwork}'s textures. */
    public boolean holdsArtwork(TrackArtwork artwork) {
        if (artwork == null) return false;
        return references(transition.getCurrent(), artwork)
                || references(transition.getNext(), artwork);
    }

    private static boolean references(BackgroundData data, TrackArtwork artwork) {
        BackgroundImages images = data == null ? null : data.image();
        return images != null
                && (artwork.holds(images.current) || artwork.holds(images.original));
    }

    public long getEffectiveBackgroundChangeTimeMillis() {
        BackgroundData override = overrides.getTop();
        long overrideChange = overrides.getTopImageChangedAtMillis();
        if (hasImage(override)) {
            return overrideChange;
        }
        if (transition.isTransitioning()) {
            return transition.getTransitionStartMillis();
        }
        BackgroundData current = transition.getCurrent();
        if (!hasImage(current)) {
            return Math.max(transition.getTransitionStartMillis(), overrideChange);
        }
        return Math.max(current.image().changedAtMillis, overrideChange);
    }

    public long getBaseEffectiveChangeTimeMillis() {
        if (transition.isTransitioning()) {
            return transition.getTransitionStartMillis();
        }
        BackgroundData current = transition.getCurrent();
        return hasImage(current)
                ? current.image().changedAtMillis
                : transition.getTransitionStartMillis();
    }

    private static boolean hasImage(BackgroundData data) {
        return data != null && data.image() != null
                && (data.image().current != null || data.image().original != null);
    }
}
