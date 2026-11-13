package indi.etern.musichud.client.ui.hud.presentation;

import java.util.function.BooleanSupplier;

/** Serializes cover request replacement with publication and delayed retry admission. */
final class CoverRequestGate {
    private long generation;

    synchronized long begin() {
        return ++generation;
    }

    synchronized boolean runIfCurrent(long token, BooleanSupplier currentTrack, Runnable action) {
        if (token != generation || !currentTrack.getAsBoolean()) return false;
        action.run();
        return true;
    }
}
