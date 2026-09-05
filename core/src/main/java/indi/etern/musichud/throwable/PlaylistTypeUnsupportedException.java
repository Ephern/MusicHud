package indi.etern.musichud.throwable;

import indi.etern.musichud.MusicHud;

/** HTTP 400 from the intelligent list API: the playlist type is not supported. Message is an i18n key. */
public class PlaylistTypeUnsupportedException extends ApiException {
    public PlaylistTypeUnsupportedException() {
        super(MusicHud.MOD_ID + ".text.intelligentUnsupported");
    }
}
