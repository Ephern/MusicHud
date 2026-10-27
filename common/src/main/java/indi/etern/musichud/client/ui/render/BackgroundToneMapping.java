package indi.etern.musichud.client.ui.render;

import indi.etern.musichud.MusicHud;
import net.minecraft.client.resources.language.I18n;

/** Temporary debug switch for the background brightness curve. */
public enum BackgroundToneMapping {
    LINEAR(MusicHud.MOD_ID + ".config.debug.backgroundToneMapping.LINEAR"),
    GAMMA(MusicHud.MOD_ID + ".config.debug.backgroundToneMapping.GAMMA");

    private final String displayNameKey;

    BackgroundToneMapping(String displayNameKey) {
        this.displayNameKey = displayNameKey;
    }

    @Override
    public String toString() {
        return I18n.get(displayNameKey);
    }
}
