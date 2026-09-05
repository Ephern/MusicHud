package indi.etern.musichud.beans.music;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;

/**
 * Origin collection (playlist/album) of a track, for scrobble
 * {@code sourceid}/{@code source}.
 */
public record SourceMeta(long id, Class<?> type) {
    public static final ByteBufCodec<SourceMeta> CODEC = ByteBufCodec.composite(
            Codecs.LONG,
            SourceMeta::id,
            Codecs.CLASS,
            SourceMeta::type,
            SourceMeta::new
    );

    public String sourceTypeName() {
        if (type == Album.class) {
            return "album";
        }
        return "list";
    }
}
