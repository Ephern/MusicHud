package indi.etern.musichud.client.compat;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.ClientConfig;

public class ReactiveMusicCompat {
    private static Object reactiveMusicThread;
    private static boolean reactiveMusicAvailable;
    private static ClientConfig clientConfig = ClientConfig.getInstance();

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
        if (!clientConfig.getDisableVanillaMusic() || !reactiveMusicAvailable) return;
        try {
            reactiveMusicThread.getClass().getMethod("setGainPercentage", float.class).invoke(reactiveMusicThread, mute ? 0.0f : 1.0f);
        } catch (Exception exception) {
            MusicHud.LOGGER.warn("Failed to mute ReactiveMusic", exception);
        }
    }
}
