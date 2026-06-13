package indi.etern.musichud.client.compat;

import indi.etern.musichud.MusicHud;

public class ReactiveMusicCompat {
    private static Object reactiveMusicThread;
    private static boolean reactiveMusicAvailable;

    static {
        try {
            reactiveMusicThread = Class.forName("circuitlord.reactivemusic.ReactiveMusic")
                    .getField("thread").get(null);
            reactiveMusicAvailable = reactiveMusicThread != null;
            MusicHud.LOGGER.info("ReactiveMusic detected");
        } catch (Exception ignored) {
            reactiveMusicAvailable = false;
        }
    }

    public static void muteReactiveMusic(boolean mute) {
        if (!reactiveMusicAvailable) return;
        try {
            reactiveMusicThread.getClass().getMethod("setGainPercentage", float.class).invoke(reactiveMusicThread, mute ? 0.0f : 1.0f);
        } catch (Exception exception) {
            MusicHud.LOGGER.warn("Failed to mute ReactiveMusic", exception);
        }
    }
}
