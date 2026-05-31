package indi.etern.musichud.beans.api;

import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.network.Codecs;
import indi.etern.musichud.server.api.ApiProvider;
import indi.etern.musichud.server.api.IMusicApiService;
import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;

@Getter
@ToString
public final class IdlePlaySource {
    public static final StreamCodec<ByteBuf, IdlePlaySource> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,
            IdlePlaySource::getId,
            Codecs.CLASS,
            IdlePlaySource::getType,
            IdlePlaySource::new
    );
    private final long id;
    private final Class<?> type;
    @Setter
    transient private Player player;
    @Getter
    transient private boolean dataLoaded = false;
    transient private MusicCollection musicCollection;

    public IdlePlaySource(long id, Class<?> type) {
        this.id = id;
        this.type = type;
    }

    public void serverLoadMusicCollection(Player player) {
        if (musicCollection == null) {
            if (type.equals(Album.class)) {
                dataLoaded = true;
                musicCollection = IMusicApiService.getInstance(ApiProvider.NCM).getAlbumInfoDetail(id, player);
            } else if (type.equals(Playlist.class)) {
                dataLoaded = true;
                musicCollection = IMusicApiService.getInstance(ApiProvider.NCM).getPlaylistDetail(id, player);
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
        return Objects.hash(id, type, player);
    }
}
