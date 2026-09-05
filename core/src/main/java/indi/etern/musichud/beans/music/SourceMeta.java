package indi.etern.musichud.beans.music;

import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.server.api.playmode.PlayMode;

/**
 * Origin collection (playlist/album) of a track, for scrobble
 * {@code sourceid}/{@code source}.
 */
public record SourceMeta(long id, String imageUrl, PlayMode playMode, String name, Class<?> type) {
    public static final ByteBufCodec<SourceMeta> CODEC = ByteBufCodec.composite(
            Codecs.LONG,
            SourceMeta::id,
            Codecs.STRING_UTF8,
            SourceMeta::imageUrl,
            Codecs.ofEnum(PlayMode.class),
            SourceMeta::playMode,
            Codecs.STRING_UTF8,
            SourceMeta::name,
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
