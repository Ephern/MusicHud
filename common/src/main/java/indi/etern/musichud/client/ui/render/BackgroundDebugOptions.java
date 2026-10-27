package indi.etern.musichud.client.ui.render;

/** Temporary in-memory debug options for the background shader experiments. */
public final class BackgroundDebugOptions {
    public static volatile BackgroundToneMapping toneMapping = BackgroundToneMapping.GAMMA;

    private BackgroundDebugOptions() {
    }
}
