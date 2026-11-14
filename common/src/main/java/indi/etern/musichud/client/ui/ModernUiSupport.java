package indi.etern.musichud.client.ui;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.RuntimeFlags;
import org.apache.logging.log4j.Logger;

/**
 * Probe for the ModernUI generation the client bindings are compiled against. The check is a class
 * lookup so it works for forks whose version strings do not follow SemVer or Maven ranges.
 */
public final class ModernUiSupport {
    public static final String DOWNLOAD_URL =
            "https://modrinth.com/mod/modern-ui";
    /** Debug switch: forces the "ModernUI missing" path so the fallback UI can be inspected. */
    public static final String FORCE_MISSING_PROPERTY = "music_hud.debug.forceModernUiMissing";

    private static final Logger LOGGER = MusicHud.getLogger(ModernUiSupport.class);
    private static final String PROBE_CLASS = "icyllis.arc3d.engine.ImageProxy";
    private static final boolean AVAILABLE = probe();

    private ModernUiSupport() {
    }

    public static boolean available() {
        return AVAILABLE;
    }

    /** Runs the probe during early client init, before any config-gated service starts. */
    public static void ensureProbed() {
        // Class initialization performs the probe.
    }

    private static boolean probe() {
        if (Boolean.getBoolean(FORCE_MISSING_PROPERTY)) {
            LOGGER.info("ModernUI support is force-disabled by -D{}=true", FORCE_MISSING_PROPERTY);
            RuntimeFlags.setClientUiAvailable(false);
            return false;
        }
        try {
            Class.forName(PROBE_CLASS, false, ModernUiSupport.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            LOGGER.warn("ModernUI 3.13+ is required for the client UI; it stays disabled this session. "
                    + "Download: {}", DOWNLOAD_URL);
            RuntimeFlags.setClientUiAvailable(false);
            return false;
        }
    }
}
