package indi.etern.musichud.client.ui.hud.presentation;

import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.utils.ui.Transitionable;
import lombok.Getter;

/**
 * Holds the currently presented background and its override stack.
 * <p>
 * The transition is time-based and lazily advanced by readers, so this state does not need a
 * tick or render loop to complete a background change.
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
        long baseChange = hasImage(current) ? current.image().changedAtMillis : 0L;
        return Math.max(baseChange, overrideChange);
    }

    public long getBaseEffectiveChangeTimeMillis() {
        if (transition.isTransitioning()) {
            return transition.getTransitionStartMillis();
        }
        BackgroundData current = transition.getCurrent();
        return hasImage(current) ? current.image().changedAtMillis : 0L;
    }

    private static boolean hasImage(BackgroundData data) {
        return data != null && data.image() != null
                && (data.image().current != null || data.image().original != null);
    }
}
