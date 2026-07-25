package indi.etern.musichud.beans.api;

import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.network.ByteBufCodec;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;
import java.util.UUID;

@Getter
@ToString
public final class IdlePlaySource {
    public static final ByteBufCodec<IdlePlaySource> CODEC = ByteBufCodec.composite(
            Codecs.VAR_LONG,//TODO replace with Codecs.LONG in 1.3.0
            IdlePlaySource::getId,
            Codecs.CLASS,
            IdlePlaySource::getType,
            IdlePlaySource::new
    );
    private final long id;
    private final Class<?> type;
    @Setter
    transient private PusherInfo pusherInfo;
    @Getter
    transient private boolean dataLoaded = false;
    transient private MusicCollection musicCollection;

    public IdlePlaySource(long id, Class<?> type) {
        this.id = id;
        this.type = type;
    }

    public void serverLoadMusicCollection(UUID playerUUID) {
        if (musicCollection == null) {
            if (type.equals(Album.class)) {
                dataLoaded = true;
                musicCollection = IMusicApiService.getInstance(ApiProvider.NCM).getAlbumInfoDetail(id, playerUUID);
            } else if (type.equals(Playlist.class)) {
                dataLoaded = true;
                musicCollection = IMusicApiService.getInstance(ApiProvider.NCM).getPlaylistDetail(id, playerUUID);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        IdlePlaySource that = (IdlePlaySource) o;
        return id == that.id && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, pusherInfo);
    }
}
